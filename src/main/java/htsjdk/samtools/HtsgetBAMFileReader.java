package htsjdk.samtools;

import htsjdk.samtools.filter.FilteringSamIterator;
import htsjdk.samtools.filter.SamRecordFilter;
import htsjdk.samtools.util.*;
import htsjdk.samtools.util.htsget.HtsgetClass;
import htsjdk.samtools.util.htsget.HtsgetRequest;
import htsjdk.samtools.util.zip.InflaterFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;

/**
 * Class for reading and querying BAM files from an htsget source
 */
public class HtsgetBAMFileReader implements SamReader.PrimitiveSamReader {
    private final URI mSource;

    private final SAMFileHeader mFileHeader;

    private final InflaterFactory mInflaterFactory;

    // If true, all SAMRecords are fully decoded as they are read.
    private boolean mEagerDecode;

    private boolean mCheckCRC;

    private final boolean mUseAsynchronousIO;

    // For error-checking.
    private ValidationStringency mValidationStringency;

    // For creating BAMRecords
    private SAMRecordFactory mSamRecordFactory;

    private SamReader mReader;

    // Hold on to all query iterators we've given out so we can close them
    private final List<CloseableIterator<SAMRecord>> iterators;

    /**
     * Prepare to read BAM from an htsget source
     *
     * @param source               source of bytes.
     * @param eagerDecode          if true, decode all BAM fields as reading rather than lazily.
     * @param validationStringency Controls how to handle invalidate reads or header lines.
     * @param samRecordFactory     SAM record factory
     * @param useAsynchronousIO    if true, use asynchronous I/O
     */
    public HtsgetBAMFileReader(final URI source,
                               final boolean eagerDecode,
                               final ValidationStringency validationStringency,
                               final SAMRecordFactory samRecordFactory,
                               final boolean useAsynchronousIO) throws IOException {
        this(source, eagerDecode, validationStringency, samRecordFactory, useAsynchronousIO, BlockGunzipper.getDefaultInflaterFactory());
    }

    /**
     * Prepare to read BAM from a htsget source
     *
     * @param source               source of bytes.
     * @param eagerDecode          if true, decode all BAM fields as reading rather than lazily.
     * @param validationStringency Controls how to handle invalidate reads or header lines.
     * @param samRecordFactory     SAM record factory
     * @param useAsynchronousIO    if true, use asynchronous I/O
     * @param inflaterFactory      InflaterFactory used by BlockCompressedInputStream
     */
    public HtsgetBAMFileReader(final URI source,
                               final boolean eagerDecode,
                               final ValidationStringency validationStringency,
                               final SAMRecordFactory samRecordFactory,
                               final boolean useAsynchronousIO,
                               final InflaterFactory inflaterFactory) throws IOException {
        this.mSource = source;
        this.mEagerDecode = eagerDecode;
        this.mValidationStringency = validationStringency;
        this.mSamRecordFactory = samRecordFactory;
        this.mUseAsynchronousIO = useAsynchronousIO;
        this.mInflaterFactory = inflaterFactory;

        final HtsgetRequest req = new HtsgetRequest(this.mSource).withDataClass(HtsgetClass.header);
        // Request only the header and use it to construct a SAMFileHeader for this reader
        try (final InputStream headerStream = req.getResponse().getDataStream()) {
            final BinaryCodec headerCodec = new BinaryCodec(
                new DataInputStream(this.mUseAsynchronousIO
                    ? new AsyncBlockCompressedInputStream(headerStream, this.mInflaterFactory)
                    : new BlockCompressedInputStream(headerStream, this.mInflaterFactory)));
            this.mFileHeader = BAMFileReader.readHeader(headerCodec, this.mValidationStringency, null);
        }

        this.iterators = new ArrayList<>();
    }

    /**
     * Set error-checking level for subsequent SAMRecord reads.
     */
    public void setValidationStringency(final ValidationStringency validationStringency) {
        this.mValidationStringency = validationStringency;
    }

