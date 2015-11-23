package htsjdk.samtools;

import htsjdk.samtools.util.CloseableIterator;

import java.io.Closeable;
import java.text.MessageFormat;

/**
 * Describes functionality for objects that produce {@link SAMRecord}s and associated information.
 *
 * Currently, only deprecated readers implement this directly; actual readers implement this
 * via {@link ReaderImplementation} and {@link PrimitiveSamReader}, which {@link SamReaderFactory}
 * converts into full readers by using {@link PrimitiveSamReaderToSamReaderAdapter}.
 *
 * @author mccowan
 */
public interface SamReader extends Iterable<SAMRecord>, Closeable {

    /** Describes a type of SAM file. */
    public abstract class Type {
        /** A string representation of this type. */
        abstract String name();

        /** The recommended file extension for SAMs of this type, without a period. */
        public abstract String fileExtension();

        /** The recommended file extension for SAM indexes of this type, without a period, or null if this type is not associated with indexes. */
        abstract String indexExtension();

        static class TypeImpl extends Type {
            final String name, fileExtension, indexExtension;

            TypeImpl(final String name, final String fileExtension, final String indexExtension) {
                this.name = name;
                this.fileExtension = fileExtension;
                this.indexExtension = indexExtension;
            }

            @Override
            String name() {
                return name;
            }

            @Override
            public String fileExtension() {
                return fileExtension;
            }

            @Override
            String indexExtension() {
                return indexExtension;
            }

            @Override
            public String toString() {
                return String.format("TypeImpl{name='%s', fileExtension='%s', indexExtension='%s'}", name, fileExtension, indexExtension);
            }
        }

        public static Type SRA_TYPE = new TypeImpl("SRA", "sra", null);
        public static Type CRAM_TYPE = new TypeImpl("CRAM", "cram", "crai");
        public static Type BAM_TYPE = new TypeImpl("BAM", "bam", "bai");
        public static Type SAM_TYPE = new TypeImpl("SAM", "sam", null);
    }

    /**
     * Facet for index-related operations.
     */
    public interface Indexing {
        /**
         * Retrieves the index for the given file type.  Ensure that the index is of the specified type.
         *
         * @return An index of the given type.
         */
        public BAMIndex getIndex();

        /**
         * Returns true if the supported index is browseable, meaning the bins in it can be traversed
         * and chunk data inspected and retrieved.
         *
         * @return True if the index supports the BrowseableBAMIndex interface.  False otherwise.
         */
        public boolean hasBrowseableIndex();

        /**
         * Gets an index tagged with the BrowseableBAMIndex interface.  Throws an exception if no such
         * index is available.
         *
         * @return An index with a browseable interface, if possible.
         * @throws SAMException if no such index is available.
         */
        public BrowseableBAMIndex getBrowseableIndex();

        /**
         * Iterate through the given chunks in the file.
         *
         * @param chunks List of chunks for which to retrieve data.
         * @return An iterator over the given chunks.
         */
        public SAMRecordIterator iterator(final SAMFileSpan chunks);

        /**
         * Gets a pointer spanning all reads in the BAM file.
         *
         * @return Unbounded pointer to the first record, in chunk format.
         */
        public SAMFileSpan getFilePointerSpanningReads();

    }

    public SAMFileHeader getFileHeader();

    /**
     * @return the {@link htsjdk.samtools.SamReader.Type} of this {@link htsjdk.samtools.SamReader}
     */
    public Type type();

    /**
     * @return a human readable description of the resource backing this sam reader
     */
    public String getResourceDescription();

    /**
     * @return true if ths is a BAM file, and has an index
     */
    public boolean hasIndex();

    /**
     * Exposes the {@link SamReader.Indexing} facet of this {@link SamReader}.
     *
     * @throws java.lang.UnsupportedOperationException If {@link #hasIndex()} returns false.
     */
    public Indexing indexing();

    /**
     * Iterate through file in order.  For a SAMFileReader constructed from an InputStream, and for any SAM file,
     * a 2nd iteration starts where the 1st one left off.  For a BAM constructed from a SeekableStream or File, each new iteration
     * starts at the first record.
     * <p/>
     * Only a single open iterator on a SAM or BAM file may be extant at any one time.  If you want to start
     * a second iteration, the first one must be closed first.
     */
    public SAMRecordIterator iterator();

