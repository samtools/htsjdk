package htsjdk.samtools;

import htsjdk.samtools.util.CloseableIterator;

/**
 * Decorator for a {@link PrimitiveSamReader} that expands its functionality into a {@link SamReader},
 * given the backing {@link SamInputResource}.
 *
 * Wraps the {@link Indexing} interface as well, which was originally separate from {@link SamReader} but in practice
 * the two are always implemented by the same class.
 *
 */
public class PrimitiveSamReaderToSamReaderAdapter implements SamReader, SamReader.Indexing {
    final PrimitiveSamReader p;
    final SamInputResource resource;

    public PrimitiveSamReaderToSamReaderAdapter(final PrimitiveSamReader p, final SamInputResource resource) {
        this.p = p;
        this.resource = resource;
    }

    /**
     * Access the underlying {@link PrimitiveSamReader} used by this adapter.
     * @return the {@link PrimitiveSamReader} used by this adapter.
     */
    public PrimitiveSamReader underlyingReader() {
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
    public boolean isQueryable() {
        return p.isQueryable();
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
