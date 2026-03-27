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
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.ref.ReferenceContextType;
import htsjdk.utils.ValidationUtils;

import java.util.EnumMap;

/**
 * Parameters that control the encoding strategy used when writing CRAM. Includes the CRAM version,
 * compression level, container/slice sizing, and per-{@link DataSeries} compressor assignments.
 *
 * <p>The default constructor applies the {@link CRAMCompressionProfile#NORMAL} profile. Use
 * {@link CRAMCompressionProfile#toStrategy()} or {@link CRAMCompressionProfile#applyTo(CRAMEncodingStrategy)}
 * to configure a specific profile.
 *
 * @see CRAMCompressionProfile
 * @see CompressorDescriptor
 */
public class CRAMEncodingStrategy {
    public static final int DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD = 1000;
    public static final int DEFAULT_READS_PER_SLICE = 10000;

    private CRAMVersion cramVersion = CramVersions.CRAM_v3_1;
    private int gzipCompressionLevel = Defaults.COMPRESSION_LEVEL;
    private int minimumSingleReferenceSliceSize = DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD;
    private int readsPerSlice = DEFAULT_READS_PER_SLICE;
    private int slicesPerContainer = 1;

    private EnumMap<DataSeries, CompressorDescriptor> compressorMap;

    // Optional: additional trial compression candidates per data series. When present for a data series,
    // the primary compressor from compressorMap plus these additional candidates are wrapped in a
    // TrialCompressor that tries all and picks the smallest output.
    private EnumMap<DataSeries, java.util.List<CompressorDescriptor>> trialCandidatesMap;

    // Advanced override: a pre-built encoding map that bypasses the compressor map entirely.
    // Used by tests that need low-level control over encoding descriptors.
    private CompressionHeaderEncodingMap customCompressionHeaderEncodingMap;

    /**
     * Create an encoding strategy with the {@link CRAMCompressionProfile#NORMAL} profile applied.
     */
    public CRAMEncodingStrategy() {
        CRAMCompressionProfile.NORMAL.applyTo(this);
    }

    /**
     * Package-private constructor that skips profile application. Used by
     * {@link CRAMCompressionProfile#toStrategy()} to avoid infinite recursion.
     *
     * @param applyDefaultProfile ignored — exists only to differentiate from the default constructor
     */
    CRAMEncodingStrategy(final boolean applyDefaultProfile) {
        // no profile applied; caller is responsible for calling applyTo()
    }

    /** @return the CRAM version to write */
    public CRAMVersion getCramVersion() {
        return cramVersion;
    }

    /**
     * Set the CRAM version to write.
     *
     * @param cramVersion the CRAM version (e.g., {@link CramVersions#CRAM_v3} or {@link CramVersions#CRAM_v3_1})
     * @return this strategy for chaining
     */
    public CRAMEncodingStrategy setCramVersion(final CRAMVersion cramVersion) {
        ValidationUtils.nonNull(cramVersion, "CRAM version must not be null");
        this.cramVersion = cramVersion;
        return this;
    }

