/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools;

import htsjdk.index.FileBackedBinningIndex;
import htsjdk.index.ReferenceBinsSource;
import htsjdk.index.RenumberedReferenceBinsSource;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.SamLineReader;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndex;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * Internal class for reading SAM text files. Text that is block-compressed is read a BGZF block at a time, which
 * lets each record say where in the file it lies, as a record of a BAM does; and if the file can be seeked in, it
 * can be read from any such place, and queried through an index: a BAI or CSI as samtools writes for such a file,
 * or a TBI or CSI as tabix does.
 */
class SAMTextReader extends SamReader.ReaderImplementation {

    private SAMRecordFactory samRecordFactory;
    private SamLineReader mReader;
    private SAMFileHeader mFileHeader = null;
    private boolean mHasCurrentLine = false;
    private RecordIterator mIterator = null;
    private Path mPath = null;

    // For block-compressed text only; null otherwise.
    private BlockAtATimeInputStream mBlockStream;
    private boolean mIsSeekable;
    private long mFirstRecordPointer;
    // Virtual offsets of the line in hand and of the byte after its terminator. As in an index made by htslib, a
    // line starts where the one before it ended.
    private long mLineStartPointer;
    private long mLineEndPointer;
    // Lines can be numbered only while reading on from the header.
    private int mLinesBeforeFirstRecord;
    private boolean mLineNumbersKnown = true;

    // At most one of these is set, and only for text that can be seeked in.
    private Path mIndexPath;
    private SeekableStream mIndexStream;
    // An index owns the stream it is opened from, and closes it even if it then turns out not to fit the file.
    private boolean mIndexStreamHandedOver;
    private BinningBAMIndex mIndex;
    private IndexLoading mIndexLoading = IndexLoading.AUTO;

    private ValidationStringency validationStringency = ValidationStringency.DEFAULT_STRINGENCY;

    /**
     * Add information about the origin (reader and position) to SAM records.
     */
    private SamReader mParentReader;

    /**
     * Prepare to read a SAM text file.
     *
     * @param stream Need not be buffered, as this class provides buffered reading.
     */
    public SAMTextReader(
            final InputStream stream, final ValidationStringency validationStringency, final SAMRecordFactory factory) {
        mReader = new SamLineReader(stream);
        this.validationStringency = validationStringency;
        this.samRecordFactory = factory;
        readHeader();
    }

    /**
     * Prepare to read a SAM text file.
     *
     * @param stream Need not be buffered, as this class provides buffered reading.
     * @param path   For error reporting only.
     */
    public SAMTextReader(
            final InputStream stream,
            final Path path,
            final ValidationStringency validationStringency,
            final SAMRecordFactory factory) {
        this(stream, validationStringency, factory);
        mPath = path;
    }

    /**
     * Prepare to read block-compressed SAM text.
     *
     * @param stream   positioned at the start of the file
     * @param seekable whether {@code stream} can seek, without which the file can only be read through once and
     *                 an index is of no use
     * @param path     the file being read, for error reporting and for finding its index; may be null
     * @param indexPath   the file's index, or null to look for one beside {@code path}
     * @param indexStream the file's index, if it is not at a path; null otherwise
     */
    SAMTextReader(
            final BlockCompressedInputStream stream,
            final boolean seekable,
            final Path path,
            final Path indexPath,
            final SeekableStream indexStream,
            final ValidationStringency validationStringency,
            final SAMRecordFactory factory) {
        mBlockStream = new BlockAtATimeInputStream(stream);
        mIsSeekable = seekable;
        mReader = new SamLineReader(mBlockStream);
        mPath = path;
        if (seekable && indexPath != null) {
            mIndexPath = indexPath;
        } else if (seekable && indexStream != null) {
            mIndexStream = indexStream;
        } else if (seekable && path != null) {
            mIndexPath = SamFiles.findIndex(path);
        }
        this.validationStringency = validationStringency;
        this.samRecordFactory = factory;
        try {
            readHeader();
        } catch (final RuntimeException e) {
            // There will be no reader for the caller to close, so what it was given is closed here
            close();
            throw e;
        }
    }

    /**
     * If true, writes the source of every read into the source SAMRecords.
     *
     * @param enabled true to write source information into each SAMRecord.
     */
    @Override
    public void enableFileSource(final SamReader reader, final boolean enabled) {
        this.mParentReader = enabled ? reader : null;
    }

    @Override
    void enableIndexCaching(final boolean enabled) {
        throw new UnsupportedOperationException("Cannot enable index caching for a SAM text reader");
    }

    @Override
    void enableIndexMemoryMapping(final boolean enabled) {
        throw new UnsupportedOperationException("Cannot enable index memory mapping for a SAM text reader");
    }

