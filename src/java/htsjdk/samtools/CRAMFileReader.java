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
import java.util.Arrays;
import java.util.Iterator;

/**
 * {@link htsjdk.samtools.BAMFileReader BAMFileReader} analogue for CRAM files.
 * Supports random access using BAI index file formats.
 *
 * @author vadim
 */
public class CRAMFileReader extends SamReader.ReaderImplementation implements SamReader.Indexing {
    private File file;
    private final ReferenceSource referenceSource;
    private InputStream is;
    private CRAMIterator it;
    private BAMIndex mIndex;
    private File mIndexFile;
    private boolean mEnableIndexCaching;
    private boolean mEnableIndexMemoryMapping;

    private ValidationStringency validationStringency;

    /**
     * Open CRAM data for reading using either the file or the input stream
     * supplied in the arguments. The
     * {@link htsjdk.samtools.Defaults#REFERENCE_FASTA default} reference fasta
     * file will be used.
     *
     * @param file CRAM file to open
     * @param is   CRAM stream to read
     */
    public CRAMFileReader(final File file, final InputStream is) {
        this(file, is, new ReferenceSource(Defaults.REFERENCE_FASTA));
    }

    /**
     * Open CRAM data for reading using either the file or the input stream
     * supplied in the arguments.
     *
     * @param file            CRAM file to read
     * @param is              index file to be used for random access
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.ReferenceSource source} of
     *                        reference sequences
     */
    public CRAMFileReader(final File file, final InputStream is,
                          final ReferenceSource referenceSource) {
        if (file == null && is == null)
            throw new IllegalArgumentException(
                    "Either file or input stream is required.");

        this.file = file;
        this.is = is;
        this.referenceSource = referenceSource;
        getIterator();
    }

    /**
     * Open CRAM file for reading. If index file is supplied than random access
     * will be available.
     *
     * @param cramFile        CRAM file to read
     * @param indexFile       index file to be used for random access
     * @param referenceSource a {@link htsjdk.samtools.cram.ref.ReferenceSource source} of
     *                        reference sequences
     */
    public CRAMFileReader(final File cramFile, final File indexFile,
                          final ReferenceSource referenceSource) {
        if (cramFile == null)
            throw new IllegalArgumentException("File is required.");

        this.file = cramFile;
        this.mIndexFile = indexFile;
        this.referenceSource = referenceSource;

        getIterator();
    }

    public CRAMFileReader(final File file, final ReferenceSource referenceSource) {
        if (file == null && is == null)
            throw new IllegalArgumentException(
                    "Either file or input stream is required.");

        this.file = file;
        this.referenceSource = referenceSource;

        getIterator();
    }

