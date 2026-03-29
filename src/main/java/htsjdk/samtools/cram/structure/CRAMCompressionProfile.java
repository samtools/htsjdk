package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.compression.range.RangeParams;
import htsjdk.samtools.cram.compression.rans.RANSNx16Params;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

import java.util.EnumMap;

/**
 * Predefined CRAM compression profiles matching those in htslib/samtools. Each profile defines
 * the CRAM version, compression level, reads-per-slice, and a per-{@link DataSeries} compressor
 * assignment via {@link CompressorDescriptor}.
 *
 * <p>Usage:
 * <pre>
 *   // Get a strategy for a specific profile:
 *   CRAMEncodingStrategy strategy = CRAMCompressionProfile.ARCHIVE.toStrategy();
 *
 *   // Or apply a profile to an existing strategy:
 *   CRAMCompressionProfile.FAST.applyTo(existingStrategy);
 * </pre>
 *
 * @see CRAMEncodingStrategy
 * @see CompressorDescriptor
 */
public enum CRAMCompressionProfile {

    /**
     * Speed-optimized profile. Uses only GZIP at level 1. Writes CRAM 3.0 since no 3.1-specific
     * codecs are used, avoiding the need for a 3.1-capable reader.
     */
    FAST(CramVersions.CRAM_v3, 1, 10_000),

    /**
     * Balanced profile (default). Uses rANS Nx16 for entropy-rich data series, FQZComp for quality
     * scores, and Name Tokeniser for read names. Writes CRAM 3.1.
     */
    NORMAL(CramVersions.CRAM_v3_1, 5, 10_000),

    /**
     * Size-optimized profile. Uses GZIP at higher compression level with FQZComp for quality scores.
     * Does not use Name Tokeniser or rANS (matching htslib SMALL behavior). Writes CRAM 3.1.
     */
    SMALL(CramVersions.CRAM_v3_1, 6, 25_000),

    /**
     * Maximum compression profile. Uses rANS Nx16 for entropy-rich data, FQZComp for quality scores,
     * and Name Tokeniser for read names at higher compression settings. Writes CRAM 3.1.
     *
     * <p>This profile uses trial compression: multiple codecs are tried per block and the smallest
     * result wins. Additional candidates include BZIP2, the Range (arithmetic) coder, and GZIP.
     */
    ARCHIVE(CramVersions.CRAM_v3_1, 7, 100_000);

    private final CRAMVersion cramVersion;
    private final int gzipLevel;
    private final int readsPerSlice;

