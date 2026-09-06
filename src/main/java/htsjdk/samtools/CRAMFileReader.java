/*******************************************************************************
 * Copyright 2013-2016 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools;

import htsjdk.index.HtsQueryIndex;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SamReader.Type;
import htsjdk.samtools.cram.CRAIIndex;
import htsjdk.samtools.cram.CRAIQueryIndex;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.*;
import htsjdk.utils.ValidationUtils;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.OptionalLong;

/**
 * {@link htsjdk.samtools.BAMFileReader BAMFileReader} analogue for CRAM files.
 * Supports random access using a CRAI (via {@link CRAIQueryIndex}) or a BAI. The index is read
 * lazily.
 *
 * @author vadim
 */
public class CRAMFileReader extends SamReader.ReaderImplementation implements SamReader.Indexing, AutoCloseable {
    private Path cramPath;
    private final CRAMReferenceSource referenceSource;
    private InputStream inputStream;
    private DeferredCloseSeekableStream deferredCloseSeekableStream;
    private CRAMIterator iterator;
    private BAMIndex mIndex;
    private Path mIndexPath;
    private boolean mEnableIndexCaching;
    private boolean mEnableIndexMemoryMapping;

    /** Index stream, if the reader was built from streams. */
    private SeekableStream indexStream;

    /** Set on first use; null if the index is not a CRAI. */
    private CRAIQueryIndex craiQueryIndex;

    private boolean craiQueryIndexResolved;

    private ValidationStringency validationStringency;

    private static final Log log = Log.getInstance(CRAMFileReader.class);

    /**
     * Create a CRAMFileReader from either a path or input stream using the reference source returned by
     * {@link ReferenceSource#getDefaultCRAMReferenceSource() getDefaultCRAMReferenceSource}.
     *
     * @param cramPath CRAM file path to open
     * @param inputStream CRAM stream to read
     *
     * @throws IllegalArgumentException if the {@code cramPath} and the {@code inputStream} are both null
     * @throws IllegalStateException if a {@link ReferenceSource#getDefaultCRAMReferenceSource() default}
     * reference source cannot be acquired
     */
    public CRAMFileReader(final Path cramPath, final InputStream inputStream) {
        this(cramPath, inputStream, ReferenceSource.getDefaultCRAMReferenceSource());
    }

    /**
     * Create a CRAMFileReader from either a file or input stream using the reference source returned by
     * {@link ReferenceSource#getDefaultCRAMReferenceSource() getDefaultCRAMReferenceSource}.
     *
     * @param cramFile CRAM file to open
     * @param inputStream CRAM stream to read
     *
     * @throws IllegalArgumentException if the {@code cramFile} and the {@code inputStream} are both null
     * @throws IllegalStateException if a {@link ReferenceSource#getDefaultCRAMReferenceSource() default}
     * reference source cannot be acquired
     * @deprecated use {@link #CRAMFileReader(Path, InputStream)} instead.
     */
    @Deprecated
    public CRAMFileReader(final File cramFile, final InputStream inputStream) {
        this(cramFile == null ? null : cramFile.toPath(), inputStream);
    }

    /**
     * Create a CRAMFileReader from either a path or input stream using the supplied reference source.
     *
     * @param cramPath        CRAM file path to read
     * @param inputStream     CRAM stream to read
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.ReferenceSource source} of
     *                        reference sequences. May not be null.
     *
     * @throws IllegalArgumentException if the {@code cramPath} and the {@code inputStream} are both null
     * or if the {@code CRAMReferenceSource} is null
     */
    public CRAMFileReader(
            final Path cramPath, final InputStream inputStream, final CRAMReferenceSource referenceSource) {
        ValidationUtils.validateArg(
                cramPath != null || inputStream != null, "Either path or input stream is required.");

        this.cramPath = cramPath;
        // inputStream may legitimately be null when a path is supplied (reads then go through the path);
        // only wrap a non-null stream to avoid a NullPointerException from BufferedInputStream.
        this.inputStream = inputStream == null ? null : new BufferedInputStream(inputStream);
        this.referenceSource = referenceSource;
        if (cramPath != null) {
            mIndexPath = findIndexForPath(null, cramPath);
        }
        getIterator();
    }

