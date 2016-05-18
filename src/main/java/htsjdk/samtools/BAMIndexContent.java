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
package htsjdk.samtools;

/**
 * Represents the contents of a bam index file for one reference.
 * A BAM index (.bai) file contains information for all references in the bam file.
 * This class describes the data present in the index file for one of these references;
 * including the bins, chunks, and linear index.
 */
class BAMIndexContent extends BinningIndexContent {
    /**
     * Chunks containing metaData for the reference, e.g. number of aligned and unaligned records
     */
    private final BAMIndexMetaData mMetaData;



    /**
     * @param referenceSequence Content corresponds to this reference.
     * @param binList              Array of bins represented by this content, possibly sparse
     * @param metaData          Extra information about the reference in this index
     * @param linearIndex       Additional index used to optimize queries
     */
    BAMIndexContent(final int referenceSequence, final BinList binList, final BAMIndexMetaData metaData, final LinearIndex linearIndex) {
        super(referenceSequence, binList, linearIndex);
        this.mMetaData = metaData;
    }

    /**
     * @param referenceSequence Content corresponds to this reference.
     * @param bins              Array of bins represented by this content, possibly sparse
     * @param numberOfBins      Number of non-null bins
     * @param metaData          Extra information about the reference in this index
     * @param linearIndex       Additional index used to optimize queries
     */
    BAMIndexContent(final int referenceSequence, final Bin[] bins, final int numberOfBins, final BAMIndexMetaData metaData, final LinearIndex linearIndex) {
        this(referenceSequence, new BinList(bins, numberOfBins), metaData, linearIndex);
    }

    /**
     * @return the meta data chunks for this content
     */
    public BAMIndexMetaData getMetaData() {
        return mMetaData;
    }

}
