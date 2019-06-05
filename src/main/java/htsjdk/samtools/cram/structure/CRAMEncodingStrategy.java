/*
 * Copyright (c) 2019 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.Defaults;
import htsjdk.utils.ValidationUtils;
import htsjdk.samtools.cram.ref.ReferenceContextType;

/**
 * Parameters that can be set to control the encoding strategy used when writing CRAM.
 */
public class CRAMEncodingStrategy {
    // Default value for the minimum number of reads we need to have seen to emit a single-reference slice.
    // If we've see fewer than this number, and we have more reads from a different reference context, we prefer to
    // switch to, and subsequently emit, a multiple reference slice, rather than a small single-reference
    // that contains fewer than this number of records.
    public static final int DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD = 1000;

    // This number must be >= DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD (required by ContainerFactory).
    public static final int DEFAULT_READS_PER_SLICE = 10000;

    // encoding strategies
    private CompressionHeaderEncodingMap customCompressionHeaderEncodingMap;

    //Note: should this have separate values for tags (separate from CRAMRecord data) ?
    private int gzipCompressionLevel = Defaults.COMPRESSION_LEVEL;

   // The minimum number of reads we need to have seen to emit a single-reference slice. If we've seen
    // fewer than this number, and we have more reads from a different reference context, we prefer to
    // switch to, and subsequently emit, a multiple reference slice, rather than a small single-reference
    // that contains fewer than this number of records. This number must be < readsPerSlice.
    private int minimumSingleReferenceSliceSize = DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD;
    private int readsPerSlice = DEFAULT_READS_PER_SLICE;
    private int slicesPerContainer = 1;

    /**
     * Create an encoding strategy that uses all default values.
     */
    public CRAMEncodingStrategy() {
        // use defaults;
    }

    /**
     * Set number of slices per container. In some cases, a container containing fewer slices than the
     * requested value will be produced in order to honor the specification rule that all slices in a
     * container must have the same {@link ReferenceContextType}.
     *
     * Note: this value must be >= {@link #getMinimumSingleReferenceSliceSize}.
     *
     * @param readsPerSlice number of slices written per container
     * @return updated CRAMEncodingStrategy
     */
    public CRAMEncodingStrategy setReadsPerSlice(final int readsPerSlice) {
        ValidationUtils.validateArg(
                readsPerSlice > 0 && readsPerSlice >= minimumSingleReferenceSliceSize,
                String.format("Reads per slice must be > 0 and < minimum single reference slice size (%d)",
                        minimumSingleReferenceSliceSize));
        this.readsPerSlice = readsPerSlice;
        return this;
    }

   /**
    * The minimum number of reads we need to have seen to emit a single-reference slice. If we've seen
    * fewer than this number, and we have more reads from a different reference context, we prefer to
    * switch to, and subsequently emit, a multiple reference slice, rather than a small single-reference
    * that contains fewer than this number of records.
    *
    * This number must be < the value for {@link #getReadsPerSlice}
    *
    * @param minimumSingleReferenceSliceSize
    */
    public CRAMEncodingStrategy setMinimumSingleReferenceSliceSize(int minimumSingleReferenceSliceSize) {
        ValidationUtils.validateArg(
                minimumSingleReferenceSliceSize <= readsPerSlice,
                String.format("Minimm single reference slice size must be < the reads per slice size (%d)", readsPerSlice));
        this.minimumSingleReferenceSliceSize = minimumSingleReferenceSliceSize;
        return this;
    }

    public int getMinimumSingleReferenceSliceSize() {
        return minimumSingleReferenceSliceSize;
    }

    public CRAMEncodingStrategy setGZIPCompressionLevel(final int compressionLevel) {
        ValidationUtils.validateArg(compressionLevel >=0 && compressionLevel <= 10,
                "cram gzip compression level must be > 0 and <= 10");
        this.gzipCompressionLevel = compressionLevel;
        return this;
    }

    /**
     * Set the number of slices per container. If > 1, multiple slices will be placed in the same container
     * if the slices share the same reference context (container records mapped to the same contig). MULTI-REF
     * slices are always emitted as a single contain to avoid conferring MULTI-REF on the next slice, which
     * might otherwise be single-ref; the spec requires a MULTI_REF container to only contain multi-ref slices).
     * @param slicesPerContainer - requested number of slices per container
     * @return CRAMEncodingStrategy
     */
    public CRAMEncodingStrategy setSlicesPerContainer(final int slicesPerContainer) {
        ValidationUtils.validateArg(slicesPerContainer >=0, "slicesPerContainer must be > 0");
        this.slicesPerContainer = slicesPerContainer;
        return this;
    }

    /**
     * Set the {@link CompressionHeaderEncodingMap} to use.
     *
     * @param encodingMap the encoding map to use
     */
    public void setCustomCompressionHeaderEncodingMap(final CompressionHeaderEncodingMap encodingMap) {
        this.customCompressionHeaderEncodingMap = encodingMap;
    }
    public CompressionHeaderEncodingMap getCustomCompressionHeaderEncodingMap() { return customCompressionHeaderEncodingMap; }

    public int getGZIPCompressionLevel() { return gzipCompressionLevel; }
    public int getReadsPerSlice() { return readsPerSlice; }
    public int getSlicesPerContainer() { return slicesPerContainer; }

    @Override
    public String toString() {
        return "CRAMEncodingStrategy{" +
                ", customCompressionMap='" + customCompressionHeaderEncodingMap + '\'' +
                ", gzipCompressionLevel=" + gzipCompressionLevel +
                ", readsPerSlice=" + readsPerSlice +
                ", slicesPerContainer=" + slicesPerContainer +
                '}';
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CRAMEncodingStrategy that = (CRAMEncodingStrategy) o;

        if (gzipCompressionLevel != that.gzipCompressionLevel) return false;
        if (getMinimumSingleReferenceSliceSize() != that.getMinimumSingleReferenceSliceSize()) return false;
        if (getReadsPerSlice() != that.getReadsPerSlice()) return false;
        if (getSlicesPerContainer() != that.getSlicesPerContainer()) return false;
        return getCustomCompressionHeaderEncodingMap() != null ?
                getCustomCompressionHeaderEncodingMap().equals(that.getCustomCompressionHeaderEncodingMap()) :
                that.getCustomCompressionHeaderEncodingMap() == null;
    }

    @Override
    public int hashCode() {
        int result = getCustomCompressionHeaderEncodingMap() != null ?
                getCustomCompressionHeaderEncodingMap().hashCode() : 0;
        result = 31 * result + gzipCompressionLevel;
        result = 31 * result + getMinimumSingleReferenceSliceSize();
        result = 31 * result + getReadsPerSlice();
        result = 31 * result + getSlicesPerContainer();
        return result;
    }

}