    /**
     * Create a CRAMFileReader from either a file or input stream using the supplied reference source.
     *
     * @param cramFile        CRAM file to read
     * @param inputStream     CRAM stream to read
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.ReferenceSource source} of
     *                        reference sequences. May not be null.
     *
     * @throws IllegalArgumentException if the {@code cramFile} and the {@code inputStream} are both null
     * or if the {@code CRAMReferenceSource} is null
     * @deprecated use {@link #CRAMFileReader(Path, InputStream, CRAMReferenceSource)} instead.
     */
    @Deprecated
    public CRAMFileReader(
            final File cramFile, final InputStream inputStream, final CRAMReferenceSource referenceSource) {
        this(cramFile == null ? null : cramFile.toPath(), inputStream, referenceSource);
    }

    /**
     * Create a CRAMFileReader from a path and optional index path using the supplied reference source. If index path
     * is supplied then random access will be available.
     *
     * @param cramPath        CRAM file path to read. May not be null.
     * @param indexPath       index file path to be used for random access. May be null.
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.CRAMReferenceSource source} of
     *                        reference sequences. May not be null.
     * @throws IllegalArgumentException if the {@code cramPath} or the {@code CRAMReferenceSource} is null
     */
    public CRAMFileReader(final Path cramPath, final Path indexPath, final CRAMReferenceSource referenceSource) {
        ValidationUtils.nonNull(cramPath, "Path is required.");

        this.cramPath = cramPath;
        mIndexPath = findIndexForPath(indexPath, cramPath);
        this.referenceSource = referenceSource;

        getIterator();
    }

    /**
     * Create a CRAMFileReader from a file and optional index file using the supplied reference source. If index file
     * is supplied then random access will be available.
     *
     * @param cramFile        CRAM file to read. May not be null.
     * @param indexFile       index file to be used for random access. May be null.
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.CRAMReferenceSource source} of
     *                        reference sequences. May not be null.
     * @throws IllegalArgumentException if the {@code cramFile} or the {@code CRAMReferenceSource} is null
     * @deprecated use {@link #CRAMFileReader(Path, Path, CRAMReferenceSource)} instead.
     */
    @Deprecated
    public CRAMFileReader(final File cramFile, final File indexFile, final CRAMReferenceSource referenceSource) {
        this(
                ValidationUtils.nonNull(cramFile, "cramFile").toPath(),
                indexFile == null ? null : indexFile.toPath(),
                referenceSource);
    }

    /**
     * Create a CRAMFileReader from a path using the supplied reference source.
     *
     * @param cramPath        CRAM file path to read. Can not be null.
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.CRAMReferenceSource source} of
     *                        reference sequences. May not be null.
     * @throws IllegalArgumentException if the {@code cramPath} or the {@code CRAMReferenceSource} is null
     */
    public CRAMFileReader(final Path cramPath, final CRAMReferenceSource referenceSource) {
        ValidationUtils.nonNull(cramPath, "Path is required.");

        this.cramPath = cramPath;
        this.referenceSource = referenceSource;
        mIndexPath = findIndexForPath(null, cramPath);

        getIterator();
    }

    /**
     * Create a CRAMFileReader from a file using the supplied reference source.
     *
     * @param cramFile        CRAM file to read. Can not be null.
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.CRAMReferenceSource source} of
     *                        reference sequences. May not be null.
     * @throws IllegalArgumentException if the {@code cramFile} or the {@code CRAMReferenceSource} is null
     * @deprecated use {@link #CRAMFileReader(Path, CRAMReferenceSource)} instead.
     */
    @Deprecated
    public CRAMFileReader(final File cramFile, final CRAMReferenceSource referenceSource) {
        this(ValidationUtils.nonNull(cramFile, "cramFile").toPath(), referenceSource);
    }

