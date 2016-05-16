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

import htsjdk.samtools.util.IOUtil;

import java.io.File;
import java.util.Iterator;

/**
 * A factory for creating DownsamplingIterators that uses a number of different strategies to achieve downsampling while
 * meeting various criteria.
 *
 * @author Tim Fennell
 */
public class DownsamplingIteratorFactory {
    public static final String HIGH_ACCURACY_DESCRIPTION =
            "Attempts (but does not guarantee) to provide accuracy up to a specified limit. Accuracy is defined as emitting " +
            "a proportion of reads as close to the requested proportion as possible. In order to do so this strategy requires " +
            "memory that is proportional to the number of template names in the incoming stream of reads, and will thus require " +
            "large amounts of memory when running on large input files.";

    public static final String CONSTANT_MEMORY_DESCRPTION =
            "Downsamples a stream or file of SAMRecords using a hash-projection strategy such that it can run in constant memory. " +
            "The downsampling is stochastic, and therefore the actual retained proportion will vary around the requested proportion. Due " +
            "to working in fixed memory this strategy is good for large inputs, and due to the stochastic nature the accuracy of this strategy " +
            "is highest with a high number of output records, and diminishes at low output volumes.";

    public static final String CHAINED_DESCRIPTION =
            "Attempts to provide a compromise strategy that offers some of the advantages of both the ConstantMemory and HighAccuracy strategies. " +
            "Uses a ConstantMemory strategy to downsample the incoming stream to approximately the desired proportion, and then a HighAccuracy " +
            "strategy to finish. Works in a single pass, and will provide accuracy close to (but often not as good as) HighAccuracy while requiring " +
            "memory proportional to the set of reads emitted from the ConstantMemory strategy to the HighAccuracy strategy. Works well when downsampling " +
            "large inputs to small proportions (e.g. downsampling hundreds of millions of reads and retaining only 2%. Should be accurate 99.9% of the time " +
            "when the input contains >= 50,000 templates (read names). For smaller inputs, HighAccuracy is recommended instead.";

    /** Describes the available downsampling strategies. */
    public enum Strategy {
        HighAccuracy(HIGH_ACCURACY_DESCRIPTION),
        ConstantMemory(CONSTANT_MEMORY_DESCRPTION),
        Chained(CHAINED_DESCRIPTION);

        public final String description;

        Strategy(final String description) {
            this.description = description;
        }

        /** Gets the description of the strategy. */
        public String getDescription() {
            return description;
        }
    }

    /**
     * Creates a new DownsamplingIterator using the supplied Strategy that attempts to read from the provided iterator and return
     * approximately proportion of the records read.
     *
     * @param iterator The iterator from which to consume SAMRecords
     * @param strategy The downsampling strategy to use
     * @param proportion The proportion of records the downsampling strategy should attempt to emit
     * @param accuracy If supported by the downsampling strategy, the accuracy goal for the downsampler. Higher accuracy will generally
     *                 require higher memory usage.  An accuracy value of 0.0001 tells the strategy to try and ensure the emitted proportion
     *                 is within proportion +/0 0.0001.
     * @param seed The seed value to use for any random process used in down-sampling.
     */
    public static DownsamplingIterator make(final Iterator<SAMRecord> iterator, final Strategy strategy, final double proportion, final double accuracy, final int seed) {
        if (strategy == null) throw new IllegalArgumentException("strategy may not be null");
        if (iterator == null) throw new IllegalArgumentException("iterator may not be null");
        if (proportion < 0) throw new IllegalArgumentException("proportion must be greater than 0");
        if (proportion > 1) throw new IllegalArgumentException("proportion must be less than 1");

        switch (strategy) {
            case HighAccuracy:   return new HighAccuracyDownsamplingIterator(iterator, proportion, seed).setTargetAccuracy(accuracy);
            case ConstantMemory: return new ConstantMemoryDownsamplingIterator(iterator, proportion, seed);
            case Chained:        return new ChainedDownsamplingIterator(iterator, proportion, seed).setTargetAccuracy(accuracy);
            default: throw new IllegalStateException("Unexpected value for Strategy enum in switch statement. Bug!!");
        }
    }

    /**
     * Convenience method that constructs a downsampling iterator for all the reads in a SAM file.
     * See {@link DownsamplingIteratorFactory#make(Iterator, Strategy, double, double, int)} for detailed parameter information.
     */
    public static DownsamplingIterator make(final File samFile, final Strategy strategy, final double proportion, final double accuracy, final int seed) {
        IOUtil.assertFileIsReadable(samFile);
        return make(SamReaderFactory.makeDefault().open(samFile), strategy, proportion, accuracy, seed);
    }

    /**
     * Convenience method that constructs a downsampling iterator for all the reads available from a SamReader.
     * See {@link DownsamplingIteratorFactory#make(Iterator, Strategy, double, double, int)} for detailed parameter information.
     */
    public static DownsamplingIterator make(final SamReader reader, final Strategy strategy, final double proportion, final double accuracy, final int seed) {
        return make(reader.iterator(), strategy, proportion, accuracy, seed);
    }
}
