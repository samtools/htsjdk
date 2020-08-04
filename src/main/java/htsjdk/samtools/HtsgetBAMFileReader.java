package htsjdk.samtools;

import htsjdk.samtools.filter.FilteringSamIterator;
import htsjdk.samtools.filter.SamRecordFilter;
import htsjdk.samtools.util.*;
import htsjdk.samtools.util.htsget.*;
import htsjdk.samtools.util.zip.InflaterFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class for reading and querying BAM files from an htsget source
 */
public class HtsgetBAMFileReader extends SamReader.ReaderImplementation {
    public static final String HTSGET_SCHEME = "htsget";

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

    // Only used for attaching as a source to SAMRecords so ok to share between iterators
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
    void enableIndexCaching(final boolean enabled) {
        throw new UnsupportedOperationException("Cannot enable index caching in HtsgetBAMFileReader");
    }

    @Override
    void enableIndexMemoryMapping(final boolean enabled) {
        throw new UnsupportedOperationException("Cannot enable index memory mapping in HtsgetBAMFileReader");
    }

    @Override
    public SamReader.Type type() {
        return SamReader.Type.BAM_HTSGET_TYPE;
    }

    /**
     * Note that this source is queryable by interval, but does NOT have an actual index
     *
     * @return true, but calls to {@link #getIndex} will return null
     */
    @Override
    public boolean hasIndex() {
        return true;
    }

    /**
     * Note that this method never returns an index despite {@link #hasIndex} being true
     *
     * @return null
     */
    @Override
    public BAMIndex getIndex() {
        return null;
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
        final HtsgetRequest req = new HtsgetRequest(this.mSource).withFormat(HtsgetFormat.BAM);
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
        final List<Locatable> namedIntervals = Arrays.stream(intervals)
            .map(i -> new Interval(this.mFileHeader.getSequence(i.referenceIndex).getSequenceName(), i.start, i.end))
            .collect(Collectors.toList());
        return this.query(namedIntervals, contained);
    }