    /**
     * Set SAMRecordFactory for subsequent SAMRecord reads.
     */
    public void setSAMRecordFactory(final SAMRecordFactory samRecordFactory) {
        this.mSamRecordFactory = samRecordFactory;
    }

    /**
     * Set whether to eagerly decode subsequent SAMRecord reads.
     */
    public void setEagerDecode(final boolean eagerDecode) {
        this.mEagerDecode = eagerDecode;
    }

    /**
     * Set whether to check CRC for subsequent iterator or query requests.
     */
    public void enableCrcChecking(final boolean check) {
        this.mCheckCRC = check;
    }

    /**
     * Set whether to write the source of every read into the source SAMRecords.
     */
    public void enableFileSource(final SamReader reader, final boolean enabled) {
        this.mReader = enabled ? reader : null;
    }

    @Override
    public SamReader.Type type() {
        return SamReader.Type.BAM_TYPE;
    }

    /**
     * @return false, since htsget sources never have indices
     */
    @Override
    public boolean hasIndex() {
        return false;
    }

    @Override
    public BAMIndex getIndex() {
        throw new UnsupportedOperationException("Cannot retrieve index from Htsget data source");
    }

    @Override
    public SAMFileHeader getFileHeader() {
        return this.mFileHeader;
    }

    /**
     * Prepare to iterate through the SAMRecords in file order.
     * Unlike file-based BAM readers, multiple iterators may be open at the same time
     */
    @Override
    public CloseableIterator<SAMRecord> getIterator() {
        final HtsgetRequest req = new HtsgetRequest(this.mSource);
        final CloseableIterator<SAMRecord> queryIterator = new HtsgetBAMFileIterator(req);
        this.iterators.add(queryIterator);
        return queryIterator;
    }

    /**
     * Generally loads data at a given point in the file.  Unsupported for HtsgetBAMFileReaders.
     *
     * @param fileSpan The file span.
     * @return An iterator over the given file span.
     */
    @Override
    public CloseableIterator<SAMRecord> getIterator(final SAMFileSpan fileSpan) {
        throw new UnsupportedOperationException("Cannot query htsget data source by file span");
    }

    /**
     * Generally gets a pointer to the first read in the file.  Unsupported for HtsgetBAMFileReaders.
     *
     * @return An pointer to the first read in the file.
     */
    @Override
    public SAMFileSpan getFilePointerSpanningReads() {
        throw new UnsupportedOperationException("Cannot retrieve file pointers from htsget data source");
    }

    /**
     * Prepare to iterate through the SAMRecords that match the given interval.
     * Unlike file-based BAM readers, multiple iterators may be open at the same time
     * <p>
     * Note that an unmapped SAMRecord may still have a reference name and an alignment start for sorting
     * purposes (typically this is the coordinate of its mate), and will be found by this method if the coordinate
     * matches the specified interval.
     *
     * @param intervals the intervals to include
     * @param contained If true, the alignments for the SAMRecords must be completely contained in the interval
     *                  specified by start and end.  If false, the SAMRecords need only overlap the interval.
     * @return Iterator for the matching SAMRecords
     */
    @Override
    public CloseableIterator<SAMRecord> query(final QueryInterval[] intervals, final boolean contained) {
        QueryInterval.assertIntervalsOptimized(intervals);
        final Interval[] namedIntervals = Arrays.stream(intervals)
            .map(i -> new Interval(this.mFileHeader.getSequence(i.referenceIndex).getSequenceName(), i.start, i.end))
            .toArray(Interval[]::new);
        return this.query(namedIntervals, contained);
    }

    public CloseableIterator<SAMRecord> query(final String sequence, final int start, final int end, final boolean contained) {
        return this.query(new Interval[]{new Interval(sequence, start, end == -1 ? Integer.MAX_VALUE : end)}, contained);
    }

