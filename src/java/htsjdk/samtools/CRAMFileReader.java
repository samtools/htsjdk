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
import htsjdk.samtools.util.CoordSpanInputSteam;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeEOFException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public SAMRecordIterator iterator() {
        return getIterator();
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
    public SAMRecordIterator iterator(SAMFileSpan chunks) {
        return null;
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
        // get the file coordinates for the span:
        long[] coords = ((BAMFileSpan) fileSpan).toCoordinateArray();
        if (coords[0] != 0) {
            // prepend file header coords to the coord array:
            final long[] coordsFromStart = new long[coords.length + 2];
            coordsFromStart[0] = 0;
            coordsFromStart[1] = it.firstContainerOffset;
            System.arraycopy(coords, 0, coordsFromStart, 2, coords.length);
            coords = coordsFromStart ;
        }
        try {
            // create an input stream that reads the source cram stream only within the coord pairs:
            SeekableStream ss = getSeekableStreamOrFailWithRTE();
            InputStream is = new CoordSpanInputSteam(ss, coords) ;
            return new CRAMIterator(is, referenceSource) ;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SAMFileSpan getFilePointerSpanningReads() {
        return new BAMFileSpan(new Chunk(it.firstContainerOffset,Long.MAX_VALUE));
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
                        it.jumpWithinContainerToPos(fileHeader.getSequenceIndex(sequence),start);
                        return new IntervalIterator(it, new QueryInterval(referenceIndex, start, -1));
                    }
                }
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
        return it;
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
        if (is == null) {
            throw new IllegalStateException("File reader is closed");
        }
        if (it != null) {
            throw new IllegalStateException("Iteration in progress");
        }
        if (mIndex == null && mIndexFile == null) {
            throw new UnsupportedOperationException(
                    "Cannot query stream-based BAM file");
        }
        throw new SAMException("Multiple interval queries not implemented.");
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

    private static class IntervalIterator implements SAMRecordIterator {
        private SAMRecordIterator delegate ;
        private QueryInterval interval ;
        private SAMRecord next ;
        private boolean noMore = false ;

        public IntervalIterator(SAMRecordIterator delegate, QueryInterval interval) {
            this.delegate = delegate;
            this.interval = interval;
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
            if (next != null) return true ;
            if (noMore) return false ;

            while (delegate.hasNext()) {
                next = delegate.next() ;

                if (isWithinTheInterval(next)) break ;
                if (isPassedTheInterval(next)) {
                    next = null ;
                    noMore = true ;
                    return false ;
                }
                next = null ;
            }

            return next != null;
        }

        protected boolean isWithinTheInterval(SAMRecord record) {
            boolean refMatch = record.getReferenceIndex() == interval.referenceIndex ;
            if (interval.start == -1) return refMatch ;

            boolean startMatch = record.getAlignmentStart() >=interval.start ;
            if (interval.end == -1) return startMatch ;

            return record.getAlignmentStart() <=interval.end;
        }

        protected boolean isPassedTheInterval(SAMRecord record) {
            boolean refMatch = record.getReferenceIndex() == interval.referenceIndex ;
            if (!refMatch) return true ;

            if (interval.end == -1) return false ;

            return record.getAlignmentStart() >interval.end;
        }

        @Override
        public SAMRecord next() {
            SAMRecord result = next;
            next = delegate.next();
            return result ;
        }

        @Override
        public void remove() {
            throw new RuntimeException("Not available.") ;
        }
    }

    public static void main(String[] args) {
        File cramFile = new File(args[0]);
        File refFile = new File(args[1]);
        String query = args[2];

//        File cramFile = new File ("H:\\dev\\Data\\CRAM_TEST_DATA\\HG00551.unmapped.ILLUMINA.bwa.PUR.low_coverage.20120522.cram") ;
//        File refFile = new File ("H:\\dev\\Data\\CRAM_TEST_DATA\\hs37d5.fa") ;
//        String query = "Y:1";

        Log.setGlobalLogLevel(Log.LogLevel.ERROR);
        CRAMFileReader reader = new CRAMFileReader(cramFile, new File (cramFile.getAbsolutePath()+".bai"), new ReferenceSource(refFile)) ;
        final SAMSequenceDictionary sequenceDictionary = reader.getFileHeader().getSequenceDictionary();
        QueryInterval interval = queryFromString(query, sequenceDictionary);

        final CloseableIterator<SAMRecord> iterator = reader.queryAlignmentStart(sequenceDictionary.getSequence(interval.referenceIndex).getSequenceName(), interval.start);
        while (iterator.hasNext()) {
            SAMRecord record = iterator.next() ;
            System.out.print(record.getSAMString());
        }
    }

    private static Pattern queryPattern = Pattern.compile("^([^:\\s]+)(?::(\\d+)(?:-(\\d+))?)?$");

    private static QueryInterval queryFromString(String s, SAMSequenceDictionary dic) {

        Matcher m = queryPattern.matcher(s);
        if (m.matches()) {
            String refName = m.group(1);
            int refIndex = dic.getSequenceIndex(refName);
            int start = -1;
            int end = -1;

            if (m.groupCount() > 2) start = Integer.parseInt(m.group(2));
            if (m.groupCount() > 3) end = Integer.parseInt(m.group(2));
            return new QueryInterval(refIndex, start, end);
        }
        return null;
    }
}