    public CloseableIterator<SAMRecord> query(final String sequence, final int start, final int end, final boolean contained) {
        return this.query(
            Collections.singletonList(new Interval(sequence, start, end == -1 ? Integer.MAX_VALUE : end)),
            contained);
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
    public CloseableIterator<SAMRecord> query(final List<Locatable> intervals, final boolean contained) {
        final CloseableIterator<SAMRecord> chainingIterator = new BAMQueryChainingIterator(intervals, contained);
        final CloseableIterator<SAMRecord> queryIterator = this.mUseAsynchronousIO
            ? chainingIterator
            : new SAMRecordPrefetchingIterator(chainingIterator, 5_000_000);
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
            return new EmptyBamIterator();
        } else {
            final HtsgetRequest req = new HtsgetRequest(this.mSource)
                .withFormat(HtsgetFormat.BAM)
                .withInterval(new Interval(sequence, start, Integer.MAX_VALUE));
            final CloseableIterator<SAMRecord> iterator = new HtsgetBAMFileIterator(req);
            final BAMStartingAtIteratorFilter filter = new BAMStartingAtIteratorFilter(referenceIndex, start);
            final CloseableIterator<SAMRecord> filteringIterator = new BAMQueryFilteringIterator(iterator, filter);
            final CloseableIterator<SAMRecord> queryIterator = this.mUseAsynchronousIO
                ? filteringIterator
                : new SAMRecordPrefetchingIterator(filteringIterator, 5_000_000);
            this.iterators.add(queryIterator);
            return queryIterator;
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
        final HtsgetRequest req = new HtsgetRequest(this.mSource)
            .withFormat(HtsgetFormat.BAM)
            .withInterval(HtsgetRequest.UNMAPPED_UNPLACED_INTERVAL);
        final CloseableIterator<SAMRecord> unmappedIterator = new HtsgetBAMFileIterator(req);
        final CloseableIterator<SAMRecord> queryIterator = this.mUseAsynchronousIO
            ? unmappedIterator
            : new SAMRecordPrefetchingIterator(unmappedIterator, 5_000_000);
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
        final HtsgetResponse resp = req.getResponse();

        if (resp.getFormat() != HtsgetFormat.BAM) {
            throw new IllegalStateException("Expected format of response to be BAM but received + " + resp.getFormat());
        }

        final InputStream stream = resp.getDataStream();
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

    public static URI convertHtsgetUriToHttps(final URI uri) throws URISyntaxException {
        return new URI("https", uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
    }

    public static URI convertHtsgetUriToHttp(final URI uri) throws URISyntaxException {
        return new URI("http", uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
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
            final SAMRecord next = this.bamRecordCodec.decode();

            if (HtsgetBAMFileReader.this.mReader != null && next != null) {
                next.setFileSource(new SAMFileSource(
                    HtsgetBAMFileReader.this.mReader,
                    null));
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
        private final List<Locatable> intervals;
        private final boolean contained;
        private final Iterator<Lazy<HtsgetBAMFileIterator>> iterators;

        private CloseableIterator<SAMRecord> currentIterator;
        private SAMRecord currentRecord;
        private int currentIntervalIndex = 0;

        public BAMQueryChainingIterator(final List<Locatable> intervals, final boolean contained) {
            this.intervals = intervals;
            this.contained = contained;
            this.iterators = intervals.stream()
                .map(i -> new Lazy<>(() -> {
                    final HtsgetRequest req = new HtsgetRequest(HtsgetBAMFileReader.this.mSource)
                        .withFormat(HtsgetFormat.BAM)
                        .withInterval(i);
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
            final Locatable currInterval = this.intervals.get(this.currentIntervalIndex);
            final Locatable prevInterval = this.currentIntervalIndex == 0 ? null : this.intervals.get(this.currentIntervalIndex - 1);
            this.currentIterator = new FilteringSamIterator(
                this.iterators.next().get(),
                new ConsecutiveDuplicateRecordFilter(currInterval, prevInterval, contained));
            this.currentIntervalIndex++;
        }
    }

    /**
     * Filters out records which are duplicated across consecutive intervals
     * <p>
     * This is necessary as the htsget protocol makes no guarantee that all reads returned by an interval query will
     * be contained by or even overlap the given interval, meaning the result of a query for an earlier interval
     * may contain reads logically "belonging" to a later interval, which will be duplicated in that later interval
     */
    // TODO: remove this class once htsget POST api is implemented
    private static class ConsecutiveDuplicateRecordFilter implements SamRecordFilter {
        private final Locatable prevInterval;
        private final Locatable currInterval;
        private final boolean contained;

        public ConsecutiveDuplicateRecordFilter(final Locatable currInterval, final Locatable prevInterval, final boolean contained) {
            this.currInterval = currInterval;
            this.prevInterval = prevInterval;
            this.contained = contained;
        }

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
            return this.contained
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
    }

    private static class BAMQueryFilteringIterator implements CloseableIterator<SAMRecord> {
        /**
         * The wrapped iterator.
         */
        private final CloseableIterator<SAMRecord> wrappedIterator;
        /**
         * The next record to be returned.  Will be null if no such record exists.
         */
        private SAMRecord nextRecord;
        private final BAMIteratorFilter iteratorFilter;

        public BAMQueryFilteringIterator(final CloseableIterator<SAMRecord> iterator, final BAMIteratorFilter iteratorFilter) {
            this.wrappedIterator = iterator;
            this.iteratorFilter = iteratorFilter;
            this.nextRecord = this.advance();
        }

        /**
         * Returns true if a next element exists; false otherwise.
         */
        @Override
        public boolean hasNext() {
            return this.nextRecord != null;
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
            final SAMRecord currentRead = this.nextRecord;
            this.nextRecord = this.advance();
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

    private static class EmptyBamIterator implements CloseableIterator<SAMRecord> {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public SAMRecord next() {
            throw new NoSuchElementException("next called on empty iterator");
        }

        @Override
        public void close() {

        }
    }
}