    /**
     * Query intervals directly by contig name instead of index relative to reference,
     * to avoid repeated conversion between name and index representations
     * <p>
     * Callers much ensure that the intervals are in increasing order and do not overlap or abut
     *
     * @param intervals intervals to query by
     * @param contained only return reads that are fully contained and not just overlapping if this is true
     */
    public CloseableIterator<SAMRecord> query(final Interval[] intervals, final boolean contained) {
        final CloseableIterator<SAMRecord> queryIterator = new BAMQueryChainingIterator(intervals, contained);
        this.iterators.add(queryIterator);
        return queryIterator;
    }

    /**
     * Prepare to iterate through the SAMRecords with the given alignment start.
     * Unlike file-based BAM readers, multiple iterators may be open at the same time
     * <p>
     * Note that an unmapped SAMRecord may still have a reference name and an alignment start for sorting
     * purposes (typically this is the coordinate of its mate), and will be found by this method if the coordinate
     * matches the specified interval.
     *
     * @param sequence Reference sequence sought.
     * @param start    Alignment start sought.
     * @return Iterator for the matching SAMRecords.
     */
    @Override
    public CloseableIterator<SAMRecord> queryAlignmentStart(final String sequence, final int start) {
        final int referenceIndex = this.mFileHeader.getSequenceIndex(sequence);
        if (referenceIndex == -1) {
            return new CloseableIterator<SAMRecord>() {
                @Override
                public void close() {
                }

                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public SAMRecord next() {
                    return null;
                }
            };
        } else {
            final HtsgetRequest req = new HtsgetRequest(this.mSource).withInterval(new Interval(sequence, start, Integer.MAX_VALUE));
            final CloseableIterator<SAMRecord> queryIterator = new HtsgetBAMFileIterator(req);
            final BAMStartingAtIteratorFilter filter = new BAMStartingAtIteratorFilter(this.mFileHeader.getSequenceIndex(sequence), start);
            final CloseableIterator<SAMRecord> filteredIterator = new BAMQueryFilteringIterator(queryIterator, filter);
            this.iterators.add(filteredIterator);
            return filteredIterator;
        }
    }

    /**
     * Prepare to iterate through the SAMRecords that are unmapped and do not have a reference name or alignment start.
     * Unlike file-based BAM readers, multiple iterators may be open at the same time
     * <p>
     *
     * @return Iterator for the matching SAMRecords.
     */
    @Override
    public CloseableIterator<SAMRecord> queryUnmapped() {
        final HtsgetRequest req = new HtsgetRequest(this.mSource).withInterval(HtsgetRequest.UNMAPPED_UNPLACED_INTERVAL);
        final CloseableIterator<SAMRecord> queryIterator = new HtsgetBAMFileIterator(req);
        this.iterators.add(queryIterator);
        return queryIterator;
    }

    @Override
    public void close() {
        this.iterators.forEach(CloseableIterator::close);
    }

    @Override
    public ValidationStringency getValidationStringency() {
        return this.mValidationStringency;
    }