    /**
     * Iterate over records that match the given interval.  Only valid to call this if hasIndex() == true.
     * <p/>
     * Only a single open iterator on a given SAMFileReader may be extant at any one time.  If you want to start
     * a second iteration, the first one must be closed first.  You can use a second SAMFileReader to iterate
     * in parallel over the same underlying file.
     * <p/>
     * Note that indexed lookup is not perfectly efficient in terms of disk I/O.  I.e. some SAMRecords may be read
     * and then discarded because they do not match the interval of interest.
     * <p/>
     * Note that an unmapped read will be returned by this call if it has a coordinate for the purpose of sorting that
     * is in the query region.
     *
     * @param sequence  Reference sequence of interest.
     * @param start     1-based, inclusive start of interval of interest. Zero implies start of the reference sequence.
     * @param end       1-based, inclusive end of interval of interest. Zero implies end of the reference sequence.
     * @param contained If true, each SAMRecord returned will have its alignment completely contained in the
     *                  interval of interest.  If false, the alignment of the returned SAMRecords need only overlap the interval of interest.
     * @return Iterator over the SAMRecords matching the interval.
     */
    public SAMRecordIterator query(final String sequence, final int start, final int end, final boolean contained);

    /**
     * Iterate over records that overlap the given interval.  Only valid to call this if hasIndex() == true.
     * <p/>
     * Only a single open iterator on a given SAMFileReader may be extant at any one time.  If you want to start
     * a second iteration, the first one must be closed first.
     * <p/>
     * Note that indexed lookup is not perfectly efficient in terms of disk I/O.  I.e. some SAMRecords may be read
     * and then discarded because they do not match the interval of interest.
     * <p/>
     * Note that an unmapped read will be returned by this call if it has a coordinate for the purpose of sorting that
     * is in the query region.
     *
     * @param sequence Reference sequence of interest.
     * @param start    1-based, inclusive start of interval of interest. Zero implies start of the reference sequence.
     * @param end      1-based, inclusive end of interval of interest. Zero implies end of the reference sequence.
     * @return Iterator over the SAMRecords overlapping the interval.
     */
    public SAMRecordIterator queryOverlapping(final String sequence, final int start, final int end);

    /**
     * Iterate over records that are contained in the given interval.  Only valid to call this if hasIndex() == true.
     * <p/>
     * Only a single open iterator on a given SAMFileReader may be extant at any one time.  If you want to start
     * a second iteration, the first one must be closed first.
     * <p/>
     * Note that indexed lookup is not perfectly efficient in terms of disk I/O.  I.e. some SAMRecords may be read
     * and then discarded because they do not match the interval of interest.
     * <p/>
     * Note that an unmapped read will be returned by this call if it has a coordinate for the purpose of sorting that
     * is in the query region.
     *
     * @param sequence Reference sequence of interest.
     * @param start    1-based, inclusive start of interval of interest. Zero implies start of the reference sequence.
     * @param end      1-based, inclusive end of interval of interest. Zero implies end of the reference sequence.
     * @return Iterator over the SAMRecords contained in the interval.
     */
    public SAMRecordIterator queryContained(final String sequence, final int start, final int end);

    /**
     * Iterate over records that match one of the given intervals.  This may be more efficient than querying
     * each interval separately, because multiple reads of the same SAMRecords is avoided.
     * <p/>
     * Only valid to call this if hasIndex() == true.
     * <p/>
     * Only a single open iterator on a given SAMFileReader may be extant at any one time.  If you want to start
     * a second iteration, the first one must be closed first.  You can use a second SAMFileReader to iterate
     * in parallel over the same underlying file.
     * <p/>
     * Note that indexed lookup is not perfectly efficient in terms of disk I/O.  I.e. some SAMRecords may be read
     * and then discarded because they do not match an interval of interest.
     * <p/>
     * Note that an unmapped read will be returned by this call if it has a coordinate for the purpose of sorting that
     * is in the query region.
     *
     * @param intervals Intervals to be queried.  The intervals must be optimized, i.e. in order, with overlapping
     *                  and abutting intervals merged.  This can be done with {@link htsjdk.samtools.QueryInterval#optimizeIntervals}
     * @param contained If true, each SAMRecord returned is will have its alignment completely contained in one of the
     *                  intervals of interest.  If false, the alignment of the returned SAMRecords need only overlap one of
     *                  the intervals of interest.
     * @return Iterator over the SAMRecords matching the interval.
     */
    public SAMRecordIterator query(final QueryInterval[] intervals, final boolean contained);