    /**
     * Look up a profile by name, ignoring case. For example, {@code "archive"}, {@code "ARCHIVE"},
     * and {@code "Archive"} all return {@link #ARCHIVE}.
     *
     * @param name the profile name (case-insensitive)
     * @return the matching profile
     * @throws IllegalArgumentException if no profile matches
     */
    public static CRAMCompressionProfile valueOfCaseInsensitive(final String name) {
        for (final CRAMCompressionProfile profile : values()) {
            if (profile.name().equalsIgnoreCase(name)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown CRAM compression profile: " + name +
                ". Must be one of: fast, normal, small, archive");
    }

    CRAMCompressionProfile(final CRAMVersion cramVersion, final int gzipLevel, final int readsPerSlice) {
        this.cramVersion = cramVersion;
        this.gzipLevel = gzipLevel;
        this.readsPerSlice = readsPerSlice;
    }

    /**
     * Create a new {@link CRAMEncodingStrategy} configured with this profile's settings.
     *
     * @return a new strategy with this profile applied
     */
    public CRAMEncodingStrategy toStrategy() {
        // Use the no-profile constructor to avoid infinite recursion (default constructor calls NORMAL.applyTo)
        final CRAMEncodingStrategy strategy = new CRAMEncodingStrategy(false);
        applyTo(strategy);
        return strategy;
    }

    /**
     * Apply this profile's settings to an existing strategy, overwriting the CRAM version,
     * GZIP compression level, reads-per-slice, and compressor map.
     *
     * @param strategy the strategy to modify
     */
    public void applyTo(final CRAMEncodingStrategy strategy) {
        strategy.setCramVersion(cramVersion);
        strategy.setGZIPCompressionLevel(gzipLevel);
        strategy.setReadsPerSlice(readsPerSlice);
        strategy.setCompressorMap(buildCompressorMap());
        strategy.setTrialCandidatesMap(buildTrialCandidatesMap());
    }

    /**
     * Build the per-DataSeries compressor map for this profile. Only includes data series
     * that are actually written by the htsjdk CRAM implementation (excludes obsolete TC, TN
     * and unused BB, QQ series).
     */
    private EnumMap<DataSeries, CompressorDescriptor> buildCompressorMap() {
        final EnumMap<DataSeries, CompressorDescriptor> map = new EnumMap<>(DataSeries.class);

        switch (this) {
            case FAST:
                buildFastMap(map);
                break;
            case NORMAL:
                buildNormalMap(map);
                break;
            case SMALL:
                buildSmallMap(map);
                break;
            case ARCHIVE:
                buildArchiveMap(map);
                break;
        }

        return map;
    }

    /** FAST: all GZIP at level 1, no 3.1 codecs. */
    private void buildFastMap(final EnumMap<DataSeries, CompressorDescriptor> map) {
        final CompressorDescriptor gzip = new CompressorDescriptor(BlockCompressionMethod.GZIP, gzipLevel);
        for (final DataSeries ds : getWrittenDataSeries()) {
            map.put(ds, gzip);
        }
    }

    /** NORMAL: rANS Nx16 for low-entropy data, GZIP for positional/byte-array data, FQZComp for QS, NameTok for RN. */
    private void buildNormalMap(final EnumMap<DataSeries, CompressorDescriptor> map) {
        final CompressorDescriptor gzip = new CompressorDescriptor(BlockCompressionMethod.GZIP, gzipLevel);
        final CompressorDescriptor ransOrder0 = new CompressorDescriptor(BlockCompressionMethod.RANSNx16, RANSNx16Params.ORDER.ZERO.ordinal());
        final CompressorDescriptor ransOrder1 = new CompressorDescriptor(BlockCompressionMethod.RANSNx16, RANSNx16Params.ORDER.ONE.ordinal());

        // Default everything to GZIP — then override specific series with better codecs
        for (final DataSeries ds : getWrittenDataSeries()) {
            map.put(ds, gzip);
        }

        // rANS Nx16 Order 0 for position-like integer data with low entropy
        map.put(DataSeries.AP_AlignmentPositionOffset, ransOrder0);
        map.put(DataSeries.RI_RefId, ransOrder0);

        // rANS Nx16 Order 1 for low-entropy integer data series where rANS outperforms GZIP
        map.put(DataSeries.BA_Base, ransOrder1);
        map.put(DataSeries.BF_BitFlags, ransOrder1);
        map.put(DataSeries.BS_BaseSubstitutionCode, ransOrder1);
        map.put(DataSeries.CF_CompressionBitFlags, ransOrder1);
        map.put(DataSeries.FC_FeatureCode, ransOrder1);
        map.put(DataSeries.FN_NumberOfReadFeatures, ransOrder1);
        map.put(DataSeries.MF_MateBitFlags, ransOrder1);
        map.put(DataSeries.MQ_MappingQualityScore, ransOrder1);
        map.put(DataSeries.NS_NextFragmentReferenceSequenceID, ransOrder1);
        map.put(DataSeries.RG_ReadGroup, ransOrder1);
        map.put(DataSeries.RL_ReadLength, ransOrder1);
        map.put(DataSeries.TL_TagIdList, ransOrder1);
        map.put(DataSeries.TS_InsertSize, ransOrder1);

        // Keep GZIP for high-entropy positional data where LZ77 helps
        // NP (mate position), FP (feature position) — these have high variance
        // IN (insertions), SC (soft clips) — byte arrays benefit from LZ77

        // Specialized codecs
        map.put(DataSeries.QS_QualityScore, new CompressorDescriptor(BlockCompressionMethod.FQZCOMP));
        map.put(DataSeries.RN_ReadName, new CompressorDescriptor(BlockCompressionMethod.NAME_TOKENISER));
    }

    /** SMALL: Same codec assignments as NORMAL but at higher compression level. Trial compression
     *  adds BZIP2 alongside rANS/GZIP to let the trial pick the best per data series. */
    private void buildSmallMap(final EnumMap<DataSeries, CompressorDescriptor> map) {
        buildNormalMap(map);
    }

    /** ARCHIVE: Same primary codecs as NORMAL but at higher compression, plus larger slices.
     *  Trial compression candidates (BZIP2, Range coder) are provided via buildTrialCandidatesMap. */
    private void buildArchiveMap(final EnumMap<DataSeries, CompressorDescriptor> map) {
        buildNormalMap(map);
    }

    /**
     * Build the trial compression candidates map for this profile. Only ARCHIVE and SMALL profiles
     * currently use trial compression. For data series with trial candidates, the primary compressor
     * (from buildCompressorMap) plus these additional candidates are all tried, and the smallest wins.
     *
     * @return the trial candidates map, or null if this profile doesn't use trial compression
     */
    private EnumMap<DataSeries, java.util.List<CompressorDescriptor>> buildTrialCandidatesMap() {
        if (this != ARCHIVE && this != SMALL) {
            return null;
        }

        final EnumMap<DataSeries, java.util.List<CompressorDescriptor>> trialMap = new EnumMap<>(DataSeries.class);

        // BZIP2 as an alternative for general data series
        final CompressorDescriptor bzip2 = new CompressorDescriptor(BlockCompressionMethod.BZIP2);

        // Range (ARITH) coder variants as alternatives to rANS Nx16
        final CompressorDescriptor arithOrder0 = new CompressorDescriptor(BlockCompressionMethod.ADAPTIVE_ARITHMETIC, 0);
        final CompressorDescriptor arithOrder1 = new CompressorDescriptor(
                BlockCompressionMethod.ADAPTIVE_ARITHMETIC, RangeParams.ORDER_FLAG_MASK);

        // GZIP as a fallback candidate (may win for small blocks)
        final CompressorDescriptor gzip = new CompressorDescriptor(BlockCompressionMethod.GZIP, gzipLevel);

        if (this == ARCHIVE) {
            // For entropy-rich data series that use rANS Nx16: also try Range coder and BZIP2
            for (final DataSeries ds : new DataSeries[]{
                    DataSeries.BA_Base, DataSeries.BF_BitFlags, DataSeries.CF_CompressionBitFlags,
                    DataSeries.NS_NextFragmentReferenceSequenceID, DataSeries.RG_ReadGroup,
                    DataSeries.RL_ReadLength, DataSeries.TS_InsertSize}) {
                trialMap.put(ds, java.util.List.of(arithOrder1, bzip2, gzip));
            }
            // Position-like data: also try Range order 0
            for (final DataSeries ds : new DataSeries[]{
                    DataSeries.AP_AlignmentPositionOffset, DataSeries.RI_RefId}) {
                trialMap.put(ds, java.util.List.of(arithOrder0, bzip2, gzip));
            }
            // GZIP-compressed data series: also try BZIP2 and rANS
            final CompressorDescriptor ransOrder1 = new CompressorDescriptor(
                    BlockCompressionMethod.RANSNx16, RANSNx16Params.ORDER.ONE.ordinal());
            for (final DataSeries ds : new DataSeries[]{
                    DataSeries.BS_BaseSubstitutionCode, DataSeries.DL_DeletionLength,
                    DataSeries.FC_FeatureCode, DataSeries.FN_NumberOfReadFeatures,
                    DataSeries.FP_FeaturePosition, DataSeries.HC_HardClip,
                    DataSeries.MF_MateBitFlags, DataSeries.MQ_MappingQualityScore,
                    DataSeries.NF_RecordsToNextFragment, DataSeries.NP_NextFragmentAlignmentStart,
                    DataSeries.PD_padding, DataSeries.RS_RefSkip, DataSeries.TL_TagIdList}) {
                trialMap.put(ds, java.util.List.of(bzip2, ransOrder1));
            }
        } else if (this == SMALL) {
            // SMALL: same as NORMAL primary codecs but with BZIP2 added to trial candidates.
            // htslib SMALL (level 6, use_rans=1, use_bz2=1) trials GZIP + BZIP2 + all rANS variants.
            // For rANS-primary series: also try BZIP2 and GZIP
            for (final DataSeries ds : new DataSeries[]{
                    DataSeries.BA_Base, DataSeries.BF_BitFlags, DataSeries.CF_CompressionBitFlags,
                    DataSeries.NS_NextFragmentReferenceSequenceID, DataSeries.RG_ReadGroup,
                    DataSeries.RL_ReadLength, DataSeries.TS_InsertSize}) {
                trialMap.put(ds, java.util.List.of(bzip2, gzip));
            }
            // For rANS Order 0 series: also try BZIP2 and GZIP
            for (final DataSeries ds : new DataSeries[]{
                    DataSeries.AP_AlignmentPositionOffset, DataSeries.RI_RefId}) {
                trialMap.put(ds, java.util.List.of(bzip2, gzip));
            }
            // For GZIP-primary series: also try BZIP2 and rANS
            final CompressorDescriptor ransOrder1 = new CompressorDescriptor(
                    BlockCompressionMethod.RANSNx16, RANSNx16Params.ORDER.ONE.ordinal());
            for (final DataSeries ds : new DataSeries[]{
                    DataSeries.BS_BaseSubstitutionCode, DataSeries.DL_DeletionLength,
                    DataSeries.FC_FeatureCode, DataSeries.FN_NumberOfReadFeatures,
                    DataSeries.FP_FeaturePosition, DataSeries.HC_HardClip,
                    DataSeries.MF_MateBitFlags, DataSeries.MQ_MappingQualityScore,
                    DataSeries.NF_RecordsToNextFragment, DataSeries.NP_NextFragmentAlignmentStart,
                    DataSeries.PD_padding, DataSeries.RS_RefSkip, DataSeries.TL_TagIdList,
                    DataSeries.IN_Insertion, DataSeries.SC_SoftClip}) {
                trialMap.put(ds, java.util.List.of(bzip2, ransOrder1));
            }
        }

        return trialMap;
    }

    /**
     * Returns the set of DataSeries values that are actually written by the htsjdk CRAM implementation.
     * Excludes obsolete (TC, TN) and unused (QQ) series.
     */
    private static final DataSeries[] WRITTEN_DATA_SERIES = {
            DataSeries.AP_AlignmentPositionOffset,
            DataSeries.BA_Base,
            DataSeries.BF_BitFlags,
            DataSeries.BS_BaseSubstitutionCode,
            DataSeries.CF_CompressionBitFlags,
            DataSeries.DL_DeletionLength,
            DataSeries.FC_FeatureCode,
            DataSeries.FN_NumberOfReadFeatures,
            DataSeries.FP_FeaturePosition,
            DataSeries.HC_HardClip,
            DataSeries.IN_Insertion,
            DataSeries.MF_MateBitFlags,
            DataSeries.MQ_MappingQualityScore,
            DataSeries.NF_RecordsToNextFragment,
            DataSeries.NP_NextFragmentAlignmentStart,
            DataSeries.NS_NextFragmentReferenceSequenceID,
            DataSeries.PD_padding,
            DataSeries.QS_QualityScore,
            DataSeries.RG_ReadGroup,
            DataSeries.RI_RefId,
            DataSeries.RL_ReadLength,
            DataSeries.RN_ReadName,
            DataSeries.RS_RefSkip,
            DataSeries.SC_SoftClip,
            DataSeries.TL_TagIdList,
            DataSeries.TS_InsertSize,
    };

    private static DataSeries[] getWrittenDataSeries() {
        return WRITTEN_DATA_SERIES;
    }
}
