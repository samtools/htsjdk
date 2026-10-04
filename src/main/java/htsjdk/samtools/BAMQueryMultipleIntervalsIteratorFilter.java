package htsjdk.samtools;

import htsjdk.samtools.util.CoordMath;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Filters out records that do not match any of the given intervals and query type.
 *
 * <p>Records are compared with the intervals of their own reference only, so a file whose references are not in
 * dictionary order (as samtools cat of per-contig files writes) is filtered correctly, as htslib's multi-region
 * iterator filters it. Within a reference, records must be in coordinate order.
 */
public class BAMQueryMultipleIntervalsIteratorFilter implements BAMIteratorFilter {
    final QueryInterval[] intervals;
    final boolean contained;
    // The intervals' distinct references, ascending, and for each the index in intervals of its first interval not
    // yet passed by that reference's records, or -1 once they have passed them all.
    private final int[] references;
    private final int[] nextInterval;
    private int referencesWithIntervalsAhead;
    // records come in runs of one reference, so the last record's slot is tried before searching
    private int lastSlot = 0;

    /**
     * @param intervals the intervals to match, sorted by reference and then start, as
     *     {@link QueryInterval#optimizeIntervals} sorts them
     * @param contained whether a record must lie within an interval rather than overlap it
     */
    public BAMQueryMultipleIntervalsIteratorFilter(final QueryInterval[] intervals, final boolean contained) {
        this.contained = contained;
        this.intervals = intervals;
        final int[] firstIntervals = IntStream.range(0, intervals.length)
                .filter(i -> i == 0 || intervals[i].referenceIndex != intervals[i - 1].referenceIndex)
                .toArray();
        this.references = Arrays.stream(firstIntervals)
                .map(i -> intervals[i].referenceIndex)
                .toArray();
        this.nextInterval = firstIntervals;
        this.referencesWithIntervalsAhead = references.length;
    }

    @Override
    public FilteringIteratorState compareToFilter(final SAMRecord record) {
        final int reference = record.getReferenceIndex();
        final int slot = slotOf(reference);
        if (slot >= 0 && nextInterval[slot] >= 0) {
            for (int i = nextInterval[slot]; i < intervals.length && intervals[i].referenceIndex == reference; i++) {
                final IntervalComparison comparison = compareIntervalToRecord(intervals[i], record);
                // an interval before the record is behind every later record of this reference too
                if (comparison == IntervalComparison.BEFORE) continue;

                nextInterval[slot] = i;
                if (comparison == IntervalComparison.CONTAINED
                        || (comparison == IntervalComparison.OVERLAPPING && !contained)) {
                    return FilteringIteratorState.MATCHES_FILTER;
                }
                return FilteringIteratorState.CONTINUE_ITERATION;
            }
            // the record is past this reference's last interval
            nextInterval[slot] = -1;
            referencesWithIntervalsAhead--;
        }
        return referencesWithIntervalsAhead == 0
                ? FilteringIteratorState.STOP_ITERATION
                : FilteringIteratorState.CONTINUE_ITERATION;
    }

    /** The slot of {@code reference} in {@link #references}, or a negative number if it has no intervals. */
    private int slotOf(final int reference) {
        if (references.length > 0 && references[lastSlot] == reference) {
            return lastSlot;
        }
        final int slot = Arrays.binarySearch(references, reference);
        if (slot >= 0) {
            lastSlot = slot;
        }
        return slot;
    }

    public static IntervalComparison compareIntervalToRecord(final QueryInterval interval, final SAMRecord record) {
        // interval.end <= 0 implies the end of the reference sequence.
        final int intervalEnd = (interval.end <= 0 ? Integer.MAX_VALUE : interval.end);
        final int alignmentEnd;
        if (record.getReadUnmappedFlag() && record.getAlignmentStart() != SAMRecord.NO_ALIGNMENT_START) {
            // Unmapped read with coordinate of mate.
            alignmentEnd = record.getAlignmentStart();
        } else {
            alignmentEnd = record.getAlignmentEnd();
        }

        if (interval.referenceIndex < record.getReferenceIndex()) return IntervalComparison.BEFORE;
        else if (interval.referenceIndex > record.getReferenceIndex()) return IntervalComparison.AFTER;
        else if (intervalEnd < record.getAlignmentStart()) return IntervalComparison.BEFORE;
        else if (alignmentEnd < interval.start) return IntervalComparison.AFTER;
        else if (CoordMath.encloses(interval.start, intervalEnd, record.getAlignmentStart(), alignmentEnd)) {
            return IntervalComparison.CONTAINED;
        } else return IntervalComparison.OVERLAPPING;
    }
}