    /**
     * Iterate over records that overlap any of the given intervals.  This may be more efficient than querying
     * each interval separately, because multiple reads of the same SAMRecords is avoided.
     * <p/>
     * Only valid to call this if hasIndex() == true.
     * <p/>
     * Only a single open iterator on a given SAMFileReader may be extant at any one time.  If you want to start
     * a second iteration, the first one must be closed first.
     * <p/>
     * Note that indexed lookup is not perfectly efficient in terms of disk I/O.  I.e. some SAMRecords may be read
     * and then discarded because they do not match the interval of interest.
     * <p/>
     * Note that an unmapped read will be returned by this call if it has a coordinate for the purpose of sorting that
     * is in the query region.
     *
     * @param intervals Intervals to be queried.  The intervals must be optimized, i.e. in order, with overlapping
     *                  and abutting intervals merged.  This can be done with {@link htsjdk.samtools.QueryInterval#optimizeIntervals}
     */
    public SAMRecordIterator queryOverlapping(final QueryInterval[] intervals);

    /**
     * Iterate over records that are contained in the given interval.  This may be more efficient than querying
     * each interval separately, because multiple reads of the same SAMRecords is avoided.
     * <p/>
     * Only valid to call this if hasIndex() == true.
     * <p/>
     * Only a single open iterator on a given SAMFileReader may be extant at any one time.  If you want to start
     * a second iteration, the first one must be closed first.
     * <p/>
     * Note that indexed lookup is not perfectly efficient in terms of disk I/O.  I.e. some SAMRecords may be read
     * and then discarded because they do not match the interval of interest.
     * <p/>
     * Note that an unmapped read will be returned by this call if it has a coordinate for the purpose of sorting that
     * is in the query region.
     *
     * @param intervals Intervals to be queried.  The intervals must be optimized, i.e. in order, with overlapping
     *                  and abutting intervals merged.  This can be done with {@link htsjdk.samtools.QueryInterval#optimizeIntervals}
     * @return Iterator over the SAMRecords contained in any of the intervals.
     */
    public SAMRecordIterator queryContained(final QueryInterval[] intervals);


    public SAMRecordIterator queryUnmapped();

    /**
     * Iterate over records that map to the given sequence and start at the given position.  Only valid to call this if hasIndex() == true.
     * <p/>
     * Only a single open iterator on a given SAMFileReader may be extant at any one time.  If you want to start
     * a second iteration, the first one must be closed first.
     * <p/>
     * Note that indexed lookup is not perfectly efficient in terms of disk I/O.  I.e. some SAMRecords may be read
     * and then discarded because they do not match the interval of interest.
     * <p/>
     * Note that an unmapped read will be returned by this call if it has a coordinate for the purpose of sorting that
     * matches the arguments.
     *
     * @param sequence Reference sequence of interest.
     * @param start    Alignment start of interest.
     * @return Iterator over the SAMRecords with the given alignment start.
     */
    public SAMRecordIterator queryAlignmentStart(final String sequence, final int start);

    /**
     * Fetch the mate for the given read.  Only valid to call this if hasIndex() == true.
     * This will work whether the mate has a coordinate or not, so long as the given read has correct
     * mate information.  This method iterates over the SAM file, so there may not be an unclosed
     * iterator on the SAM file when this method is called.
     * <p/>
     * Note that it is not possible to call queryMate when iterating over the SAMFileReader, because queryMate
     * requires its own iteration, and there cannot be two simultaneous iterations on the same SAMFileReader.  The
     * work-around is to open a second SAMFileReader on the same input file, and call queryMate on the second
     * reader.
     *
     * @param rec Record for which mate is sought.  Must be a paired read.
     * @return rec's mate, or null if it cannot be found.
     */
    public SAMRecord queryMate(final SAMRecord rec);

    /**
     * The minimal subset of functionality needed for a {@link SAMRecord} data source.
     * {@link SamReader} itself is somewhat large and bulky, but the core functionality can be captured in
     * relatively few methods, which are included here. For documentation, see the corresponding methods
     * in {@link SamReader}.
     *
     * See also: {@link PrimitiveSamReaderToSamReaderAdapter}, {@link ReaderImplementation}
     *
     */
    public interface PrimitiveSamReader {
        Type type();

