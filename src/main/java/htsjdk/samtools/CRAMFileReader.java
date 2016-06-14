/*******************************************************************************
 * Copyright 2013 EMBL-EBI
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

import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SamReader.Type;
import htsjdk.samtools.cram.CRAIIndex;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.CoordMath;
import htsjdk.samtools.util.RuntimeEOFException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * {@link htsjdk.samtools.BAMFileReader BAMFileReader} analogue for CRAM files.
 * Supports random access using BAI index file formats.
 *
 * @author vadim
 */
@SuppressWarnings("UnusedDeclaration")
public class CRAMFileReader extends SamReader.ReaderImplementation implements SamReader.Indexing {
    private File cramFile;
    private final CRAMReferenceSource referenceSource;
    private InputStream inputStream;
    private CRAMIterator iterator;
    private BAMIndex mIndex;
    private File mIndexFile;
    private boolean mEnableIndexCaching;
    private boolean mEnableIndexMemoryMapping;

    private ValidationStringency validationStringency;

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
     */
    public CRAMFileReader(final File cramFile, final InputStream inputStream) {
        this(cramFile, inputStream, ReferenceSource.getDefaultCRAMReferenceSource());
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
     */
    public CRAMFileReader(final File cramFile, final InputStream inputStream,
                          final CRAMReferenceSource referenceSource) {
        if (cramFile == null && inputStream == null) {
            throw new IllegalArgumentException("Either file or input stream is required.");
        }
        if (referenceSource == null) {
            throw new IllegalArgumentException("A reference is required for CRAM readers");
        }

        this.cramFile = cramFile;
        this.inputStream = inputStream;
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
     */
    public CRAMFileReader(final File cramFile, final File indexFile,
                          final CRAMReferenceSource referenceSource) {
        if (cramFile == null)
            throw new IllegalArgumentException("File is required.");
        if (referenceSource == null) {
            throw new IllegalArgumentException("A reference is required for CRAM readers");
        }

        this.cramFile = cramFile;
        this.mIndexFile = indexFile;
        this.referenceSource = referenceSource;

        getIterator();
    }

    /**
     * Create a CRAMFileReader from a file using the supplied reference source.
     *
     * @param cramFile        CRAM file to read. Can not be null.
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.CRAMReferenceSource source} of
     *                        reference sequences. May not be null.
     * @throws IllegalArgumentException if the {@code cramFile} or the {@code CRAMReferenceSource} is null
     */
    public CRAMFileReader(final File cramFile, final CRAMReferenceSource referenceSource) {
        if (cramFile == null)
            throw new IllegalArgumentException("CRAM file cannot be null.");
        if (referenceSource == null) {
            throw new IllegalArgumentException("A reference is required for CRAM readers");
        }

        this.cramFile = cramFile;
        this.referenceSource = referenceSource;

        getIterator();
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
    public CRAMFileReader(final InputStream inputStream, final SeekableStream indexInputStream,
                          final CRAMReferenceSource referenceSource, final ValidationStringency validationStringency) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream can not be null for CRAM reader");
        }
        if (referenceSource == null) {
            throw new IllegalArgumentException("A reference is required for CRAM readers");
        }

        this.inputStream = inputStream;
        this.referenceSource = referenceSource;
        this.validationStringency = validationStringency;

        iterator = new CRAMIterator(inputStream, referenceSource, validationStringency);
        if (indexInputStream != null) {
            SeekableStream baiStream = SamIndexes.asBaiSeekableStreamOrNull(indexInputStream, iterator.getSAMFileHeader().getSequenceDictionary());
            if (null != baiStream)  {
                mIndex = new CachingBAMFileIndex(baiStream, iterator.getSAMFileHeader().getSequenceDictionary());
            }
            else {
                throw new IllegalArgumentException("CRAM index must be a BAI or CRAI stream");
            }
        }
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
     */
    public CRAMFileReader(final InputStream stream,
                          final File indexFile, final CRAMReferenceSource referenceSource,
                          final ValidationStringency validationStringency) throws IOException {
        this(stream, indexFile == null ? null: new SeekableFileStream(indexFile), referenceSource, validationStringency);
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
     */
    public CRAMFileReader(final File cramFile,
                          final File indexFile, final CRAMReferenceSource referenceSource,
                          final ValidationStringency validationStringency) throws IOException {
        this(new FileInputStream(cramFile), indexFile, referenceSource, validationStringency);
        this.cramFile = cramFile;
    }

    @Override
    void enableIndexCaching(final boolean enabled) {
        // relevant to BAI only
        mEnableIndexCaching = enabled;
    }

    @Override
    void enableIndexMemoryMapping(final boolean enabled) {
        // relevant to BAI only
        mEnableIndexMemoryMapping = enabled;
    }

    @Override
    void enableCrcChecking(final boolean enabled) {
        // inapplicable to CRAM: do nothing
    }

    @Override
    void setSAMRecordFactory(final SAMRecordFactory factory) {
    }

    @Override
    public boolean hasIndex() {
        return mIndex != null || mIndexFile != null;
    }

    @Override
    public BAMIndex getIndex() {
        if (!hasIndex())
            throw new SAMException("No index is available for this CRAM file.");
        if (mIndex == null) {
            final SAMSequenceDictionary dictionary = getFileHeader()
                    .getSequenceDictionary();
            if (mIndexFile.getName().endsWith(BAMIndex.BAMIndexSuffix)) {
                mIndex = mEnableIndexCaching ? new CachingBAMFileIndex(mIndexFile,
                        dictionary, mEnableIndexMemoryMapping)
                        : new DiskBasedBAMFileIndex(mIndexFile, dictionary,
                        mEnableIndexMemoryMapping);
                return mIndex;
            }

            if (!mIndexFile.getName().endsWith(CRAIIndex.CRAI_INDEX_SUFFIX)) return null;
            // convert CRAI into BAI:
            final SeekableStream baiStream;
            try {
                baiStream = SamIndexes.asBaiSeekableStreamOrNull(new SeekableFileStream(mIndexFile), iterator.getSAMFileHeader().getSequenceDictionary());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            mIndex = mEnableIndexCaching ? new CachingBAMFileIndex(baiStream, getFileHeader().getSequenceDictionary()) :
                    new DiskBasedBAMFileIndex(baiStream, getFileHeader().getSequenceDictionary());
        }
        return mIndex;
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
        if (coordinateArray == null || coordinateArray.length == 0) return emptyIterator;
        try {
            // create an input stream that reads the source cram stream only within the coordinate pairs:
            final SeekableStream seekableStream = getSeekableStreamOrFailWithRTE();
            return new CRAMIterator(seekableStream, referenceSource, coordinateArray, validationStringency);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SAMFileHeader getFileHeader() {
        return iterator.getSAMFileHeader();
    }

    @Override
    public SAMRecordIterator getIterator() {
        if (iterator != null && cramFile == null)
            return iterator;
        try {
            final CRAMIterator newIterator;
            if (cramFile != null) {
                newIterator = new CRAMIterator(new FileInputStream(cramFile),
                        referenceSource, validationStringency);
            } else
                newIterator = new CRAMIterator(inputStream, referenceSource, validationStringency);

            iterator = newIterator;
            return iterator;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CloseableIterator<SAMRecord> getIterator(final SAMFileSpan fileSpan) {
        return iterator(fileSpan);
    }

    @Override
    public SAMFileSpan getFilePointerSpanningReads() {
        return new BAMFileSpan(new Chunk(iterator.firstContainerOffset << 16, Long.MAX_VALUE));
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
        public void close() {
        }

        @Override
        public SAMRecordIterator assertSorted(final SortOrder sortOrder) {
            return this;
        }
    };

    @Override
    public CloseableIterator<SAMRecord> queryAlignmentStart(final String sequence,
                                                            final int start) {
        final SAMFileHeader fileHeader = getFileHeader();
        final int referenceIndex = fileHeader.getSequenceIndex(sequence);
        return new CRAMIntervalIterator(new QueryInterval[]{new QueryInterval(referenceIndex, start, -1)}, true);
    }

    @Override
    public CloseableIterator<SAMRecord> queryUnmapped() {
        final long startOfLastLinearBin = getIndex().getStartOfLastLinearBin();

        final SeekableStream seekableStream = getSeekableStreamOrFailWithRTE();
        final CRAMIterator newIterator;
        try {
            seekableStream.seek(0);
            newIterator = new CRAMIterator(seekableStream, referenceSource, validationStringency);
            seekableStream.seek(startOfLastLinearBin >>> 16);
            final Container container = ContainerIO.readContainerHeader(newIterator.getCramHeader().getVersion().major, seekableStream);
            seekableStream.seek(seekableStream.position() + container.containerByteSize);
            iterator = newIterator;
            boolean atAlignments;
            do {
                atAlignments = iterator.advanceToAlignmentInContainer(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, SAMRecord.NO_ALIGNMENT_START);
            } while (!atAlignments && iterator.hasNext());
        } catch (final IOException e) {
            throw new RuntimeEOFException(e);
        }

        return iterator;
    }

    private SeekableStream getSeekableStreamOrFailWithRTE() {
        SeekableStream seekableStream = null;
        if (cramFile != null) {
            try {
                seekableStream = new SeekableFileStream(cramFile);
            } catch (final FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else if (inputStream instanceof SeekableStream) {
            seekableStream = (SeekableStream) inputStream;
        }
        return seekableStream;
    }

    @Override
    public void close() {
        CloserUtil.close(iterator);
        CloserUtil.close(inputStream);
        CloserUtil.close(mIndex);
    }

    @Override
    void setValidationStringency(final ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
        if (iterator != null) iterator.setValidationStringency(validationStringency);
    }

    @Override
    public ValidationStringency getValidationStringency() {
        return validationStringency;
    }

    @Override
    public CloseableIterator<SAMRecord> query(final QueryInterval[] intervals,
                                              final boolean contained) {
        return new CRAMIntervalIterator(intervals, contained);
    }

    @Override
    public Type type() {
        return Type.CRAM_TYPE;
    }

    @Override
    void enableFileSource(final SamReader reader, final boolean enabled) {
        if (iterator != null)
            iterator.setFileSource(enabled ? reader : null);
    }

    private class CRAMIntervalIterator
            extends BAMQueryMultipleIntervalsIteratorFilter
            implements SAMRecordIterator {

        // the granularity of this iterator is the container, so the records returned
        // by it must still be filtered to find those matching the filter criteria
        private CRAMIterator unfilteredIterator;
        SAMRecord nextRec = null;

        public CRAMIntervalIterator(final QueryInterval[] queries, final boolean contained) {
            super(queries, contained);

            long[] coordinates = coordinatesFromQueryIntervals(getIndex(), queries);
            if (coordinates != null && coordinates.length != 0) {
                try {
                    unfilteredIterator = new CRAMIterator(
                            getSeekableStreamOrFailWithRTE(),
                            referenceSource,
                            coordinates,
                            validationStringency
                    );
                } catch (final IOException e) {
                    throw new RuntimeEOFException(e);
                }
                getNextRecord(); // advance to the first record that matches the filter criteria
            }
        }

        // convert queries -> merged BAMFileSpan -> coordinate array
        private long[] coordinatesFromQueryIntervals(BAMIndex index, QueryInterval[] queries) {
            ArrayList<BAMFileSpan> spanList = new ArrayList<>(1);
            Arrays.asList(queries).forEach(qi -> spanList.add(mIndex.getSpanOverlapping(qi.referenceIndex, qi.start, qi.end)));
            BAMFileSpan spanArray[] = new BAMFileSpan[spanList.size()];
            for (int i = 0; i < spanList.size(); i++) {
                spanArray[i] = spanList.get(i);
            }

            return BAMFileSpan.merge(spanArray).toCoordinateArray();
        }

        @Override
        public SAMRecordIterator assertSorted(final SortOrder sortOrder) {
            return null;
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

        private SAMRecord getNextRecord() {
            final SAMRecord result = nextRec;
            nextRec = null;
            while(nextRec == null && unfilteredIterator.hasNext()) {
                SAMRecord nextRecord = unfilteredIterator.next();
                switch(compareToFilter(nextRecord)) {
                    case MATCHES_FILTER:
                        nextRec = nextRecord;
                        break;
                    case CONTINUE_ITERATION:
                        continue;
                    case STOP_ITERATION:
                        break;
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
}