    @Override
    void enableCrcChecking(final boolean enabled) {
        // Do nothing - this has no meaning for SAM reading
    }

    @Override
    void setSAMRecordFactory(final SAMRecordFactory factory) {
        this.samRecordFactory = factory;
    }

    @Override
    public SamReader.Type type() {
        return SamReader.Type.SAM_TYPE;
    }

    @Override
    void setIndexLoading(final IndexLoading indexLoading) {
        if (mIndex != null && indexLoading != mIndexLoading) {
            throw new SAMException("Unable to change index loading; index file has already been loaded.");
        }
        mIndexLoading = indexLoading;
    }

    @Override
    public boolean hasIndex() {
        return mIndexPath != null || mIndexStream != null;
    }

    @Override
    public BAMIndex getIndex() {
        if (!hasIndex()) {
            throw new UnsupportedOperationException("No index is available for this SAM text");
        }
        if (mIndex == null) {
            mIndex = openIndex();
        }
        return mIndex;
    }

    /**
     * Opens the file's index, whichever of the two tools made it. samtools numbers the references of an index as
     * the header lists them, and says nothing more in it; tabix numbers them as it meets them in the file, and
     * names them, in a TBI or in the aux block of a CSI. Asking an index of the second kind by header ordinal
     * would silently give the records of some other reference, so it is asked by name.
     */
    private BinningBAMIndex openIndex() {
        if (mIndexPath != null
                && mIndexPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(FileExtensions.TABIX_INDEX)) {
            final TabixIndex tabix;
            try {
                tabix = new TabixIndex(mIndexPath);
            } catch (final IOException e) {
                throw new RuntimeIOException("Error reading index " + mIndexPath, e);
            } catch (final TribbleException e) {
                throw new SAMFormatException("The index " + mIndexPath + " is not a tabix index", e);
            }
            return new BinningBAMIndex(numberedAsTheHeaderDoes(
                    tabix.getBinningIndex(), new TabixIndex.Header(tabix.getFormatSpec(), tabix.getSequenceNames())));
        }

        final BinningBAMIndex index = mIndexPath != null
                ? BinningBAMIndex.open(mIndexPath, mIndexLoading)
                : BinningBAMIndex.open(mIndexStream, mIndexLoading);
        mIndexStreamHandedOver = mIndexStream != null;
        try {
            final ReferenceBinsSource source = index.getSource();
            if (source instanceof FileBackedBinningIndex file && file.getAux().length > 0) {
                final TabixIndex.Header tabixHeader;
                try {
                    tabixHeader = TabixIndex.readCsiAux(file.getAux());
                } catch (final TribbleException e) {
                    throw new SAMFormatException(
                            "The index " + describeIndex() + " has something other than a tabix header in its aux "
                                    + "block, so there is no telling how it numbers the sequences",
                            e);
                }
                return new BinningBAMIndex(numberedAsTheHeaderDoes(source, tabixHeader));
            }
            final int sequenceCount = mFileHeader.getSequenceDictionary().size();
            if (index.getNumberOfReferences() != sequenceCount) {
                throw new SAMFormatException(String.format(
                        "The index %s covers %d reference sequences but the header of %s has %d, so it was not "
                                + "made for this file",
                        describeIndex(), index.getNumberOfReferences(), describeFile(), sequenceCount));
            }
            return index;
        } catch (final RuntimeException e) {
            index.close();
            throw e;
        }
    }

