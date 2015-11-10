/*
 * The MIT License
 *
 * Copyright (c) 2015 The Broad Institute
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

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMRecord;

/**
 * Filters out reads with very few unclipped bases, likely due to the read coming
 * from a foreign organism, e.g. bacterial contamination.
 *
 * Based on GATK's OverclippedReadFilter.
 */
public class OverclippedReadFilter implements SamRecordFilter {
    // if the number of unclipped bases is below this threshold, the read is considered overclipped
    private final int unclippedBasesThreshold;
    // if set to true, then reads with at least one clipped end will be filtered; if false, we require both ends to be clipped
    private final boolean filterSingleEndClips;

    public OverclippedReadFilter(final int unclippedBasesThreshold, final boolean filterSingleEndClips) {
        if (unclippedBasesThreshold < 0) throw new SAMException("unclippedBasesThreshold must be non-negative");
        this.unclippedBasesThreshold = unclippedBasesThreshold;
        this.filterSingleEndClips = filterSingleEndClips;
    }

    @Override
    public boolean filterOut(final SAMRecord record) {
        int alignedLength = 0;
        int softClipBlocks = 0;
        int minSoftClipBlocks = filterSingleEndClips ? 1 : 2;
        CigarOperator lastOperator = null;

        for ( final CigarElement element : record.getCigar().getCigarElements() ) {
            if ( element.getOperator() == CigarOperator.S ) {
                //Treat consecutive S blocks as a single one
                if(lastOperator != CigarOperator.S){
                    softClipBlocks += 1;
                }

            } else if ( element.getOperator().consumesReadBases() ) {   // M, I, X, and EQ (S was already accounted for above)
                alignedLength += element.getLength();
            }
            lastOperator = element.getOperator();
        }

        return(alignedLength < unclippedBasesThreshold && softClipBlocks >= minSoftClipBlocks);
    }

    @Override
    public boolean filterOut(final SAMRecord first, final SAMRecord second) {
        return filterOut(first) || filterOut(second);
    }
}
