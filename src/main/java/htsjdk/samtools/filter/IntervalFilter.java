/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
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

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalUtil;

import java.util.Iterator;
import java.util.List;

/**
 * Filter SAMRecords so that only those that overlap the given list of intervals.
 * It is required that the SAMRecords are passed in coordinate order, and have non-null SAMFileHeaders.
 *
 * $Id$
 *
 * @author alecw@broadinstitute.org
 */
public class IntervalFilter implements SamRecordFilter {
    private final Iterator<Interval> intervals;
    /**
     * Null only if there are no more intervals
     */
    private final SAMFileHeader samHeader;
    private Interval currentInterval;
    private int currentSequenceIndex;

    /**
     * Prepare to filter out SAMRecords that do not overlap the given list of intervals
     * @param intervals -- must be locus-ordered & non-overlapping
     */
    public IntervalFilter(final List<Interval> intervals, final SAMFileHeader samHeader) {
        this.samHeader = samHeader;
        IntervalUtil.assertOrderedNonOverlapping(intervals.iterator(), samHeader.getSequenceDictionary());
        this.intervals = intervals.iterator();
        advanceInterval();
    }

    /**
     * Determines whether a SAMRecord matches this filter
     *
     * @param record the SAMRecord to evaluate
     * @return true if the SAMRecord matches the filter, otherwise false
     */
    public boolean filterOut(final SAMRecord record) {
        while (currentInterval != null &&
                (currentSequenceIndex < record.getReferenceIndex() ||
                 (currentSequenceIndex == record.getReferenceIndex() && currentInterval.getEnd() < record.getAlignmentStart()))) {
            advanceInterval();
        }
        // Return true if record should be filtered out
        return !(currentInterval != null && currentSequenceIndex == record.getReferenceIndex() &&
                 currentInterval.getStart() <= record.getAlignmentEnd());
    }

    private void advanceInterval() {
        if (intervals.hasNext()) {
            currentInterval = intervals.next();
            currentSequenceIndex = samHeader.getSequenceIndex(currentInterval.getContig());
        } else {
            currentInterval = null;
        }
    }

    /**
     * Determines whether a pair of SAMRecord matches this filter
     *
     * @param first  the first SAMRecord to evaluate
     * @param second the second SAMRecord to evaluate
     *
     * @return true if the SAMRecords matches the filter, otherwise false
     */
    public boolean filterOut(final SAMRecord first, final SAMRecord second) {
        throw new UnsupportedOperationException("Paired IntervalFilter filter not implemented!");
    }
}
