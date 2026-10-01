package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokeniserExternalCompressor;
import htsjdk.samtools.cram.compression.range.RangeParams;
import htsjdk.samtools.cram.compression.rans.RANS4x8Params;
import htsjdk.samtools.cram.compression.rans.RANSNx16Params;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Collectors;

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
     * Speed-optimized profile. Uses GZIP at level 1 for the data series, and GZIP or rANS 4x8 for tags. Writes
     * CRAM 3.0, which readers without CRAM 3.1 support can read.
     */
    FAST(CramVersions.CRAM_v3, 1, 10_000),

    /**
     * Balanced profile (default), matching htslib's normal. Uses rANS Nx16 for entropy-rich data series and the Name
     * Tokeniser for read names; quality scores and tags each use the smallest of GZIP and the rANS Nx16 variants
     * htslib tries. Writes CRAM 3.1.
     */
    NORMAL(CramVersions.CRAM_v3_1, 5, 10_000),

    /**
     * Size-optimized profile, matching htslib's small. Uses the same codecs as NORMAL at a higher GZIP level and with
     * larger slices; quality scores and tags also try BZIP2 and two more rANS Nx16 variants, and quality scores
     * FQZComp. Writes CRAM 3.1.
     */
    SMALL(CramVersions.CRAM_v3_1, 6, 25_000),

    /**
     * Maximum compression profile, matching htslib's archive. As SMALL, with still larger slices, the Range
     * (arithmetic) coder among the candidates for quality scores, tags and the entropy-rich data series, and read names
     * tokenised with the arithmetic coder. Writes CRAM 3.1.
     */
    ARCHIVE(CramVersions.CRAM_v3_1, 7, 100_000),

    /**
     * Balanced profile for CRAM 3.0, which readers without CRAM 3.1 support can read. Uses the codecs htsjdk wrote
     * CRAM 3.0 with before 5.0.0: rANS 4x8 for the low-entropy data series, GZIP for the rest, and GZIP or rANS 4x8
     * for tags.
     */
    NORMAL_3_0(CramVersions.CRAM_v3, 5, 10_000);

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
        throw new IllegalArgumentException("Unknown CRAM compression profile: " + name + ". Must be one of: "
                + Arrays.stream(values()).map(p -> p.name().toLowerCase()).collect(Collectors.joining(", ")));
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
     * GZIP compression level, reads-per-slice, compressor map, trial candidates, and tag compressors.
     *
     * @param strategy the strategy to modify
     */
    public void applyTo(final CRAMEncodingStrategy strategy) {
        strategy.setCramVersion(cramVersion);
        strategy.setGZIPCompressionLevel(gzipLevel);
        strategy.setReadsPerSlice(readsPerSlice);
        strategy.setCompressorMap(buildCompressorMap());
        strategy.setTrialCandidatesMap(buildTrialCandidatesMap());
        strategy.setTagCompressorCandidates(buildTagCompressorCandidates());
    }

    /**
     * The compressors tried on each tag's block: for a CRAM 3.0 profile GZIP and rANS 4x8, and for 3.1 those htslib
     * tries on a block for the same profile.
     */
    private List<CompressorDescriptor> buildTagCompressorCandidates() {
        final CompressorDescriptor gzip = new CompressorDescriptor(BlockCompressionMethod.GZIP, gzipLevel);
        if (cramVersion.equals(CramVersions.CRAM_v3)) {
            return List.of(
                    gzip,
                    new CompressorDescriptor(BlockCompressionMethod.RANS, RANS4x8Params.ORDER.ZERO.ordinal()),
                    new CompressorDescriptor(BlockCompressionMethod.RANS, RANS4x8Params.ORDER.ONE.ordinal()));
        }
        return htslibBlockCompressors();
    }

    /**
     * The compressors htslib tries on a block for this CRAM 3.1 profile ({@code cram_compress_slice}): GZIP at the
     * profile's level; BZIP2 for SMALL and ARCHIVE; rANS Nx16 with the flag combinations htslib tries at the profile's
     * level; and for ARCHIVE the Range coder with its combinations. It leaves out htslib's GZIP at level 1 and with
     * the RLE strategy.
     */
    private List<CompressorDescriptor> htslibBlockCompressors() {
        final List<CompressorDescriptor> compressors = new ArrayList<>();
        compressors.add(new CompressorDescriptor(BlockCompressionMethod.GZIP, gzipLevel));
        if (this == SMALL || this == ARCHIVE) {
            compressors.add(new CompressorDescriptor(BlockCompressionMethod.BZIP2));
        }
        final int order1 = RANSNx16Params.ORDER_FLAG_MASK;
        final int rle = RANSNx16Params.RLE_FLAG_MASK;
        final int pack = RANSNx16Params.PACK_FLAG_MASK;
        final int stripe = RANSNx16Params.STRIPE_FLAG_MASK;
        final List<Integer> ransFlags =
                new ArrayList<>(List.of(0, order1, rle, order1 | stripe, pack, order1 | pack | rle));
        if (gzipLevel > 5) {
            ransFlags.addAll(List.of(order1 | pack, pack | rle));
        }
        for (final int flags : ransFlags) {
            compressors.add(new CompressorDescriptor(BlockCompressionMethod.RANSNx16, flags));
        }
        if (this == ARCHIVE) {
            for (final int flags :
                    List.of(0, order1, rle, order1 | stripe, pack, order1 | pack, pack | rle, order1 | pack | rle)) {
                compressors.add(new CompressorDescriptor(BlockCompressionMethod.ADAPTIVE_ARITHMETIC, flags));
            }
        }
        return compressors;
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
            case NORMAL_3_0:
                buildNormal30Map(map);
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

    /**
     * NORMAL: rANS Nx16 for low-entropy data, GZIP for positional/byte-array data, NameTok for RN, and rANS Nx16
     * order 1 as the first of QS's trial candidates.
     */
    private void buildNormalMap(final EnumMap<DataSeries, CompressorDescriptor> map) {
        final CompressorDescriptor gzip = new CompressorDescriptor(BlockCompressionMethod.GZIP, gzipLevel);
        final CompressorDescriptor ransOrder0 =
                new CompressorDescriptor(BlockCompressionMethod.RANSNx16, RANSNx16Params.ORDER.ZERO.ordinal());
        final CompressorDescriptor ransOrder1 =
                new CompressorDescriptor(BlockCompressionMethod.RANSNx16, RANSNx16Params.ORDER.ONE.ordinal());

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

        map.put(DataSeries.QS_QualityScore, ransOrder1);
        map.put(DataSeries.RN_ReadName, new CompressorDescriptor(BlockCompressionMethod.NAME_TOKENISER));
    }

    /** NORMAL_3_0: htsjdk 4.x's CRAM 3.0 map, rANS 4x8 for low-entropy data series and GZIP for the rest. */
    private void buildNormal30Map(final EnumMap<DataSeries, CompressorDescriptor> map) {
        final CompressorDescriptor gzip = new CompressorDescriptor(BlockCompressionMethod.GZIP, gzipLevel);
        final CompressorDescriptor ransOrder0 =
                new CompressorDescriptor(BlockCompressionMethod.RANS, RANS4x8Params.ORDER.ZERO.ordinal());
        final CompressorDescriptor ransOrder1 =
                new CompressorDescriptor(BlockCompressionMethod.RANS, RANS4x8Params.ORDER.ONE.ordinal());

        for (final DataSeries ds : getWrittenDataSeries()) {
            map.put(ds, gzip);
        }
        map.put(DataSeries.AP_AlignmentPositionOffset, ransOrder0);
        map.put(DataSeries.RI_RefId, ransOrder0);
        map.put(DataSeries.BA_Base, ransOrder1);
        map.put(DataSeries.BF_BitFlags, ransOrder1);
        map.put(DataSeries.CF_CompressionBitFlags, ransOrder1);
        map.put(DataSeries.NS_NextFragmentReferenceSequenceID, ransOrder1);
        map.put(DataSeries.QS_QualityScore, ransOrder1);
        map.put(DataSeries.RG_ReadGroup, ransOrder1);
        map.put(DataSeries.RL_ReadLength, ransOrder1);
        map.put(DataSeries.TS_InsertSize, ransOrder1);
    }

    /** SMALL: as NORMAL, with FQZComp as the first of QS's trial candidates. */
    private void buildSmallMap(final EnumMap<DataSeries, CompressorDescriptor> map) {
        buildNormalMap(map);
        map.put(DataSeries.QS_QualityScore, new CompressorDescriptor(BlockCompressionMethod.FQZCOMP));
    }

    /** ARCHIVE: as SMALL, with read names tokenised using the arithmetic coder, as htslib's archive does. */
    private void buildArchiveMap(final EnumMap<DataSeries, CompressorDescriptor> map) {
        buildSmallMap(map);
        map.put(
                DataSeries.RN_ReadName,
                new CompressorDescriptor(
                        BlockCompressionMethod.NAME_TOKENISER, NameTokeniserExternalCompressor.USE_ARITH));
    }

    /**
     * Build the trial compression candidates map for this profile. The CRAM 3.1 profiles try htslib's candidates on
     * quality scores, and SMALL and ARCHIVE also on most other data series. For data series with trial candidates,
     * the primary compressor (from buildCompressorMap) plus these additional candidates are all tried, and the
     * smallest wins.
     *
     * @return the trial candidates map, or null if this profile doesn't use trial compression
     */
    private EnumMap<DataSeries, java.util.List<CompressorDescriptor>> buildTrialCandidatesMap() {
        if (!cramVersion.equals(CramVersions.CRAM_v3_1)) {
            return null;
        }

        final EnumMap<DataSeries, java.util.List<CompressorDescriptor>> trialMap = new EnumMap<>(DataSeries.class);

        // Quality scores try every compressor htslib does besides the primary
        final List<CompressorDescriptor> qsCandidates = htslibBlockCompressors();
        qsCandidates.remove(buildCompressorMap().get(DataSeries.QS_QualityScore));
        trialMap.put(DataSeries.QS_QualityScore, qsCandidates);

        // BZIP2 as an alternative for general data series
        final CompressorDescriptor bzip2 = new CompressorDescriptor(BlockCompressionMethod.BZIP2);

        // Range (ARITH) coder variants as alternatives to rANS Nx16
        final CompressorDescriptor arithOrder0 =
                new CompressorDescriptor(BlockCompressionMethod.ADAPTIVE_ARITHMETIC, 0);
        final CompressorDescriptor arithOrder1 =
                new CompressorDescriptor(BlockCompressionMethod.ADAPTIVE_ARITHMETIC, RangeParams.ORDER_FLAG_MASK);

        // GZIP as a fallback candidate (may win for small blocks)
        final CompressorDescriptor gzip = new CompressorDescriptor(BlockCompressionMethod.GZIP, gzipLevel);

        if (this == ARCHIVE) {
            // For entropy-rich data series that use rANS Nx16: also try Range coder and BZIP2
            for (final DataSeries ds : new DataSeries[] {
                DataSeries.BA_Base,
                DataSeries.BF_BitFlags,
                DataSeries.CF_CompressionBitFlags,
                DataSeries.NS_NextFragmentReferenceSequenceID,
                DataSeries.RG_ReadGroup,
                DataSeries.RL_ReadLength,
                DataSeries.TS_InsertSize
            }) {
                trialMap.put(ds, java.util.List.of(arithOrder1, bzip2, gzip));
            }
            // Position-like data: also try Range order 0
            for (final DataSeries ds : new DataSeries[] {DataSeries.AP_AlignmentPositionOffset, DataSeries.RI_RefId}) {
                trialMap.put(ds, java.util.List.of(arithOrder0, bzip2, gzip));
            }
            // GZIP-compressed data series: also try BZIP2 and rANS
            final CompressorDescriptor ransOrder1 =
                    new CompressorDescriptor(BlockCompressionMethod.RANSNx16, RANSNx16Params.ORDER.ONE.ordinal());
            for (final DataSeries ds : new DataSeries[] {
                DataSeries.BS_BaseSubstitutionCode,
                DataSeries.DL_DeletionLength,
                DataSeries.FC_FeatureCode,
                DataSeries.FN_NumberOfReadFeatures,
                DataSeries.FP_FeaturePosition,
                DataSeries.HC_HardClip,
                DataSeries.MF_MateBitFlags,
                DataSeries.MQ_MappingQualityScore,
                DataSeries.NF_RecordsToNextFragment,
                DataSeries.NP_NextFragmentAlignmentStart,
                DataSeries.PD_padding,
                DataSeries.RS_RefSkip,
                DataSeries.TL_TagIdList
            }) {
                trialMap.put(ds, java.util.List.of(bzip2, ransOrder1));
            }
        } else if (this == SMALL) {
            // SMALL: same as NORMAL primary codecs but with BZIP2 added to trial candidates.
            // htslib SMALL (level 6, use_rans=1, use_bz2=1) trials GZIP + BZIP2 + all rANS variants.
            // For rANS-primary series: also try BZIP2 and GZIP
            for (final DataSeries ds : new DataSeries[] {
                DataSeries.BA_Base,
                DataSeries.BF_BitFlags,
                DataSeries.CF_CompressionBitFlags,
                DataSeries.NS_NextFragmentReferenceSequenceID,
                DataSeries.RG_ReadGroup,
                DataSeries.RL_ReadLength,
                DataSeries.TS_InsertSize
            }) {
                trialMap.put(ds, java.util.List.of(bzip2, gzip));
            }
            // For rANS Order 0 series: also try BZIP2 and GZIP
            for (final DataSeries ds : new DataSeries[] {DataSeries.AP_AlignmentPositionOffset, DataSeries.RI_RefId}) {
                trialMap.put(ds, java.util.List.of(bzip2, gzip));
            }
            // For GZIP-primary series: also try BZIP2 and rANS
            final CompressorDescriptor ransOrder1 =
                    new CompressorDescriptor(BlockCompressionMethod.RANSNx16, RANSNx16Params.ORDER.ONE.ordinal());
            for (final DataSeries ds : new DataSeries[] {
                DataSeries.BS_BaseSubstitutionCode,
                DataSeries.DL_DeletionLength,
                DataSeries.FC_FeatureCode,
                DataSeries.FN_NumberOfReadFeatures,
                DataSeries.FP_FeaturePosition,
                DataSeries.HC_HardClip,
                DataSeries.MF_MateBitFlags,
                DataSeries.MQ_MappingQualityScore,
                DataSeries.NF_RecordsToNextFragment,
                DataSeries.NP_NextFragmentAlignmentStart,
                DataSeries.PD_padding,
                DataSeries.RS_RefSkip,
                DataSeries.TL_TagIdList,
                DataSeries.IN_Insertion,
                DataSeries.SC_SoftClip
            }) {
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
