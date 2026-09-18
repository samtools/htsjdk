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

import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.SamLineReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Internal class for reading SAM text files. Text that is block-compressed is read a BGZF block at a time, which
 * lets each record say where in the file it lies, as a record of a BAM does; and if the file can be seeked in, it
 * can be read from any such place.
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
     * @param seekable whether {@code stream} can seek, without which the file can only be read through once
     * @param path     For error reporting only; may be null.
     */
    SAMTextReader(
            final BlockCompressedInputStream stream,
            final boolean seekable,
            final Path path,
            final ValidationStringency validationStringency,
            final SAMRecordFactory factory) {
        mBlockStream = new BlockAtATimeInputStream(stream);
        mIsSeekable = seekable;
        mReader = new SamLineReader(mBlockStream);
        mPath = path;
        this.validationStringency = validationStringency;
        this.samRecordFactory = factory;
        readHeader();
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
    public boolean hasIndex() {
        return false;
    }

    @Override
    public BAMIndex getIndex() {
        throw new UnsupportedOperationException();
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

    /**
     * Unsupported for SAM text files.
     */
    public CloseableIterator<SAMRecord> query(
            final String sequence, final int start, final int end, final boolean contained) {
        throw new UnsupportedOperationException("Cannot query SAM text files");
    }

    @Override
    public CloseableIterator<SAMRecord> query(final QueryInterval[] intervals, final boolean contained) {
        throw new UnsupportedOperationException("Cannot query SAM text files");
    }

    /**
     * Unsupported for SAM text files.
     */
    @Override
    public CloseableIterator<SAMRecord> queryAlignmentStart(final String sequence, final int start) {
        throw new UnsupportedOperationException("Cannot query SAM text files");
    }

    @Override
    public CloseableIterator<SAMRecord> queryUnmapped() {
        throw new UnsupportedOperationException("Cannot query SAM text files");
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
         * @param filePointers the starts and ends of the chunks, as virtual offsets, in the order to read them
         */
        private SpanIterator(final long[] filePointers) {
            this.filePointers = filePointers;
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