    public CRAMFileReader(final InputStream is, final SeekableStream indexInputStream,
                          final ReferenceSource referenceSource, ValidationStringency validationStringency) throws IOException {
        this.is = is;
        this.referenceSource = referenceSource;
        this.validationStringency = validationStringency;

        it = new CRAMIterator(is, referenceSource);
        it.setValidationStringency(validationStringency);
        mIndex = new CachingBAMFileIndex(indexInputStream, it.getSAMFileHeader().getSequenceDictionary());
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
            throw new SAMException("No index is available for this BAM file.");
        if (mIndex == null) {
            final SAMSequenceDictionary dictionary = getFileHeader()
                    .getSequenceDictionary();
            mIndex = mEnableIndexCaching ? new CachingBAMFileIndex(mIndexFile,
                    dictionary, mEnableIndexMemoryMapping)
                    : new DiskBasedBAMFileIndex(mIndexFile, dictionary,
                    mEnableIndexMemoryMapping);
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
    public SAMRecordIterator iterator(SAMFileSpan fileSpan) {
        // get the file coordinates for the span:
        long[] coordinateArray = ((BAMFileSpan) fileSpan).toCoordinateArray();
        if (coordinateArray == null || coordinateArray.length == 0) return emptyIterator;
        try {
            // create an input stream that reads the source cram stream only within the coordinate pairs:
            SeekableStream ss = getSeekableStreamOrFailWithRTE();
            return new CRAMIterator(ss, referenceSource, coordinateArray);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SAMFileHeader getFileHeader() {
        return it.getSAMFileHeader();
    }

    @Override
    public SAMRecordIterator getIterator() {
        if (it != null && file == null)
            return it;
        try {
            final CRAMIterator si;
            if (file != null) {
                si = new CRAMIterator(new FileInputStream(file),
                        referenceSource);
            } else
                si = new CRAMIterator(is, referenceSource);

            si.setValidationStringency(validationStringency);
            it = si;
            return it;
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
        return new BAMFileSpan(new Chunk(it.firstContainerOffset << 16, Long.MAX_VALUE));
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
        long[] filePointers = null;

        // Hit the index to determine the chunk boundaries for the required data.
        final SAMFileHeader fileHeader = getFileHeader();
        final int referenceIndex = fileHeader.getSequenceIndex(sequence);
        if (referenceIndex != -1) {
            final BAMIndex fileIndex = getIndex();
            final BAMFileSpan fileSpan = fileIndex.getSpanOverlapping(
                    referenceIndex, start, -1);
            filePointers = fileSpan != null ? fileSpan.toCoordinateArray()
                    : null;
        }

        if (filePointers == null || filePointers.length == 0)
            return emptyIterator;

        Container c;
        final SeekableStream s = getSeekableStreamOrFailWithRTE();
        for (int i = 0; i < filePointers.length; i += 2) {
            final long containerOffset = filePointers[i] >>> 16;

            try {
                if (s.position() != containerOffset || it.container == null) {
                    s.seek(containerOffset);
                    c = ContainerIO.readContainerHeader(it.getCramHeader().getVersion().major, s);
                    if (c.alignmentStart + c.alignmentSpan > start) {
                        s.seek(containerOffset);
                        it.jumpWithinContainerToPos(fileHeader.getSequenceIndex(sequence), start);
                        return new IntervalIterator(it, new QueryInterval(referenceIndex, start, -1));
                    }
                } else {
                    c = it.container;
                    if (c.alignmentStart + c.alignmentSpan > start) {
                        it.jumpWithinContainerToPos(fileHeader.getSequenceIndex(sequence), start);
                        return new IntervalIterator(it, new QueryInterval(referenceIndex, start, -1));
                    }
                }
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
        return it;
    }

    CloseableIterator<SAMRecord> query(final int referenceIndex,
                                       final int start, final int end, boolean overlap) throws IOException {
        long[] filePointers = null;

        // Hit the index to determine the chunk boundaries for the required data.
        if (referenceIndex != -1) {
            final BAMIndex fileIndex = getIndex();
            final BAMFileSpan fileSpan = fileIndex.getSpanOverlapping(
                    referenceIndex, start, -1);
            filePointers = fileSpan != null ? fileSpan.toCoordinateArray()
                    : null;
        }

        if (filePointers == null || filePointers.length == 0)
            return emptyIterator;

        CRAMIterator it = new CRAMIterator(getSeekableStreamOrFailWithRTE(), referenceSource, filePointers);
        return new IntervalIterator(it, new QueryInterval(referenceIndex, start, end), overlap);
    }

    @Override
    public CloseableIterator<SAMRecord> queryUnmapped() {
        final long startOfLastLinearBin = getIndex().getStartOfLastLinearBin();

        final SeekableStream s = getSeekableStreamOrFailWithRTE();
        final CRAMIterator si;
        try {
            s.seek(0);
            si = new CRAMIterator(s, referenceSource);
            si.setValidationStringency(validationStringency);
            s.seek(startOfLastLinearBin >>> 16);
            Container c = ContainerIO.readContainerHeader(si.getCramHeader().getVersion().major, s);
            s.seek(s.position() + c.containerByteSize);
            it = si;
            it.jumpWithinContainerToPos(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, SAMRecord.NO_ALIGNMENT_START);
        } catch (final IOException e) {
            throw new RuntimeEOFException(e);
        }

        return it;
    }

    private SeekableStream getSeekableStreamOrFailWithRTE() {
        SeekableStream s = null;
        if (file != null) {
            try {
                s = new SeekableFileStream(file);
            } catch (final FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else if (is instanceof SeekableStream)
            s = (SeekableStream) is;
        return s;
    }

    @Override
    public void close() {
        CloserUtil.close(it);
        CloserUtil.close(is);
        CloserUtil.close(mIndex);
    }

    @Override
    void setValidationStringency(final ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
        if (it != null) it.setValidationStringency(validationStringency);
    }

    @Override
    public ValidationStringency getValidationStringency() {
        return validationStringency;
    }

    @Override
    public CloseableIterator<SAMRecord> query(final QueryInterval[] intervals,
                                              final boolean contained) {
        return new MultiIntervalIterator(Arrays.asList(intervals).iterator(), !contained);
    }

    @Override
    public Type type() {
        return Type.CRAM_TYPE;
    }

    @Override
    void enableFileSource(final SamReader reader, final boolean enabled) {
        if (it != null)
            it.setFileSource(enabled ? reader : null);
    }

    private class MultiIntervalIterator implements SAMRecordIterator {
        private final Iterator<QueryInterval> queries;
        private CloseableIterator<SAMRecord> iterator;
        private final boolean overlap;

        public MultiIntervalIterator(Iterator<QueryInterval> queries, boolean overlap) {
            this.queries = queries;
            this.overlap = overlap;
        }

        @Override
        public SAMRecordIterator assertSorted(SortOrder sortOrder) {
            return null;
        }

        @Override
        public void close() {

        }

        @Override
        public boolean hasNext() {
            if (iterator == null || !iterator.hasNext()) {
                if (!queries.hasNext()) return false;
                do {
                    QueryInterval query = queries.next();
                    try {
                        iterator = query(query.referenceIndex, query.start, query.end, overlap);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } while (!iterator.hasNext() && queries.hasNext());
            }
            return iterator.hasNext();
        }

        @Override
        public SAMRecord next() {
            return iterator.next();
        }

        @Override
        public void remove() {
            iterator.remove();
        }
    }

    public static class IntervalIterator implements SAMRecordIterator {
        private final CloseableIterator<SAMRecord> delegate;
        private final QueryInterval interval;
        private SAMRecord next;
        private boolean noMore = false;
        private final boolean overlap;

        public IntervalIterator(CloseableIterator<SAMRecord> delegate, QueryInterval interval) {
            this(delegate, interval, true);
        }

        public IntervalIterator(CloseableIterator<SAMRecord> delegate, QueryInterval interval, boolean overlap) {
            this.delegate = delegate;
            this.interval = interval;
            this.overlap = overlap;
        }

        @Override
        public SAMRecordIterator assertSorted(SortOrder sortOrder) {
            return null;
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public boolean hasNext() {
            if (next != null) return true;
            if (noMore) return false;

            while (delegate.hasNext()) {
                next = delegate.next();

                if (isWithinTheInterval(next)) break;
                if (isPassedTheInterval(next)) {
                    next = null;
                    noMore = true;
                    return false;
                }
                next = null;
            }

            return next != null;
        }

        boolean isWithinTheInterval(SAMRecord record) {
            boolean refMatch = record.getReferenceIndex() == interval.referenceIndex;
            if (interval.start == -1) return refMatch;

            int start = record.getAlignmentStart();
            int end = record.getAlignmentEnd();
            if (overlap) {
                return CoordMath.overlaps(start, end, interval.start, interval.end < 0 ? Integer.MAX_VALUE : interval.end);
            } else {
                // contained:
                return CoordMath.encloses(interval.start, interval.end < 0 ? Integer.MAX_VALUE : interval.end, start, end);
            }

        }

        boolean isPassedTheInterval(SAMRecord record) {
            if (record.getReadUnmappedFlag()) return false;
            boolean refMatch = record.getReferenceIndex() == interval.referenceIndex;
            if (!refMatch) return true;

            return interval.end != -1 && record.getAlignmentStart() > interval.end;

        }

        @Override
        public SAMRecord next() {
            SAMRecord result = next;
            next = null;
            return result;
        }

        @Override
        public void remove() {
            throw new RuntimeException("Not available.");
        }
    }
}