    /**
     * Set number of reads per slice. In some cases, a container containing fewer slices than the
     * requested value will be produced in order to honor the specification rule that all slices in a
     * container must have the same {@link ReferenceContextType}.
     *
     * Note: this value must be >= {@link #getMinimumSingleReferenceSliceSize}.
     *
     * @param readsPerSlice number of reads written per slice
     * @return updated CRAMEncodingStrategy
     */
    public CRAMEncodingStrategy setReadsPerSlice(final int readsPerSlice) {
        ValidationUtils.validateArg(
                readsPerSlice > 0 && readsPerSlice >= minimumSingleReferenceSliceSize,
                String.format("Reads per slice must be > 0 and >= minimum single reference slice size (%d)",
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
    * This number must be <= the value for {@link #getReadsPerSlice}
    *
    * @param minimumSingleReferenceSliceSize the minimum slice size
    * @return this strategy for chaining
    */
    public CRAMEncodingStrategy setMinimumSingleReferenceSliceSize(int minimumSingleReferenceSliceSize) {
        ValidationUtils.validateArg(
                minimumSingleReferenceSliceSize <= readsPerSlice,
                String.format("Minimum single reference slice size must be <= the reads per slice size (%d)", readsPerSlice));
        this.minimumSingleReferenceSliceSize = minimumSingleReferenceSliceSize;
        return this;
    }

    public int getMinimumSingleReferenceSliceSize() {
        return minimumSingleReferenceSliceSize;
    }

    /**
     * Set the GZIP compression level used for data series compressed with GZIP.
     *
     * @param compressionLevel GZIP compression level (0-10)
     * @return this strategy for chaining
     */
    public CRAMEncodingStrategy setGZIPCompressionLevel(final int compressionLevel) {
        ValidationUtils.validateArg(compressionLevel >= 0 && compressionLevel <= 10,
                "cram gzip compression level must be >= 0 and <= 10");
        this.gzipCompressionLevel = compressionLevel;
        return this;
    }

    /**
     * Set the number of slices per container. If > 1, multiple slices will be placed in the same container
     * if the slices share the same reference context (container records mapped to the same contig). MULTI-REF
     * slices are always emitted as a single container to avoid conferring MULTI-REF on the next slice, which
     * might otherwise be single-ref; the spec requires a MULTI_REF container to only contain multi-ref slices).
     *
     * @param slicesPerContainer requested number of slices per container
     * @return this strategy for chaining
     */
    public CRAMEncodingStrategy setSlicesPerContainer(final int slicesPerContainer) {
        ValidationUtils.validateArg(slicesPerContainer > 0, "slicesPerContainer must be > 0");
        this.slicesPerContainer = slicesPerContainer;
        return this;
    }

    /**
     * Set the per-DataSeries compressor map. Each entry maps a {@link DataSeries} to the
     * {@link CompressorDescriptor} that should be used to compress its block.
     *
     * @param compressorMap the compressor map (defensively copied)
     * @return this strategy for chaining
     */
    public CRAMEncodingStrategy setCompressorMap(final EnumMap<DataSeries, CompressorDescriptor> compressorMap) {
        ValidationUtils.nonNull(compressorMap, "compressor map must not be null");
        this.compressorMap = new EnumMap<>(compressorMap);
        return this;
    }

    /** @return the per-DataSeries compressor map, or null if not set */
    public EnumMap<DataSeries, CompressorDescriptor> getCompressorMap() {
        return compressorMap;
    }

    /**
     * Set additional trial compression candidates per DataSeries. For data series with entries in
     * this map, a {@link htsjdk.samtools.cram.compression.TrialCompressor} will be created that
     * tries the primary compressor plus all listed candidates, selecting the smallest output.
     *
     * @param trialCandidatesMap map of data series to additional candidate descriptors
     * @return this strategy for chaining
     */
    public CRAMEncodingStrategy setTrialCandidatesMap(
            final EnumMap<DataSeries, java.util.List<CompressorDescriptor>> trialCandidatesMap) {
        this.trialCandidatesMap = trialCandidatesMap != null ? new EnumMap<>(trialCandidatesMap) : null;
        return this;
    }

    /** @return the trial candidates map, or null if trial compression is not configured */
    public EnumMap<DataSeries, java.util.List<CompressorDescriptor>> getTrialCandidatesMap() {
        return trialCandidatesMap;
    }

    /**
     * Set a pre-built {@link CompressionHeaderEncodingMap} that bypasses the compressor map.
     * This is an advanced override intended for tests that need low-level control over encoding
     * descriptors. When set, {@link htsjdk.samtools.cram.build.CompressionHeaderFactory} will use
     * this map directly instead of building one from the compressor map.
     *
     * @param encodingMap the encoding map to use, or null to use the compressor map
     */
    public void setCustomCompressionHeaderEncodingMap(final CompressionHeaderEncodingMap encodingMap) {
        this.customCompressionHeaderEncodingMap = encodingMap;
    }

    /** @return the custom encoding map, or null if the compressor map should be used */
    public CompressionHeaderEncodingMap getCustomCompressionHeaderEncodingMap() {
        return customCompressionHeaderEncodingMap;
    }

    public int getGZIPCompressionLevel() { return gzipCompressionLevel; }
    public int getReadsPerSlice() { return readsPerSlice; }
    public int getSlicesPerContainer() { return slicesPerContainer; }

    @Override
    public String toString() {
        return "CRAMEncodingStrategy{" +
                "cramVersion=" + cramVersion +
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
        if (!cramVersion.equals(that.cramVersion)) return false;
        return compressorMap != null ?
                compressorMap.equals(that.compressorMap) :
                that.compressorMap == null;
    }

    @Override
    public int hashCode() {
        int result = cramVersion.hashCode();
        result = 31 * result + gzipCompressionLevel;
        result = 31 * result + getMinimumSingleReferenceSliceSize();
        result = 31 * result + getReadsPerSlice();
        result = 31 * result + getSlicesPerContainer();
        result = 31 * result + (compressorMap != null ? compressorMap.hashCode() : 0);
        return result;
    }
}