    /**
     * Execute an htsget request and return an input stream of its data, inflated using this reader's inflater factory,
     * optionally using asynchronous IO, and with the bytes of the header skipped such that reads can immediately be
     * decoded from the returned stream
     *
     * @param req an htsget request
     * @return the inflated stream with header skipped
     */
    private BlockCompressedInputStream getRequestStream(final HtsgetRequest req) {
        final InputStream stream = req.getResponse().getDataStream();

        final BlockCompressedInputStream compressedInputStream = this.mUseAsynchronousIO
            ? new AsyncBlockCompressedInputStream(stream, this.mInflaterFactory)
            : new BlockCompressedInputStream(stream, this.mInflaterFactory);
        if (this.mCheckCRC) {
            compressedInputStream.setCheckCrcs(true);
        }

        // Skip over the header
        try {
            BAMFileReader.readHeader(new BinaryCodec(compressedInputStream), ValidationStringency.SILENT, null);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return compressedInputStream;
    }

    private class HtsgetBAMFileIterator implements CloseableIterator<SAMRecord> {
        private final BlockCompressedInputStream stream;
        private final BAMRecordCodec bamRecordCodec;
        private SAMRecord currentRecord;
        private int samRecordIndex = 0;

        public HtsgetBAMFileIterator(final HtsgetRequest req) {
            this.stream = HtsgetBAMFileReader.this.getRequestStream(req);
            this.bamRecordCodec = new BAMRecordCodec(
                HtsgetBAMFileReader.this.mFileHeader,
                HtsgetBAMFileReader.this.mSamRecordFactory);
            this.bamRecordCodec.setInputStream(new DataInputStream(this.stream));
            this.advance();
        }

        @Override
        public void close() {
            try {
                this.stream.close();
            } catch (final IOException e) {
                throw new RuntimeIOException("Exception while closing HtsgetBAMFileIterator", e);
            }
        }

        @Override
        public boolean hasNext() {
            return this.currentRecord != null;
        }

        @Override
        public SAMRecord next() {
            final SAMRecord result = this.currentRecord;
            this.advance();
            return result;
        }

        private SAMRecord getNextRecord() {
            final long startCoordinate = this.stream.getFilePointer();
            final SAMRecord next = this.bamRecordCodec.decode();
            final long stopCoordinate = this.stream.getFilePointer();

            if (HtsgetBAMFileReader.this.mReader != null && next != null) {
                next.setFileSource(new SAMFileSource(
                    HtsgetBAMFileReader.this.mReader,
                    new BAMFileSpan(new Chunk(startCoordinate, stopCoordinate))));
            }
            return next;
        }

        private void advance() {
            this.currentRecord = this.getNextRecord();

            if (this.currentRecord != null) {
                ++this.samRecordIndex;
                // Because some decoding is done lazily, the record needs to remember the validation stringency.
                this.currentRecord.setValidationStringency(HtsgetBAMFileReader.this.mValidationStringency);

                if (HtsgetBAMFileReader.this.mValidationStringency != ValidationStringency.SILENT) {
                    final boolean firstErrorOnly = HtsgetBAMFileReader.this.mValidationStringency == ValidationStringency.STRICT;
                    SAMUtils.processValidationErrors(
                        this.currentRecord.isValid(firstErrorOnly),
                        this.samRecordIndex,
                        HtsgetBAMFileReader.this.mValidationStringency);
                }
            }
            if (HtsgetBAMFileReader.this.mEagerDecode && this.currentRecord != null) {
                this.currentRecord.eagerDecode();
            }
        }
    }

    /**
     * Iterator over reads from the source that match the provided intervals
     * <p>
     * Makes an htsget request for each interval lazily and filters out reads that are duplicated across two intervals
     */
    // TODO: remove this class and replace with HtsgetBAMFileIterator reading from htsget POST api once implemented
    private class BAMQueryChainingIterator implements CloseableIterator<SAMRecord> {
        private final Interval[] intervals;
        private final boolean contained;
        private final Iterator<Lazy<HtsgetBAMFileIterator>> iterators;

        private CloseableIterator<SAMRecord> currentIterator;
        private SAMRecord currentRecord;
        private int currentIntervalIndex = 0;

        public BAMQueryChainingIterator(final Interval[] intervals, final boolean contained) {
            this.intervals = intervals;
            this.contained = contained;
            this.iterators = Arrays.stream(intervals)
                .map(i -> new Lazy<>(() -> {
                    final HtsgetRequest req = new HtsgetRequest(HtsgetBAMFileReader.this.mSource).withInterval(i);
                    return new HtsgetBAMFileIterator(req);
                }))
                .iterator();
            this.advanceIterator();
            this.advance();
        }

        @Override
        public void close() {
            // All previous iterators were already closed when they were consumed and iterators after this one
            // have not been created yet, so only need to close current one
            if (this.currentIterator != null) {
                this.currentIterator.close();
            }
        }

        @Override
        public boolean hasNext() {
            return this.currentRecord != null;
        }

        @Override
        public SAMRecord next() {
            final SAMRecord result = this.currentRecord;
            this.advance();
            return result;
        }

        private void advance() {
            while (this.currentIterator != null && !this.currentIterator.hasNext()) {
                this.advanceIterator();
            }
            this.currentRecord = this.currentIterator == null ? null : this.currentIterator.next();
        }

        private void advanceIterator() {
            if (this.currentIterator != null) {
                this.currentIterator.close();
            }
            if (!this.iterators.hasNext()) {
                this.currentIterator = null;
                return;
            }
            final Interval currInterval = this.intervals[this.currentIntervalIndex];
            final Interval prevInterval = this.currentIntervalIndex == 0 ? null : this.intervals[this.currentIntervalIndex - 1];
            final SamRecordFilter filter = new SamRecordFilter() {
                @Override
                public boolean filterOut(final SAMRecord record) {
                    return record.getReadUnmappedFlag() && record.getAlignmentStart() != SAMRecord.NO_ALIGNMENT_START
                        ? !this.acceptUnmappedRecord(record)
                        : !this.acceptRecord(record);
                }

                @Override
                public boolean filterOut(final SAMRecord first, final SAMRecord second) {
                    throw new UnsupportedOperationException();
                }

                private boolean acceptRecord(final SAMRecord rec) {
                    return BAMQueryChainingIterator.this.contained
                        ? currInterval.contains(rec) && (prevInterval == null || !prevInterval.contains(rec))
                        : currInterval.overlaps(rec) && (prevInterval == null || !prevInterval.overlaps(rec));
                }

                private boolean acceptUnmappedRecord(final SAMRecord rec) {
                    final int start = rec.getStart();
                    final boolean matchesCurrInterval =
                        rec.contigsMatch(currInterval) &&
                            CoordMath.encloses(currInterval.getStart(), currInterval.getEnd(), start, start);
                    final boolean matchesPrevInterval =
                        prevInterval != null &&
                            rec.contigsMatch(prevInterval) &&
                            CoordMath.encloses(prevInterval.getStart(), prevInterval.getEnd(), start, start);
                    return matchesCurrInterval && !matchesPrevInterval;
                }
            };
            this.currentIterator = new FilteringSamIterator(this.iterators.next().get(), filter, false);
            this.currentIntervalIndex++;
        }
    }

    public static class BAMQueryFilteringIterator implements CloseableIterator<SAMRecord> {
        /**
         * The wrapped iterator.
         */
        private final CloseableIterator<SAMRecord> wrappedIterator;
        /**
         * The next record to be returned.  Will be null if no such record exists.
         */
        private SAMRecord mNextRecord;
        private final BAMIteratorFilter iteratorFilter;

        public BAMQueryFilteringIterator(final CloseableIterator<SAMRecord> iterator, final BAMIteratorFilter iteratorFilter) {
            this.wrappedIterator = iterator;
            this.iteratorFilter = iteratorFilter;
            this.mNextRecord = this.advance();
        }

        /**
         * Returns true if a next element exists; false otherwise.
         */
        @Override
        public boolean hasNext() {
            return this.mNextRecord != null;
        }

        /**
         * Gets the next record from the given iterator.
         *
         * @return The next SAM record in the iterator.
         */
        @Override
        public SAMRecord next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException("BAMQueryFilteringIterator: no next element available");
            }
            final SAMRecord currentRead = this.mNextRecord;
            this.mNextRecord = this.advance();
            return currentRead;
        }

        SAMRecord advance() {
            while (true) {
                // Pull next record from stream
                if (!this.wrappedIterator.hasNext()) {
                    return null;
                }

                final SAMRecord record = this.wrappedIterator.next();
                switch (this.iteratorFilter.compareToFilter(record)) {
                    case MATCHES_FILTER:
                        return record;
                    case STOP_ITERATION:
                        return null;
                    case CONTINUE_ITERATION:
                        break; // keep looping
                    default:
                        throw new SAMException("Unexpected return from compareToFilter");
                }
            }
        }

        @Override
        public void close() {
            this.wrappedIterator.close();
        }
    }
}
