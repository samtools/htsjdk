/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
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
package htsjdk.samtools.filter;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.OverlapDetector;

import java.util.Collection;
import java.util.List;

/**
 * Filter out SAMRecords where neither record of a pair overlaps a given set of
 * intervals. If one record of a pair overlaps the interval list, than both are
 * kept. It is required that the SAMRecords are passed in coordinate order, have
 * non-null SAMFileHeaders, and that Mate Cigar (MC) is present.
 *
 * @author kbergin@broadinstitute.org
 */
public class IntervalKeepPairFilter implements SamRecordFilter {
    private final OverlapDetector<Interval> intervalOverlapDetector;

    /**
     * Prepare to filter out SAMRecords that do not overlap the given list of
     * intervals
     * @param intervals
     */
    public IntervalKeepPairFilter(final List<Interval> intervals) {
        this.intervalOverlapDetector = new OverlapDetector<>(0, 0);
        this.intervalOverlapDetector.addAll(intervals, intervals);
    }

    /**
     * Determines whether a SAMRecord matches this filter. Takes record, finds
     * the location of its mate using the MC tag. Checks if either record
     * overlaps the current interval using overlap detector. If yes, return
     * false -> don't filter it out.
     *
     * If a read is secondary or supplementary, filter read out. Use
     * {@link IntervalFilter} if you want to keep these reads, but NOTE: the
     * resulting bam may not be valid.
     *
     * @param record the SAMRecord to evaluate
     * @return true if the SAMRecord matches the filter, otherwise false
     */
    public boolean filterOut(final SAMRecord record) {
        if (record.isSecondaryOrSupplementary()) {
           return true;
        }

        if (!record.getReadUnmappedFlag()
                && hasOverlaps(record.getReferenceName(), record.getStart(), record.getEnd())) {
            return false;
        }

        return record.getMateUnmappedFlag() || !hasOverlaps(record.getMateReferenceName(),
                record.getMateAlignmentStart(), SAMUtils.getMateAlignmentEnd(record));
    }

    /**
     * Returns true if the record overlaps any intervals in list, false otherwise.
     *
     * @param refSequence Reference contig name where record maps
     * @param start Record alignment start
     * @param end Record alignment end
     * @return true if SAMRecord overlaps any intervals in list
     */
    private boolean hasOverlaps(final String refSequence, final int start, final int end) {
        final Interval readInterval = new Interval(refSequence, start, end);
        final Collection<Interval> overlapsRead = intervalOverlapDetector.getOverlaps(readInterval);

        return !overlapsRead.isEmpty();
    }

    /**
     * Determines whether a pair of SAMRecord matches this filter
     *
     * @param first  the first SAMRecord to evaluate
     * @param second the second SAMRecord to evaluate
     *
     * @return true if both SAMRecords do not overlap the interval list
     */
    public boolean filterOut(final SAMRecord first, final SAMRecord second) {
        return filterOut(first) && filterOut(second);
    }
}
