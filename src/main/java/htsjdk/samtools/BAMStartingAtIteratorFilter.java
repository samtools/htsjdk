/*
 * The MIT License
 *
 * Copyright (c) 2019 The Broad Institute
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
