/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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

import htsjdk.samtools.util.BlockCompressedFilePointerUtil;

import java.util.Arrays;
import java.util.List;

import static htsjdk.samtools.GenomicIndexUtil.MAX_BINS;

/**
 * Builder for a BinningIndexContent object.
 */
public class BinningIndexBuilder {
    private static final int UNINITIALIZED_WINDOW = -1;
    private final int referenceSequence;
    // the bins for the current reference
    private final Bin[] bins; // made only as big as needed for each reference
    private int binsSeen = 0;

    // linear index for the current reference
    private final long[] index = new long[LinearIndex.MAX_LINEAR_INDEX_SIZE];
    private int largestIndexSeen = -1;

    private final boolean fillInUninitializedValues;

    /**
     *
     * @param referenceSequence
     * @param sequenceLength 0 implies unknown length.  Known length will reduce memory use.
     * @param fillInUninitializedValues if true, set uninitialized values (-1) to the last non-zero offset;
     *                                  if false, leave uninitialized values as -1, which is required when merging index files
     *                                  (see {@link BAMIndexMerger})
     */
    public BinningIndexBuilder(final int referenceSequence, final int sequenceLength, final boolean fillInUninitializedValues) {
        this.referenceSequence = referenceSequence;
        this.fillInUninitializedValues = fillInUninitializedValues;
        // Initially set each window to -1 so we can distinguish between windows that have no overlapping
        // features, and those whose lowest offset is 0, which is a valid (virtual file) offset for feature
        // formats that don't require a header.
        Arrays.fill(index, UNINITIALIZED_WINDOW);
        final int numBins;
        if (sequenceLength <= 0) numBins = MAX_BINS + 1;
        else numBins = AbstractBAMFileIndex.getMaxBinNumberForSequenceLength(sequenceLength) + 1;
        bins = new Bin[numBins];
    }
    /**
     *
     * @param referenceSequence
     * @param sequenceLength 0 implies unknown length.  Known length will reduce memory use.
     */
    public BinningIndexBuilder(final int referenceSequence, final int sequenceLength) {
        this(referenceSequence, sequenceLength, true);
    }

    public BinningIndexBuilder(final int referenceSequence) {
        this(referenceSequence, 0);
    }

    /**
     * coordinates are 1-based, inclusive
     */
    public interface FeatureToBeIndexed {
        public int getStart();
        public int getEnd();
        public Integer getIndexingBin();
        public Chunk getChunk();
    }

    public void processFeature(final FeatureToBeIndexed feature) {

        // process bins

        final Integer binNumber = feature.getIndexingBin();
        final int binNum = binNumber == null ? computeIndexingBin(feature) : binNumber;


        // is there a bin already represented for this index?  if not, add one
        final Bin bin;
        if (bins[binNum] != null) {
            bin = bins[binNum];
        } else {
            bin = new Bin(referenceSequence, binNum);
            bins[binNum] = bin;
            binsSeen++;
        }

        // process chunks

        final Chunk newChunk = feature.getChunk();
        final long chunkStart = newChunk.getChunkStart();
        bin.addChunk(newChunk);

        // process linear index

        // the smallest file offset that appears in the 16k window for this bin
        final int featureEnd = feature.getEnd();
        int startWindow = LinearIndex.convertToLinearIndexOffset(feature.getStart()); // the 16k window
        final int endWindow;

        if (featureEnd == GenomicIndexUtil.UNSET_GENOMIC_LOCATION) {   // assume feature uses one position
            // Next line for C (samtools index) compatibility. Differs only when on a window boundary
            startWindow = LinearIndex.convertToLinearIndexOffset(feature.getStart() - 1);
            endWindow = startWindow;
        } else {
            endWindow = LinearIndex.convertToLinearIndexOffset(featureEnd);
        }

        if (endWindow > largestIndexSeen) {
            largestIndexSeen = endWindow;
        }

        // Set the linear index at every 16K window that this feature overlaps, but only if this chunk
        // start is strictly earlier than any previously seen chunk start for the window, or if the window
        // is uninitialized (this is the first feature overlapping this window).
        for (int win = startWindow; win <= endWindow; win++) {
            // Initially each window is set to UNINITIALIZED_WINDOW (-1) so that we can distinguish here between
            // windows that have no overlapping features, and those whose lowest feature offset is legitimately 0,
            // which is a valid (virtual file) offset for feature formats that don't require a header.
            if (index[win] == UNINITIALIZED_WINDOW || chunkStart < index[win]) {
                index[win] = chunkStart;
            }
        }
    }

    /**
     * Creates the BAMIndexContent for this reference.
     * Requires all features of the reference have already been processed.
     */
    public BinningIndexContent generateIndexContent() {


        // process bins
        if (binsSeen == 0) return null;  // no bins for this reference

        // process chunks
        // nothing needed

        // process linear index
        // linear index will only be as long as the largest index seen
        final long[] newIndex = new long[largestIndexSeen + 1]; // in java1.6 Arrays.copyOf(index, largestIndexSeen + 1);

        // C (samtools index) also fills in intermediate 0's with values.  This seems unnecessary, but safe
        long lastNonZeroOffset = 0;
        for (int i = 0; i <= largestIndexSeen; i++) {
            if (index[i] == UNINITIALIZED_WINDOW) {
                if (fillInUninitializedValues) {
                    index[i] = lastNonZeroOffset; // not necessary, but C (samtools index) does this
                }
                // note, if fillInUninitializedValues is false BAMIndexWriterTest will have to change
            } else {
                lastNonZeroOffset = index[i];
            }
            newIndex[i] = index[i];
        }

        final LinearIndex linearIndex = new LinearIndex(referenceSequence, 0, newIndex);

        return new BinningIndexContent(referenceSequence, new BinningIndexContent.BinList(bins, binsSeen), linearIndex);
    }

    private int computeIndexingBin(final FeatureToBeIndexed feature) {
        // regionToBin has zero-based, half-open API
        final int start = feature.getStart()-1;
        int end = feature.getEnd();
        if (end <= 0) {
            // If feature end cannot be determined (e.g. because a read is not really aligned),
            // then treat this as a one base feature for indexing purposes.
            end = start + 1;
        }
        return GenomicIndexUtil.regionToBin(start, end);
    }
}