        boolean hasIndex();

        BAMIndex getIndex();

        SAMFileHeader getFileHeader();

        CloseableIterator<SAMRecord> getIterator();

        CloseableIterator<SAMRecord> getIterator(SAMFileSpan fileSpan);

        SAMFileSpan getFilePointerSpanningReads();

        CloseableIterator<SAMRecord> query(QueryInterval[] intervals, boolean contained);

        CloseableIterator<SAMRecord> queryAlignmentStart(String sequence, int start);

        CloseableIterator<SAMRecord> queryUnmapped();

        void close();

        ValidationStringency getValidationStringency();
    }

    /**
     * Decorator for a {@link SamReader.PrimitiveSamReader} that expands its functionality into a {@link SamReader},
     * given the backing {@link SamInputResource}.
     *
     * Wraps the {@link Indexing} interface as well, which was originally separate from {@link SamReader} but in practice
     * the two are always implemented by the same class.
     *
     */
    class PrimitiveSamReaderToSamReaderAdapter implements SamReader, Indexing {
        final PrimitiveSamReader p;
        final SamInputResource resource;

        public PrimitiveSamReaderToSamReaderAdapter(final PrimitiveSamReader p, final SamInputResource resource) {
            this.p = p;
            this.resource = resource;
        }

        PrimitiveSamReader underlyingReader() {
            return p;
        }

        @Override
        public SAMRecordIterator queryOverlapping(final String sequence, final int start, final int end) {
            return query(sequence, start, end, false);
        }

        @Override
        public SAMRecordIterator queryOverlapping(final QueryInterval[] intervals) {
            return query(intervals, false);
        }

        @Override
        public SAMRecordIterator queryContained(final String sequence, final int start, final int end) {
            return query(sequence, start, end, true);
        }

        @Override
        public SAMRecordIterator queryContained(final QueryInterval[] intervals) {
            return query(intervals, true);
        }

        /**
         * Wraps the boilerplate code for querying a record's mate, which is common across many implementations.
         *
         * @param rec Record for which mate is sought.  Must be a paired read.
         * @return
         */
        @Override
        public SAMRecord queryMate(final SAMRecord rec) {
            if (!rec.getReadPairedFlag()) {
                throw new IllegalArgumentException("queryMate called for unpaired read.");
            }
            if (rec.getFirstOfPairFlag() == rec.getSecondOfPairFlag()) {
                throw new IllegalArgumentException("SAMRecord must be either first and second of pair, but not both.");
            }
            final boolean firstOfPair = rec.getFirstOfPairFlag();
            final CloseableIterator<SAMRecord> it;
            if (rec.getMateReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                it = queryUnmapped();
            } else {
                it = queryAlignmentStart(rec.getMateReferenceName(), rec.getMateAlignmentStart());
            }
            try {
                SAMRecord mateRec = null;
                while (it.hasNext()) {
                    final SAMRecord next = it.next();
                    if (!next.getReadPairedFlag()) {
                        if (rec.getReadName().equals(next.getReadName())) {
                            throw new SAMFormatException("Paired and unpaired reads with same name: " + rec.getReadName());
                        }
                        continue;
                    }
                    if (firstOfPair) {
                        if (next.getFirstOfPairFlag()) continue;
                    } else {
                        if (next.getSecondOfPairFlag()) continue;
                    }
                    if (rec.getReadName().equals(next.getReadName())) {
                        if (mateRec != null) {
                            throw new SAMFormatException("Multiple SAMRecord with read name " + rec.getReadName() +
                                    " for " + (firstOfPair ? "second" : "first") + " end.");
                        }
                        mateRec = next;
                    }
                }
                return mateRec;
            } finally {
                it.close();
            }
        }

        @Override
        public boolean hasBrowseableIndex() {
            return hasIndex() && getIndex() instanceof BrowseableBAMIndex;
        }

        @Override
        public BrowseableBAMIndex getBrowseableIndex() {
            final BAMIndex index = getIndex();
            if (!(index instanceof BrowseableBAMIndex))
                throw new SAMException("Cannot return index: index created by BAM is not browseable.");
            return BrowseableBAMIndex.class.cast(index);
        }