    /**
     * Create a CRAMFileReader from an input stream and optional index stream using the supplied reference
     * source and validation stringency.
     *
     * @param inputStream      CRAM stream to read. May not be null.
     * @param indexInputStream index stream to be used for random access. May be null.
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.CRAMReferenceSource source} of
     *                        reference sequences. May not be null.
     * @param validationStringency Validation stringency to be used when reading
     *
     * @throws IllegalArgumentException if the {@code inputStream} or the {@code CRAMReferenceSource} is null
     */
    public CRAMFileReader(
            final InputStream inputStream,
            final SeekableStream indexInputStream,
            final CRAMReferenceSource referenceSource,
            final ValidationStringency validationStringency)
            throws IOException {
        ValidationUtils.nonNull(inputStream, "Input stream can not be null for CRAM reader");
        this.referenceSource = referenceSource;
        initWithStreams(inputStream, indexInputStream, validationStringency);
    }

    /**
     * Create a CRAMFileReader from an input stream and optional index path using the supplied reference
     * source and validation stringency.
     *
     * @param stream            CRAM stream to read. May not be null.
     * @param indexPath         index file path to be used for random access. May be null.
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.CRAMReferenceSource source} of
     *                        reference sequences. May not be null.
     * @param validationStringency Validation stringency to be used when reading
     *
     * @throws IllegalArgumentException if the {@code inputStream} or the {@code CRAMReferenceSource} is null
     */
    public CRAMFileReader(
            final InputStream stream,
            final Path indexPath,
            final CRAMReferenceSource referenceSource,
            final ValidationStringency validationStringency)
            throws IOException {
        this(
                stream,
                indexPath == null ? null : new SeekablePathStream(indexPath),
                referenceSource,
                validationStringency);
    }

    /**
     * Create a CRAMFileReader from an input stream and optional index file using the supplied reference
     * source and validation stringency.
     *
     * @param stream            CRAM stream to read. May not be null.
     * @param indexFile         index file to be used for random access. May be null.
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.CRAMReferenceSource source} of
     *                        reference sequences. May not be null.
     * @param validationStringency Validation stringency to be used when reading
     *
     * @throws IllegalArgumentException if the {@code inputStream} or the {@code CRAMReferenceSource} is null
     * @deprecated use {@link #CRAMFileReader(InputStream, Path, CRAMReferenceSource, ValidationStringency)} instead.
     */
    @Deprecated
    public CRAMFileReader(
            final InputStream stream,
            final File indexFile,
            final CRAMReferenceSource referenceSource,
            final ValidationStringency validationStringency)
            throws IOException {
        this(stream, indexFile == null ? null : indexFile.toPath(), referenceSource, validationStringency);
    }

    /**
     * Create a CRAMFileReader from a CRAM path and optional index path using the supplied reference
     * source and validation stringency.
     *
     * @param cramPath        CRAM path to read. May not be null.
     * @param indexPath       index file path to be used for random access. May be null.
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.CRAMReferenceSource source} of
     *                        reference sequences. May not be null.
     * @param validationStringency Validation stringency to be used when reading
     *
     * @throws IllegalArgumentException if the {@code cramPath} or the {@code CRAMReferenceSource} is null
     */
    public CRAMFileReader(
            final Path cramPath,
            final Path indexPath,
            final CRAMReferenceSource referenceSource,
            final ValidationStringency validationStringency)
            throws IOException {
        ValidationUtils.nonNull(cramPath, "Input path can not be null for CRAM reader");

        this.cramPath = cramPath;
        this.referenceSource = referenceSource;
        this.mIndexPath = findIndexForPath(indexPath, cramPath);
        // The index is opened lazily from mIndexPath.
        initWithStreams(new BufferedInputStream(Files.newInputStream(cramPath)), null, validationStringency);
    }

