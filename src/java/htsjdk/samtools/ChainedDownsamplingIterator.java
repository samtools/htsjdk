/*
 * The MIT License
 *
 * Copyright (c) 2015 Tim Fennell
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

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A DownsamplingIterator that combines the ConstantMemory and HighAccuracy downsampling techniques to provide an
 * iterator that has accuracy approaching that of HighAccuracy, but with more limited memory usage.  Instead of
 * requiring memory proportional to number of read names in the incoming stream of reads, requires memory
 * approximately proportional to the number of output reads.
 *
 * @author Tim Fennell
 */
class ChainedDownsamplingIterator extends HighAccuracyDownsamplingIterator {
    public static final int MIN_ACCURATE_INPUT_READS = 50000;

    /**
     * Constructs a chained iterator that will read from the provided iterator and attempt to downsampling to the provided proportion.
     */
    ChainedDownsamplingIterator(final Iterator<SAMRecord> iterator, final double proportion, final int seed) {
        super(new ConstantMemoryDownsamplingIterator(iterator, adjustProportion(proportion), seed), proportion, seed);

        // Deal with the fact that the iterator will advance and discard some reads at construction
        final long discarded = ((ConstantMemoryDownsamplingIterator) getUnderlyingIterator()).getDiscardedCount();
        recordDiscardRecords(discarded);
    }

    /**
     * Calculates the upper bound of 99.9% CI given the proportion, that is used to "buffer" the proportion on
     * the constant memory downsampler, to make sure it leaves enough reads for us to downsample.
     *
     * Uses an assumed number of reads tested as this is often not known until after the fact.
     */
    private static double adjustProportion(final double p) {
        final double ciAdjustment99_9 = 3.3 * Math.sqrt(p/MIN_ACCURATE_INPUT_READS);
        return Math.min(1, p + ciAdjustment99_9);
    }


    /**
     * Resets statistics before reading from the underlying iterator.
     */
    @Override
    protected void readFromUnderlyingIterator(final List<SAMRecord> recs, final Set<String> names, final int templatesToRead) {
        // Reset the stats on the underlying iterator
        ((ConstantMemoryDownsamplingIterator) getUnderlyingIterator()).resetStatistics();

        // Read from the underlying iterator
        super.readFromUnderlyingIterator(recs, names, templatesToRead);
    }

    @Override
    protected int calculateTemplatesToKeep(final int templatesRead, final double overallProportion) {
        // Calculate an adjusted proportion to keep, knowing what proportion the underlying iterator discarded
        final ConstantMemoryDownsamplingIterator iter = (ConstantMemoryDownsamplingIterator) getUnderlyingIterator();
        final double priorProportion = iter.getAcceptedFraction();
        final double p = Math.max(0, Math.min(1, overallProportion / priorProportion));
        final int retval =  super.calculateTemplatesToKeep(templatesRead, p);

        // Record all the discarded records to keep the overall statistics accurate, but do it after
        // the call to super() so it doesn't affect the proportion calculation.
        recordDiscardRecords(iter.getDiscardedCount());

        return retval;
    }
}