        @Override
        public SAMRecordIterator iterator() {
            return new AssertingIterator(p.getIterator());
        }

        @Override
        public SAMRecordIterator iterator(final SAMFileSpan chunks) {
            return new AssertingIterator(p.getIterator(chunks));
        }

        @Override
        public void close() {
            p.close();
        }

        @Override
        public SAMFileSpan getFilePointerSpanningReads() {
            return p.getFilePointerSpanningReads();
        }

        @Override
        public SAMFileHeader getFileHeader() {
            return p.getFileHeader();
        }

        @Override
        public Type type() {
            return p.type();
        }

        @Override
        public String getResourceDescription() {
            return this.resource.toString();
        }

        @Override
        public boolean hasIndex() {
            return p.hasIndex();
        }

        @Override
        public Indexing indexing() {
            return this;
        }

        @Override
        public BAMIndex getIndex() {
            return p.getIndex();
        }

        @Override
        public SAMRecordIterator query(final QueryInterval[] intervals, final boolean contained) {
            return AssertingIterator.of(p.query(intervals, contained));
        }

        @Override
        public SAMRecordIterator query(final String sequence, final int start, final int end, final boolean contained) {
            return query(new QueryInterval[]{new QueryInterval(getFileHeader().getSequenceIndex(sequence), start, end)}, contained);
        }

        @Override
        public SAMRecordIterator queryUnmapped() {
            return AssertingIterator.of(p.queryUnmapped());
        }

        @Override
        public SAMRecordIterator queryAlignmentStart(final String sequence, final int start) {
            return AssertingIterator.of(p.queryAlignmentStart(sequence, start));
        }

    }

    static class AssertingIterator implements SAMRecordIterator {

        static AssertingIterator of(final CloseableIterator<SAMRecord> iterator) {
            return new AssertingIterator(iterator);
        }

        private final CloseableIterator<SAMRecord> wrappedIterator;
        private SAMRecord previous = null;
        private SAMRecordComparator comparator = null;

        public AssertingIterator(final CloseableIterator<SAMRecord> iterator) {
            wrappedIterator = iterator;
        }

        public SAMRecordIterator assertSorted(final SAMFileHeader.SortOrder sortOrder) {

            if (sortOrder == null || sortOrder == SAMFileHeader.SortOrder.unsorted) {
                comparator = null;
                return this;
            }

            comparator = sortOrder.getComparatorInstance();
            return this;
        }

        public SAMRecord next() {
            final SAMRecord result = wrappedIterator.next();
            if (comparator != null) {
                if (previous != null) {
                    if (comparator.fileOrderCompare(previous, result) > 0) {
                        throw new IllegalStateException(MessageFormat.format(
                                "Records {0} ({1}:{2}) should come after {3} ({4}:{5}) when sorting with {6}",
                                previous.getReadName(),
                                previous.getReferenceName(),
                                previous.getAlignmentStart(),
                                result.getReadName(),
                                result.getReferenceName(),
                                result.getAlignmentStart(),
                                comparator.getClass().getName())
                        );
                    }
                }
                previous = result;
            }
            return result;
        }

        public void close() { wrappedIterator.close(); }

        public boolean hasNext() { return wrappedIterator.hasNext(); }

        public void remove() { wrappedIterator.remove(); }
    }

    /**
     * Internal interface for SAM/BAM/CRAM file reader implementations,
     * as distinct from non-file-based readers.
     *
     * Implemented as an abstract class to enforce better access control.
     *
     * TODO -- Many of these methods only apply for a subset of implementations,
     * TODO -- and either no-op or throw an exception for the others.
     * TODO -- We should consider refactoring things to avoid this;
     * TODO -- perhaps we can get away with not having this class at all.
     */
    abstract class ReaderImplementation implements PrimitiveSamReader {
        abstract void enableFileSource(final SamReader reader, final boolean enabled);

        abstract void enableIndexCaching(final boolean enabled);

        abstract void enableIndexMemoryMapping(final boolean enabled);

        abstract void enableCrcChecking(final boolean enabled);

        abstract void setSAMRecordFactory(final SAMRecordFactory factory);

        abstract void setValidationStringency(final ValidationStringency validationStringency);
    }
}