    /**
     * Create a CRAMFileReader from a CRAM file and optional index file using the supplied reference
     * source and validation stringency.
     *
     * @param cramFile        CRAM stream to read. May not be null.
     * @param indexFile       index file to be used for random access. May be null.
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.CRAMReferenceSource source} of
     *                        reference sequences. May not be null.
     * @param validationStringency Validation stringency to be used when reading
     *
     * @throws IllegalArgumentException if the {@code cramFile} or the {@code CRAMReferenceSource} is null
     * @deprecated use {@link #CRAMFileReader(Path, Path, CRAMReferenceSource, ValidationStringency)} instead.
     */
    @Deprecated
    public CRAMFileReader(
            final File cramFile,
            final File indexFile,
            final CRAMReferenceSource referenceSource,
            final ValidationStringency validationStringency)
            throws IOException {
        this(
                ValidationUtils.nonNull(cramFile, "cramFile").toPath(),
                indexFile == null ? null : indexFile.toPath(),
                referenceSource,
                validationStringency);
    }

    private void initWithStreams(
            final InputStream inputStream,
            final SeekableStream indexInputStream,
            final ValidationStringency validationStringency)
            throws IOException {
        this.inputStream = inputStream;
        this.validationStringency = validationStringency;
        this.indexStream = indexInputStream;
        iterator = new CRAMIterator(inputStream, referenceSource, validationStringency);
    }

    private Path findIndexForPath(Path indexPath, final Path cramPath) {
        indexPath = indexPath == null ? SamFiles.findIndex(cramPath) : indexPath;
        if (indexPath != null) {
            try {
                if (Files.getLastModifiedTime(indexPath).toMillis()
                        < Files.getLastModifiedTime(cramPath).toMillis()) {
                    log.warn("CRAM index file " + indexPath.toAbsolutePath() + " is older than CRAM "
                            + cramPath.toAbsolutePath());
                }
            } catch (final IOException e) {
                log.warn("Could not check modification times for " + indexPath + " and " + cramPath);
            }
        }
        return indexPath;
    }

    /** No-op for a CRAI, which is held fully in memory (issue #535). */
    @Override
    void enableIndexCaching(final boolean enabled) {
        mEnableIndexCaching = enabled;
    }

    /** No-op for a CRAI; see {@link #enableIndexCaching(boolean)}. */
    @Override
    void enableIndexMemoryMapping(final boolean enabled) {
        mEnableIndexMemoryMapping = enabled;
    }

    @Override
    void enableCrcChecking(final boolean enabled) {
        // inapplicable to CRAM: do nothing
    }

    @Override
    void setSAMRecordFactory(final SAMRecordFactory factory) {}

    @Override
    public boolean hasIndex() {
        return mIndex != null || mIndexPath != null || indexStream != null;
    }

    /** Returns a {@link CRAIQueryIndex} for a CRAI or a {@link BAMIndex} for a BAI, with no conversion. */
    @Override
    public HtsQueryIndex getHtsIndex() {
        if (!hasIndex()) {
            throw new SAMException("No index is available for this CRAM file.");
        }
        final CRAIQueryIndex craiIndex = getCRAIQueryIndexOrNull();
        return craiIndex != null ? craiIndex : getIndex();
    }

    @Override
    @Deprecated
    public BAMIndex getIndex() {
        if (!hasIndex()) {
            throw new SAMException("No index is available for this CRAM file.");
        }
        if (mIndex == null) {
            final SAMSequenceDictionary dictionary = getFileHeader().getSequenceDictionary();
            if (mIndexPath != null) {
                final String indexFileName = mIndexPath.getFileName().toString();
                if (indexFileName.endsWith(FileExtensions.BAI_INDEX)) {
                    mIndex = mEnableIndexCaching
                            ? new CachingBAMFileIndex(mIndexPath, dictionary, mEnableIndexMemoryMapping)
                            : new DiskBasedBAMFileIndex(mIndexPath, dictionary, mEnableIndexMemoryMapping);
                    return mIndex;
                }
            }

            // Convert a CRAI to the BAI this method's return type demands. Region queries do not use
            // this path.
            final SeekableStream baiStream;
            try {
                final SeekableStream indexSource = openIndexStream();
                final SamIndexes indexType = SamIndexes.getSAMIndexTypeFromStream(indexSource);
                if (indexType == SamIndexes.CRAI) {
                    // The conversion reads the whole CRAI, so the source can be closed.
                    try (indexSource) {
                        baiStream = CRAIIndex.openCraiFileAsBaiStream(indexSource, dictionary);
                    }
                } else if (indexType == SamIndexes.BAI) {
                    baiStream = indexSource;
                } else {
                    indexSource.close();
                    throw new IllegalArgumentException("CRAM index must be a BAI or CRAI stream");
                }
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }

            mIndex = mEnableIndexCaching
                    ? new CachingBAMFileIndex(baiStream, dictionary)
                    : new DiskBasedBAMFileIndex(baiStream, dictionary);
        }
        return mIndex;
    }

