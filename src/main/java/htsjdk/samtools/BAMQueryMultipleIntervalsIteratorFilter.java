package htsjdk.samtools;

import htsjdk.samtools.util.CoordMath;

/**
 * Filters out records that do not match any of the given intervals and query type.
 */
public class BAMQueryMultipleIntervalsIteratorFilter implements BAMIteratorFilter {
    final QueryInterval[] intervals;
    final boolean contained;
    int intervalIndex = 0;


    public BAMQueryMultipleIntervalsIteratorFilter(final QueryInterval[] intervals,
                                                   final boolean contained) {
        this.contained = contained;
        this.intervals = intervals;
    }

    @Override
    public FilteringIteratorState compareToFilter(final SAMRecord record) {
        while (intervalIndex < intervals.length) {
            final IntervalComparison comparison = compareIntervalToRecord(intervals[intervalIndex], record);
            switch (comparison) {
                // Interval is before SAMRecord.  Try next interval;
                case BEFORE: ++intervalIndex; break;
                // Interval is after SAMRecord.  Keep scanning forward in SAMRecords
                case AFTER: return FilteringIteratorState.CONTINUE_ITERATION;
                // Found a good record
                case CONTAINED: return FilteringIteratorState.MATCHES_FILTER;
                // Either found a good record, or else keep scanning SAMRecords
                case OVERLAPPING: return
                        (contained ? FilteringIteratorState.CONTINUE_ITERATION : FilteringIteratorState.MATCHES_FILTER);
            }
        }
        // Went past the last interval
        return FilteringIteratorState.STOP_ITERATION;
    }

    public static IntervalComparison compareIntervalToRecord(final QueryInterval interval, final SAMRecord record) {
        // interval.end <= 0 implies the end of the reference sequence.
        final int intervalEnd = (interval.end <= 0? Integer.MAX_VALUE: interval.end);
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