    /**
     * @param source an index made by tabix
     * @param header what that index says of the file
     * @return the index with each reference under the number the header gives the sequence of that name
     */
    private ReferenceBinsSource numberedAsTheHeaderDoes(
            final ReferenceBinsSource source, final TabixIndex.Header header) {
        if (!TabixFormat.SAM.equals(header.format())) {
            throw new SAMFormatException(
                    "The index " + describeIndex() + " was made by tabix for a format other than SAM");
        }
        final int[] indexOrdinals = new int[mFileHeader.getSequenceDictionary().size()];
        Arrays.fill(indexOrdinals, -1);
        for (int i = 0; i < header.sequenceNames().size(); i++) {
            final String name = header.sequenceNames().get(i);
            if (name.equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME)) {
                continue; // tabix lists the reads without a position as a sequence of their own
            }
            final int headerOrdinal = mFileHeader.getSequenceIndex(name);
            if (headerOrdinal == -1) {
                throw new SAMFormatException(String.format(
                        "The index %s names the sequence %s, which the header of %s does not have, so it was not "
                                + "made for this file",
                        describeIndex(), name, describeFile()));
            }
            if (indexOrdinals[headerOrdinal] != -1) {
                throw new SAMFormatException(
                        "The index " + describeIndex() + " lists the sequence " + name + " more than once");
            }
            indexOrdinals[headerOrdinal] = i;
        }
        return new RenumberedReferenceBinsSource(source, indexOrdinals);
    }

    private String describeIndex() {
        return mIndexPath != null ? mIndexPath.toString() : mIndexStream.getSource();
    }

    private String describeFile() {
        return mPath != null ? mPath.toString() : "the SAM text";
    }

    @Override
    public void close() {
        if (mReader != null) {
            try {
                mReader.close();
            } finally {
                mReader = null;
            }
        }
        if (mIndex != null) {
            mIndex.close();
            mIndex = null;
        } else if (mIndexStream != null && !mIndexStreamHandedOver) {
            try {
                mIndexStream.close();
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
        }
        mIndexStream = null;
    }

    @Override
    public SAMFileHeader getFileHeader() {
        return mFileHeader;
    }

    @Override
    public ValidationStringency getValidationStringency() {
        return validationStringency;
    }

    @Override
    public void setValidationStringency(final ValidationStringency stringency) {
        this.validationStringency = stringency;
    }

    /**
     * There can only be one extant iterator on a SAMTextReader at a time.  The previous one must
     * be closed before calling getIterator().  Unless the input is block-compressed and seekable, closing an
     * iterator closes the reader, since the rest of the input cannot be reached again.
     *
     * @return Iterator of SAMRecords in file order.
     */
    @Override
    public CloseableIterator<SAMRecord> getIterator() {
        if (mReader == null) {
            throw new IllegalStateException("File reader is closed");
        }
        if (mIterator != null) {
            throw new IllegalStateException("Iteration in progress");
        }
        if (mIsSeekable) {
            return getIterator(getFilePointerSpanningReads());
        }
        mIterator = new RecordIterator();
        return mIterator;
    }

    /**
     * Reads the records that start within a span of the file.  Supported only for block-compressed text that can be
     * seeked in.
     *
     * @param fileSpan The file span.
     * @return An iterator over the given file span.
     */
    @Override
    public CloseableIterator<SAMRecord> getIterator(final SAMFileSpan fileSpan) {
        if (!mIsSeekable) {
            throw new UnsupportedOperationException(
                    "Cannot directly iterate over regions within SAM text files unless block-compressed and seekable.");
        }
        if (mReader == null) {
            throw new IllegalStateException("File reader is closed");
        }
        if (mIterator != null) {
            throw new IllegalStateException("Iteration in progress");
        }
        if (!(fileSpan instanceof BAMFileSpan)) {
            throw new IllegalArgumentException("A span of block-compressed SAM text must be a BAMFileSpan");
        }
        mIterator = new SpanIterator(((BAMFileSpan) fileSpan).toCoordinateArray());
        return mIterator;
    }

    /**
     * Gets a pointer spanning all the reads in the file.  Supported only for block-compressed text.
     *
     * @return A span from the first read in the file to its end.
     */
    @Override
    public SAMFileSpan getFilePointerSpanningReads() {
        if (mBlockStream == null) {
            throw new UnsupportedOperationException(
                    "Cannot retrieve file pointers within SAM text files unless block-compressed.");
        }
        return new BAMFileSpan(new Chunk(mFirstRecordPointer, Long.MAX_VALUE));
    }

    /** Supported only for block-compressed text that has an index. */
    @Override
    public CloseableIterator<SAMRecord> query(final QueryInterval[] intervals, final boolean contained) {
        assertQueryable();
        QueryInterval.assertIntervalsOptimized(intervals);
        final BAMFileSpan span = BAMFileReader.getFileSpan(intervals, getIndex());
        return new QueryFilteringIterator(
                getIterator(span == null ? new BAMFileSpan() : span),
                new BAMQueryMultipleIntervalsIteratorFilter(intervals, contained));
    }

    /** Supported only for block-compressed text that has an index. */
    @Override
    public CloseableIterator<SAMRecord> queryAlignmentStart(final String sequence, final int start) {
        assertQueryable();
        final int referenceIndex = mFileHeader.getSequenceIndex(sequence);
        final BAMFileSpan span = referenceIndex == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX
                ? new BAMFileSpan()
                : getIndex().getSpanOverlapping(referenceIndex, start, 0);
        return new QueryFilteringIterator(getIterator(span), new BAMStartingAtIteratorFilter(referenceIndex, start));
    }

    /** Supported only for block-compressed text that has an index. */
    @Override
    public CloseableIterator<SAMRecord> queryUnmapped() {
        assertQueryable();
        // The reads without a position follow every read that has one, so the search starts from the last place the
        // index knows of, or from the top if it knows of none.
        final long startOfLastLinearBin = getIndex().getStartOfLastLinearBin();
        final long start = startOfLastLinearBin == -1 ? mFirstRecordPointer : startOfLastLinearBin;
        return new QueryFilteringIterator(
                getIterator(new BAMFileSpan(new Chunk(start, Long.MAX_VALUE))),
                record -> record.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX
                        ? BAMIteratorFilter.FilteringIteratorState.MATCHES_FILTER
                        : BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION);
    }

    private void assertQueryable() {
        if (!hasIndex()) {
            throw new UnsupportedOperationException(
                    "Cannot query SAM text files unless block-compressed, seekable and indexed");
        }
    }

    private void readHeader() {
        final SAMTextHeaderCodec headerCodec = new SAMTextHeaderCodec();
        headerCodec.setValidationStringency(validationStringency);
        mFileHeader = headerCodec.decode(mReader, (mPath != null ? mPath.toString() : null));
        mLinesBeforeFirstRecord = mReader.getLineNumber();
        if (mBlockStream != null) {
            mFirstRecordPointer = mBlockStream.filePointerAt(mReader.getBytesConsumed());
            mLineEndPointer = mFirstRecordPointer;
        }
        advanceLine();
    }

    private void advanceLine() {
        mLineStartPointer = mLineEndPointer;
        mHasCurrentLine = mReader.readNextLine();
        if (mBlockStream != null) {
            mLineEndPointer = mBlockStream.filePointerAt(mReader.getBytesConsumed());
        }
    }

    /** Moves to a virtual offset at which a line starts; the next line read is that one. */
    private void seek(final long filePointer) {
        try {
            mBlockStream.seek(filePointer);
        } catch (final IOException e) {
            throw new RuntimeIOException("Error seeking in " + (mPath != null ? mPath : "SAM text"), e);
        }
        mReader.reset();
        mLineEndPointer = filePointer;
        mLineNumbersKnown = filePointer == mFirstRecordPointer;
    }

    /** @return the number of the line in hand, or -1 if reading did not run on from the top of the file */
    private int currentLineNumber() {
        return mLineNumbersKnown ? mLinesBeforeFirstRecord + mReader.getLineNumber() : -1;
    }

    /**
     * SAMRecord iterator for SAMTextReader
     */
    private class RecordIterator implements CloseableIterator<SAMRecord> {

        private final SAMLineParser parser =
                new SAMLineParser(samRecordFactory, validationStringency, mFileHeader, mParentReader, mPath);

        private RecordIterator() {
            if (mReader == null) {
                throw new IllegalStateException("Reader is closed.");
            }
        }

        @Override
        public void close() {
            if (mIsSeekable) {
                mIterator = null;
            } else {
                SAMTextReader.this.close();
            }
        }

        /** Takes up the next line to be returned, if there is one. */
        void advance() {
            advanceLine();
        }

        @Override
        public boolean hasNext() {
            return mHasCurrentLine;
        }

        @Override
        public SAMRecord next() {
            if (!hasNext()) {
                throw new IllegalStateException("Cannot call next() on exhausted iterator");
            }
            try {
                final SAMRecord record = parser.parseLineFromBytes(
                        mReader.getLineBuffer(), mReader.getLineOffset(), mReader.getLineLength(), currentLineNumber());
                if (mParentReader != null && mBlockStream != null) {
                    record.setFileSource(new SAMFileSource(
                            mParentReader, new BAMFileSpan(new Chunk(mLineStartPointer, mLineEndPointer))));
                }
                return record;
            } finally {
                advance();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not supported: remove");
        }
    }

    /** Reads the lines that start within each of a list of chunks in turn. */
    private class SpanIterator extends RecordIterator {
        private final long[] filePointers;
        private int nextFilePointer = 0;
        private long chunkEnd = -1; // before everything, so that the first advance moves to the first chunk

        /**
         * @param filePointers the starts and ends of the chunks, as virtual offsets, in the order to read them; null
         *     if there are none, which is how an empty {@link BAMFileSpan} puts it
         */
        private SpanIterator(final long[] filePointers) {
            this.filePointers = filePointers == null ? new long[0] : filePointers;
            advance();
        }

        @Override
        void advance() {
            while (mLineEndPointer >= chunkEnd) {
                if (nextFilePointer >= filePointers.length) {
                    mHasCurrentLine = false;
                    return;
                }
                seek(filePointers[nextFilePointer++]);
                chunkEnd = filePointers[nextFilePointer++];
            }
            advanceLine();
        }
    }
}