    /**
     * A stream at the start of the index, safe for the caller to close: a fresh stream for a path, or
     * a deferred-close wrapper for a supplied stream.
     */
    private SeekableStream openIndexStream() throws IOException {
        if (mIndexPath != null) {
            return new SeekablePathStream(mIndexPath);
        }
        indexStream.seek(0);
        return new DeferredCloseSeekableStream(indexStream);
    }

    /** Built on first use; null if the index is not a CRAI. */
    private CRAIQueryIndex getCRAIQueryIndexOrNull() {
        if (craiQueryIndexResolved) {
            return craiQueryIndex;
        }
        craiQueryIndexResolved = true;
        if (!hasIndex()) {
            return null;
        }
        if (mIndexPath != null && mIndexPath.getFileName().toString().endsWith(FileExtensions.BAI_INDEX)) {
            // Trust the .bai extension, as getIndex() does.
            return null;
        }
        try (final SeekableStream stream = openIndexStream()) {
            // Sniff content: a CSI is also gzipped and must not be parsed as a CRAI.
            if (SamIndexes.getSAMIndexTypeFromStream(stream) == SamIndexes.CRAI) {
                stream.seek(0);
                craiQueryIndex =
                        new CRAIQueryIndex(CRAMCRAIIndexer.readIndex(stream).getCRAIEntries());
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return craiQueryIndex;
    }

    @Override
    public boolean hasBrowseableIndex() {
        return false;
    }

    @Override
    public BrowseableBAMIndex getBrowseableIndex() {
        return null;
    }

    @Override
    public SAMRecordIterator iterator(final SAMFileSpan fileSpan) {
        // get the file coordinates for the span:
        final long[] coordinateArray = ((BAMFileSpan) fileSpan).toCoordinateArray();
        if (coordinateArray == null || coordinateArray.length == 0) {
            return emptyIterator;
        }

        // create an input stream that reads the source cram stream only within the coordinate pairs:
        final SeekableStream seekableStream = getSeekableStreamOrFailWithRTE();
        return new CRAMIterator(seekableStream, referenceSource, validationStringency, null, coordinateArray);
    }

    @Override
    public SAMFileHeader getFileHeader() {
        return iterator.getSAMFileHeader();
    }

    @Override
    public SAMRecordIterator getIterator() {
        if (iterator != null && cramPath == null) {
            return iterator;
        }
        try {
            if (cramPath != null) {
                iterator = new CRAMIterator(
                        new BufferedInputStream(Files.newInputStream(cramPath)), referenceSource, validationStringency);
            } else {
                iterator = new CRAMIterator(inputStream, referenceSource, validationStringency);
            }
            return iterator;
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Note: the resolution of this iterator is the Slice, so the records returned are all of the records
     * in all the slices that overlap these spans.
     */
    @Override
    public CloseableIterator<SAMRecord> getIterator(final SAMFileSpan fileSpan) {
        return iterator(fileSpan);
    }

    @Override
    public SAMFileSpan getFilePointerSpanningReads() {
        return new BAMFileSpan(new Chunk(iterator.getFirstContainerOffset() << 16, Long.MAX_VALUE));
    }

    private static final SAMRecordIterator emptyIterator = new SAMRecordIterator() {

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public SAMRecord next() {
            throw new RuntimeException("No records.");
        }

        @Override
        public void remove() {
            throw new RuntimeException("Remove not supported.");
        }

        @Override
        public void close() {}

        @Override
        public SAMRecordIterator assertSorted(final SortOrder sortOrder) {
            return this;
        }
    };

    @Override
    public CloseableIterator<SAMRecord> queryAlignmentStart(final String sequence, final int start) {
        final SAMFileHeader fileHeader = getFileHeader();
        final int referenceIndex = fileHeader.getSequenceIndex(sequence);
        // alignment start requires a filtering iterator to ensure that records in the
        // same container that start AFTER the requested start are filtered out
        return new CRAMAlignmentStartIterator(referenceIndex, start);
    }

    @Override
    public CloseableIterator<SAMRecord> queryUnmapped() {
        final OptionalLong startOffset;
        final CRAIQueryIndex craiIndex = getCRAIQueryIndexOrNull();
        if (craiIndex != null) {
            startOffset = craiIndex.getFirstUnplacedContainerOffset();
            if (startOffset.isEmpty()) {
                return emptyIterator;
            }
        } else {
            // A BAI for a CRAM stores container offsets shifted up 16 bits; -1 means no placed records.
            final long startOfLastLinearBin = getIndex().getStartOfLastLinearBin();
            startOffset =
                    startOfLastLinearBin == -1 ? OptionalLong.empty() : OptionalLong.of(startOfLastLinearBin >>> 16);
        }

        final SeekableStream seekableStream = getSeekableStreamOrFailWithRTE();
        try {
            seekableStream.seek(0);
            iterator = new CRAMIterator(seekableStream, referenceSource, validationStringency);
            if (startOffset.isPresent()) {
                seekableStream.seek(startOffset.getAsLong());
            }
            boolean atAlignments;
            do {
                atAlignments = iterator.advanceToAlignmentInContainer(
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, SAMRecord.NO_ALIGNMENT_START);
            } while (!atAlignments && iterator.hasNext());
        } catch (final IOException e) {
            throw new RuntimeEOFException(e);
        }

        return iterator;
    }

    private SeekableStream getSeekableStreamOrFailWithRTE() {
        SeekableStream seekableStream = null;

        if (cramPath != null) {
            try {
                // If this reader was provided with a Path, create a SeekableStream directly and
                // let it be closed by CloseableIterators, and then recreated on demand.
                seekableStream = new SeekablePathStream(cramPath);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        } else if (inputStream != null && inputStream instanceof SeekableStream) {
            // For SeekableStreams that were provided to the reader constructor instead of a Path, we
            // need to prevent CloseableIterators from closing the underlying stream since we can't
            // reconstitute a SeekableStream from an InputStream. So wrap the underlying SeekableStream
            // in a DeferredCloseSeekableStream with a no-op close implementation, and defer closing it
            // until enclosing reader is closed.
            if (deferredCloseSeekableStream == null) {
                deferredCloseSeekableStream = new DeferredCloseSeekableStream((SeekableStream) inputStream);
            }
            seekableStream = deferredCloseSeekableStream;
        }

        if (seekableStream == null) {
            // Query and index operations require the ability to seek. Historically this method returned null
            // here, which turned into an unhelpful NullPointerException further down the call stack.
            // See https://github.com/samtools/htsjdk/issues/426.
            throw new IllegalStateException(
                    "This CRAM reader cannot satisfy query or index operations because it was created from an "
                            + "input stream that is not a SeekableStream. Construct the reader with a File or Path, "
                            + "or with an input stream that implements SeekableStream, in order to query by "
                            + "interval or to use an index.");
        }
        return seekableStream;
    }

    // In order to reuse a SeekableStream multiple times with CloseableIterators (which close the
    // underlying stream when they're done), we need to wrap the SeekableStream in an object that
    // has a no-op close implementation so the actual close can be deferred  until the enclosing
    // reader is closed.
    private static class DeferredCloseSeekableStream extends SeekableStream {
        private final SeekableStream delegateStream;

        public DeferredCloseSeekableStream(final SeekableStream delegateStream) {
            this.delegateStream = delegateStream;
            if (delegateStream instanceof DeferredCloseSeekableStream) {
                throw new IllegalArgumentException("ReuseableSeekableStream objects cannot be nested");
            }
        }

        public SeekableStream getDelegate() {
            return delegateStream;
        }

        @Override
        public long length() {
            return delegateStream.length();
        }

        @Override
        public long position() throws IOException {
            return delegateStream.position();
        }

        @Override
        public void seek(long position) throws IOException {
            delegateStream.seek(position);
        }

        @Override
        public int read() throws IOException {
            return delegateStream.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return delegateStream.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            // defer close, and let the caller close the delegate when its ready to by calling
            // getDelegate().close()
        }

        @Override
        public boolean eof() throws IOException {
            return delegateStream.eof();
        }

        @Override
        public String getSource() {
            return delegateStream.getSource();
        }
    }

    @Override
    public void close() {
        // if at any point we created a deferredCloseSeekableStream, close the underlying delegate now
        if (deferredCloseSeekableStream != null) {
            CloserUtil.close(deferredCloseSeekableStream.getDelegate());
        }
        CloserUtil.close(iterator);
        CloserUtil.close(inputStream);
        CloserUtil.close(mIndex);
        CloserUtil.close(indexStream);
    }

    @Override
    void setValidationStringency(final ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
        if (iterator != null) {
            iterator.setValidationStringency(validationStringency);
        }
    }

    @Override
    public ValidationStringency getValidationStringency() {
        return validationStringency;
    }

    @Override
    public CloseableIterator<SAMRecord> query(final QueryInterval[] intervals, final boolean contained) {
        return new CRAMIntervalIterator(intervals, contained);
    }

    @Override
    public Type type() {
        return Type.CRAM_TYPE;
    }

    @Override
    void enableFileSource(final SamReader reader, final boolean enabled) {
        if (iterator != null) {
            iterator.setFileSource(enabled ? reader : null);
        }
    }

    /**
     * Prepare to iterate through SAMRecords that match the intersection of the given intervals and chunk boundaries.
     * @param intervals the intervals to restrict reads to
     * @param contained if <code>true</code>, return records that are strictly
     *                  contained in the intervals, otherwise return records that overlap
     * @param filePointers file pointer pairs corresponding to chunk boundaries for the
     *                     intervals
     */
    public CloseableIterator<SAMRecord> createIndexIterator(
            final QueryInterval[] intervals, final boolean contained, final long[] filePointers) {
        return new CRAMIntervalIterator(intervals, contained, filePointers);
    }

    /** Resolves query intervals to container coordinates for {@link CRAMIterator}, via the CRAI if present, else the BAI. */
    private long[] coordinatesFromQueryIntervals(final QueryInterval[] queries) {
        final CRAIQueryIndex craiIndex = getCRAIQueryIndexOrNull();
        return craiIndex != null
                ? craiIndex.getCoordinatesForQueries(queries)
                : coordinatesFromQueryIntervals(getIndex(), queries);
    }

    // convert queries -> merged BAMFileSpan -> coordinate array
    private static long[] coordinatesFromQueryIntervals(BAMIndex index, QueryInterval[] queries) {
        ArrayList<BAMFileSpan> spanList = new ArrayList<>(1);
        Arrays.asList(queries)
                .forEach(qi -> spanList.add(index.getSpanOverlapping(qi.referenceIndex, qi.start, qi.end)));
        BAMFileSpan spanArray[] = new BAMFileSpan[spanList.size()];
        for (int i = 0; i < spanList.size(); i++) {
            spanArray[i] = spanList.get(i);
        }

        return BAMFileSpan.merge(spanArray).toCoordinateArray();
    }

    /**
     * This class is intended to be a base class for various CRAM filtering iterators. Subclasses must
     * ensure that {@link CRAMIntervalIteratorBase#initializeIterator} is called once after the subclass'
     * construction is complete, preferably at the end of the subclass' constructor, but before any
     * attempt is made to use the iterator.
     */
    private abstract class CRAMIntervalIteratorBase extends BAMQueryMultipleIntervalsIteratorFilter
            implements CloseableIterator<SAMRecord> {

        // the granularity of this iterator is the container, so the records returned
        // by it must still be filtered to find those matching the filter criteria
        private CRAMIterator unfilteredIterator;
        private SAMRecord nextRec = null;

        public CRAMIntervalIteratorBase(final QueryInterval[] queries, final boolean contained) {
            super(queries, contained);
        }

        /**
         * Subclasses must call this method in their constructors AFTER construction of this class is complete.
         * It can't be called directly by this class's constructor because it calls getRecord(), which may be
         * overridden in subclasses, and can depend on state established by the subclass' constructor (specifically,
         * it may need to establish a filter comparator).
         * @param coordinates array or coordinates as produced by {@link BAMFileSpan#toCoordinateArray}
         */
        protected void initializeIterator(final QueryInterval[] queryIntervals, final long[] coordinates) {
            if (coordinates != null && coordinates.length != 0) {
                unfilteredIterator = new CRAMIterator(
                        getSeekableStreamOrFailWithRTE(),
                        referenceSource,
                        validationStringency,
                        queryIntervals,
                        coordinates);
                getNextRecord(); // advance to the first record that matches the filter criteria
            }
        }

        @Override
        public void close() {
            if (unfilteredIterator != null) {
                unfilteredIterator.close();
            }
        }

        @Override
        public boolean hasNext() {
            return nextRec != null;
        }

        @Override
        public SAMRecord next() {
            if (!hasNext()) {
                throw new NoSuchElementException("Next called on empty CRAMIntervalIterator");
            }
            return getNextRecord();
        }

        protected SAMRecord getNextRecord() {
            final SAMRecord result = nextRec;
            nextRec = null;
            while (nextRec == null && unfilteredIterator.hasNext()) {
                SAMRecord nextRecord = unfilteredIterator.next();
                switch (compareToFilter(nextRecord)) {
                    case MATCHES_FILTER:
                        nextRec = nextRecord;
                        break;
                    case CONTINUE_ITERATION:
                        continue;
                    case STOP_ITERATION:
                        return result;
                    default:
                        throw new SAMException("Unexpected return from compareToFilter");
                }
            }
            return result;
        }

        @Override
        public void remove() {
            throw new RuntimeException("Method \"remove\" not implemented for CRAMIntervalIterator.");
        }
    }

    // An iterator for querying reads that match a set of query intervals
    private class CRAMIntervalIterator extends CRAMIntervalIteratorBase {
        public CRAMIntervalIterator(final QueryInterval[] queries, final boolean contained) {
            this(queries, contained, coordinatesFromQueryIntervals(queries));
        }

        public CRAMIntervalIterator(final QueryInterval[] queries, final boolean contained, final long[] coordinates) {
            super(queries, contained);
            initializeIterator(queries, coordinates);
        }
    }

    // An iterator for querying reads that match a given alignment start
    private class CRAMAlignmentStartIterator extends CRAMIntervalIteratorBase {
        final BAMStartingAtIteratorFilter startingAtIteratorFilter;

        public CRAMAlignmentStartIterator(final int referenceIndex, final int start) {
            super(new QueryInterval[] {new QueryInterval(referenceIndex, start, -1)}, true);
            startingAtIteratorFilter = new BAMStartingAtIteratorFilter(referenceIndex, start);
            initializeIterator(intervals, coordinatesFromQueryIntervals(intervals));
        }

        @Override
        public FilteringIteratorState compareToFilter(final SAMRecord record) {
            return startingAtIteratorFilter.compareToFilter(record);
        }
    }
}
