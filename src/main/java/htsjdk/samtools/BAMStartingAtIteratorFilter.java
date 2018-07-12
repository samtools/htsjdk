package htsjdk.samtools;

/**
 * A decorating iterator that filters out records that do not match the given reference and start position.
 */
public class BAMStartingAtIteratorFilter implements BAMIteratorFilter {

    private final int mReferenceIndex;
    private final int mRegionStart;

    public BAMStartingAtIteratorFilter(final int referenceIndex, final int start) {
        mReferenceIndex = referenceIndex;
        mRegionStart = start;
    }

    /**
     *
     * @return MATCHES_FILTER if this record matches the filter;
     * CONTINUE_ITERATION if does not match filter but iteration should continue;
     * STOP_ITERATION if does not match filter and iteration should end.
     */
    @Override
    public FilteringIteratorState compareToFilter(final SAMRecord record) {
        // If beyond the end of this reference sequence, end iteration
        final int referenceIndex = record.getReferenceIndex();
        if (referenceIndex < 0 || referenceIndex > mReferenceIndex) {
            return FilteringIteratorState.STOP_ITERATION;
        } else if (referenceIndex < mReferenceIndex) {
            // If before this reference sequence, continue
            return FilteringIteratorState.CONTINUE_ITERATION;
        }
        final int alignmentStart = record.getAlignmentStart();
        if (alignmentStart > mRegionStart) {
            // If scanned beyond target region, end iteration
            return FilteringIteratorState.STOP_ITERATION;
        } else  if (alignmentStart == mRegionStart) {
            return FilteringIteratorState.MATCHES_FILTER;
        } else {
            return FilteringIteratorState.CONTINUE_ITERATION;
        }
    }

}
