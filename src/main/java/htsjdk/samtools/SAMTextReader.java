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

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.SamLineReader;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Internal class for reading SAM text files.
 */
class SAMTextReader extends SamReader.ReaderImplementation {

    private SAMRecordFactory samRecordFactory;
    private SamLineReader mReader;
    private SAMFileHeader mFileHeader = null;
    private boolean mHasCurrentLine = false;
    private RecordIterator mIterator = null;
    private Path mPath = null;

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
     * be closed before calling getIterator().  Because the input stream is not seekable, a subsequent
     * call to getIterator() returns an iterator that starts where the last one left off.
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
        mIterator = new RecordIterator();
        return mIterator;
    }

    /**
     * Generally loads data at a given point in the file.  Unsupported for SAMTextReaders.
     *
     * @param fileSpan The file span.
     * @return An iterator over the given file span.
     */
    @Override
    public CloseableIterator<SAMRecord> getIterator(final SAMFileSpan fileSpan) {
        throw new UnsupportedOperationException("Cannot directly iterate over regions within SAM text files.");
    }

    /**
     * Generally gets a pointer to the first read in the file.  Unsupported for SAMTextReaders.
     *
     * @return An pointer to the first read in the file.
     */
    @Override
    public SAMFileSpan getFilePointerSpanningReads() {
        throw new UnsupportedOperationException("Cannot retrieve file pointers within SAM text files.");
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
        advanceLine();
    }

    private void advanceLine() {
        mHasCurrentLine = mReader.readNextLine();
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
            SAMTextReader.this.close();
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
                return parser.parseLineFromBytes(
                        mReader.getLineBuffer(),
                        mReader.getLineOffset(),
                        mReader.getLineLength(),
                        mReader.getLineNumber());
            } finally {
                advanceLine();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not supported: remove");
        }
    }
}
