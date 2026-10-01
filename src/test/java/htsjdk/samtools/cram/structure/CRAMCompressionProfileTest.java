package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokeniserExternalCompressor;
import htsjdk.samtools.cram.compression.rans.RANSNx16Params;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for {@link CRAMCompressionProfile} and its integration with {@link CRAMEncodingStrategy}.
 */
public class CRAMCompressionProfileTest extends HtsjdkTest {

    @DataProvider(name = "profiles")
    public Object[][] getProfiles() {
        return new Object[][] {
            {CRAMCompressionProfile.FAST},
            {CRAMCompressionProfile.NORMAL},
            {CRAMCompressionProfile.SMALL},
            {CRAMCompressionProfile.ARCHIVE},
            {CRAMCompressionProfile.NORMAL_3_0},
        };
    }

    @Test(dataProvider = "profiles")
    public void testToStrategyProducesNonNullStrategy(final CRAMCompressionProfile profile) {
        final CRAMEncodingStrategy strategy = profile.toStrategy();
        Assert.assertNotNull(strategy);
        Assert.assertNotNull(strategy.getCompressorMap());
        Assert.assertNotNull(strategy.getCramVersion());
    }

    @Test(dataProvider = "profiles")
    public void testCompressorMapCoversAllWrittenDataSeries(final CRAMCompressionProfile profile) {
        final EnumMap<DataSeries, CompressorDescriptor> map =
                profile.toStrategy().getCompressorMap();

        // All profiles should have entries for QS, RN, BA, and other written series
        Assert.assertNotNull(map.get(DataSeries.QS_QualityScore), "QS must be in compressor map");
        Assert.assertNotNull(map.get(DataSeries.RN_ReadName), "RN must be in compressor map");
        Assert.assertNotNull(map.get(DataSeries.BA_Base), "BA must be in compressor map");
        Assert.assertNotNull(map.get(DataSeries.BF_BitFlags), "BF must be in compressor map");
        Assert.assertNotNull(map.get(DataSeries.RL_ReadLength), "RL must be in compressor map");
        Assert.assertNotNull(map.get(DataSeries.AP_AlignmentPositionOffset), "AP must be in compressor map");
    }

    @Test
    public void testFastProfile() {
        final CRAMEncodingStrategy strategy = CRAMCompressionProfile.FAST.toStrategy();
        Assert.assertEquals(strategy.getCramVersion(), CramVersions.CRAM_v3);
        Assert.assertEquals(strategy.getGZIPCompressionLevel(), 1);
        Assert.assertEquals(strategy.getReadsPerSlice(), 10_000);

        // FAST should use GZIP for everything
        final EnumMap<DataSeries, CompressorDescriptor> map = strategy.getCompressorMap();
        for (final CompressorDescriptor desc : map.values()) {
            Assert.assertEquals(
                    desc.method(), BlockCompressionMethod.GZIP, "FAST profile should use GZIP for all data series");
        }
    }

    @Test
    public void testNormalProfile() {
        final CRAMEncodingStrategy strategy = CRAMCompressionProfile.NORMAL.toStrategy();
        Assert.assertEquals(strategy.getCramVersion(), CramVersions.CRAM_v3_1);
        Assert.assertEquals(strategy.getGZIPCompressionLevel(), 5);
        Assert.assertEquals(strategy.getReadsPerSlice(), 10_000);

        final EnumMap<DataSeries, CompressorDescriptor> map = strategy.getCompressorMap();
        Assert.assertEquals(map.get(DataSeries.QS_QualityScore), rans(RANSNx16Params.ORDER_FLAG_MASK));
        Assert.assertEquals(map.get(DataSeries.RN_ReadName).method(), BlockCompressionMethod.NAME_TOKENISER);
        Assert.assertEquals(map.get(DataSeries.BA_Base).method(), BlockCompressionMethod.RANSNx16);
        Assert.assertEquals(map.get(DataSeries.AP_AlignmentPositionOffset).method(), BlockCompressionMethod.RANSNx16);
        Assert.assertEquals(map.get(DataSeries.MQ_MappingQualityScore).method(), BlockCompressionMethod.RANSNx16);
        // NP and FP stay GZIP — rANS performs poorly on high-entropy positional data
        Assert.assertEquals(map.get(DataSeries.NP_NextFragmentAlignmentStart).method(), BlockCompressionMethod.GZIP);
        Assert.assertEquals(map.get(DataSeries.FP_FeaturePosition).method(), BlockCompressionMethod.GZIP);
    }

    @Test
    public void testSmallProfile() {
        final CRAMEncodingStrategy strategy = CRAMCompressionProfile.SMALL.toStrategy();
        Assert.assertEquals(strategy.getCramVersion(), CramVersions.CRAM_v3_1);
        Assert.assertEquals(strategy.getGZIPCompressionLevel(), 6);
        Assert.assertEquals(strategy.getReadsPerSlice(), 25_000);

        // SMALL uses the same primary codecs as NORMAL but FQZComp for QS, with BZIP2 added via trial compression
        final EnumMap<DataSeries, CompressorDescriptor> map = strategy.getCompressorMap();
        Assert.assertEquals(map.get(DataSeries.QS_QualityScore).method(), BlockCompressionMethod.FQZCOMP);
        Assert.assertEquals(map.get(DataSeries.RN_ReadName).method(), BlockCompressionMethod.NAME_TOKENISER);
        Assert.assertEquals(map.get(DataSeries.BA_Base).method(), BlockCompressionMethod.RANSNx16);
    }

    @Test
    public void testArchiveProfile() {
        final CRAMEncodingStrategy strategy = CRAMCompressionProfile.ARCHIVE.toStrategy();
        Assert.assertEquals(strategy.getCramVersion(), CramVersions.CRAM_v3_1);
        Assert.assertEquals(strategy.getGZIPCompressionLevel(), 7);
        Assert.assertEquals(strategy.getReadsPerSlice(), 100_000);

        final EnumMap<DataSeries, CompressorDescriptor> map = strategy.getCompressorMap();
        Assert.assertEquals(map.get(DataSeries.QS_QualityScore).method(), BlockCompressionMethod.FQZCOMP);
        Assert.assertEquals(
                map.get(DataSeries.RN_ReadName),
                new CompressorDescriptor(
                        BlockCompressionMethod.NAME_TOKENISER, NameTokeniserExternalCompressor.USE_ARITH));
        Assert.assertEquals(map.get(DataSeries.BA_Base).method(), BlockCompressionMethod.RANSNx16);
    }

    @Test
    public void testDefaultStrategyIsNormalProfile() {
        final CRAMEncodingStrategy defaultStrategy = new CRAMEncodingStrategy();
        final CRAMEncodingStrategy normalStrategy = CRAMCompressionProfile.NORMAL.toStrategy();

        Assert.assertEquals(defaultStrategy.getCramVersion(), normalStrategy.getCramVersion());
        Assert.assertEquals(defaultStrategy.getGZIPCompressionLevel(), normalStrategy.getGZIPCompressionLevel());
        Assert.assertEquals(defaultStrategy.getReadsPerSlice(), normalStrategy.getReadsPerSlice());
        Assert.assertEquals(defaultStrategy.getCompressorMap(), normalStrategy.getCompressorMap());
    }

    @Test
    public void testApplyToModifiesExistingStrategy() {
        final CRAMEncodingStrategy strategy = new CRAMEncodingStrategy();
        // Starts as NORMAL
        Assert.assertEquals(strategy.getCramVersion(), CramVersions.CRAM_v3_1);

        // Apply FAST
        CRAMCompressionProfile.FAST.applyTo(strategy);
        Assert.assertEquals(strategy.getCramVersion(), CramVersions.CRAM_v3);
        Assert.assertEquals(strategy.getGZIPCompressionLevel(), 1);
    }

    @Test
    public void testStrategyOverridesAfterProfile() {
        // Apply ARCHIVE profile then override reads-per-slice
        final CRAMEncodingStrategy strategy = CRAMCompressionProfile.ARCHIVE.toStrategy();
        strategy.setReadsPerSlice(50_000);
        Assert.assertEquals(strategy.getReadsPerSlice(), 50_000);
        // Other ARCHIVE settings should still be in effect
        Assert.assertEquals(strategy.getCramVersion(), CramVersions.CRAM_v3_1);
        Assert.assertEquals(strategy.getGZIPCompressionLevel(), 7);
    }

    @Test
    public void testArchiveHasTrialCandidates() {
        final CRAMEncodingStrategy strategy = CRAMCompressionProfile.ARCHIVE.toStrategy();
        final EnumMap<DataSeries, java.util.List<CompressorDescriptor>> trialMap = strategy.getTrialCandidatesMap();
        Assert.assertNotNull(trialMap, "ARCHIVE profile should have trial candidates");
        Assert.assertFalse(trialMap.isEmpty(), "ARCHIVE trial map should not be empty");

        // BA_Base should have trial candidates including ARITH and BZIP2
        Assert.assertTrue(trialMap.containsKey(DataSeries.BA_Base), "BA_Base should have trial candidates in ARCHIVE");
        final java.util.List<CompressorDescriptor> baCandidates = trialMap.get(DataSeries.BA_Base);
        Assert.assertTrue(
                baCandidates.stream().anyMatch(d -> d.method() == BlockCompressionMethod.ADAPTIVE_ARITHMETIC),
                "ARCHIVE BA_Base trial candidates should include Range (ARITH) coder");
        Assert.assertTrue(
                baCandidates.stream().anyMatch(d -> d.method() == BlockCompressionMethod.BZIP2),
                "ARCHIVE BA_Base trial candidates should include BZIP2");
    }

    @Test
    public void testSmallHasTrialCandidates() {
        final CRAMEncodingStrategy strategy = CRAMCompressionProfile.SMALL.toStrategy();
        final EnumMap<DataSeries, java.util.List<CompressorDescriptor>> trialMap = strategy.getTrialCandidatesMap();
        Assert.assertNotNull(trialMap, "SMALL profile should have trial candidates");

        // SMALL should have BZIP2 as trial candidate for general data series
        Assert.assertTrue(trialMap.containsKey(DataSeries.BA_Base), "BA_Base should have trial candidates in SMALL");
    }

    @Test
    public void testFastHasNoTrialCandidates() {
        Assert.assertNull(
                CRAMCompressionProfile.FAST.toStrategy().getTrialCandidatesMap(),
                "FAST should not have trial candidates");
    }

    private static CompressorDescriptor rans(final int flags) {
        return new CompressorDescriptor(BlockCompressionMethod.RANSNx16, flags);
    }

    private static CompressorDescriptor range(final int flags) {
        return new CompressorDescriptor(BlockCompressionMethod.ADAPTIVE_ARITHMETIC, flags);
    }

    /** The rANS Nx16 flag combinations htslib tries on a block at level 5 and below: o0, o1, RLE, STRIPE, PACK. */
    private static List<CompressorDescriptor> htslibRansUpToLevel5() {
        return List.of(rans(0), rans(1), rans(64), rans(9), rans(128), rans(193));
    }

    /** Every compressor a profile tries on quality scores: its primary and its trial candidates. */
    private static Set<CompressorDescriptor> qualityScoreCompressors(final CRAMCompressionProfile profile) {
        final CRAMEncodingStrategy strategy = profile.toStrategy();
        final Set<CompressorDescriptor> compressors = new HashSet<>();
        compressors.add(strategy.getCompressorMap().get(DataSeries.QS_QualityScore));
        compressors.addAll(strategy.getTrialCandidatesMap().get(DataSeries.QS_QualityScore));
        return compressors;
    }

    private static Set<CompressorDescriptor> normalCompressors() {
        final Set<CompressorDescriptor> compressors = new HashSet<>(htslibRansUpToLevel5());
        compressors.add(new CompressorDescriptor(BlockCompressionMethod.GZIP, 5));
        return compressors;
    }

    private static Set<CompressorDescriptor> smallCompressors() {
        final Set<CompressorDescriptor> compressors = new HashSet<>(htslibRansUpToLevel5());
        compressors.addAll(List.of(
                rans(129),
                rans(192),
                new CompressorDescriptor(BlockCompressionMethod.GZIP, 6),
                new CompressorDescriptor(BlockCompressionMethod.BZIP2)));
        return compressors;
    }

    private static Set<CompressorDescriptor> archiveCompressors() {
        final Set<CompressorDescriptor> compressors = new HashSet<>(htslibRansUpToLevel5());
        compressors.addAll(List.of(
                rans(129),
                rans(192),
                new CompressorDescriptor(BlockCompressionMethod.GZIP, 7),
                new CompressorDescriptor(BlockCompressionMethod.BZIP2)));
        for (final int flags : new int[] {0, 1, 64, 9, 128, 129, 192, 193}) {
            compressors.add(range(flags));
        }
        return compressors;
    }

    @Test
    public void normalTriesHtslibNormalsCompressorsOnQualityScores() {
        Assert.assertEquals(qualityScoreCompressors(CRAMCompressionProfile.NORMAL), normalCompressors());
    }

    @Test
    public void smallTriesHtslibSmallsCompressorsAndFqzcompOnQualityScores() {
        final Set<CompressorDescriptor> expected = smallCompressors();
        expected.add(new CompressorDescriptor(BlockCompressionMethod.FQZCOMP));
        Assert.assertEquals(qualityScoreCompressors(CRAMCompressionProfile.SMALL), expected);
    }

    @Test
    public void archiveTriesHtslibArchivesCompressorsAndFqzcompOnQualityScores() {
        final Set<CompressorDescriptor> expected = archiveCompressors();
        expected.add(new CompressorDescriptor(BlockCompressionMethod.FQZCOMP));
        Assert.assertEquals(qualityScoreCompressors(CRAMCompressionProfile.ARCHIVE), expected);
    }

    @Test
    public void normalTriesHtslibNormalsCompressorsOnTags() {
        Assert.assertEquals(
                new HashSet<>(CRAMCompressionProfile.NORMAL.toStrategy().getTagCompressorCandidates()),
                normalCompressors());
    }

    @Test
    public void smallTriesHtslibSmallsCompressorsOnTags() {
        Assert.assertEquals(
                new HashSet<>(CRAMCompressionProfile.SMALL.toStrategy().getTagCompressorCandidates()),
                smallCompressors());
    }

    @Test
    public void archiveTriesHtslibArchivesCompressorsOnTags() {
        Assert.assertEquals(
                new HashSet<>(CRAMCompressionProfile.ARCHIVE.toStrategy().getTagCompressorCandidates()),
                archiveCompressors());
    }

    @Test(dataProvider = "profiles")
    public void everyProfileUsesOnlyCodecsItsCramVersionSpecifies(final CRAMCompressionProfile profile) {
        final CRAMEncodingStrategy strategy = profile.toStrategy();
        final List<CompressorDescriptor> descriptors =
                new java.util.ArrayList<>(strategy.getCompressorMap().values());
        if (strategy.getTrialCandidatesMap() != null) {
            strategy.getTrialCandidatesMap().values().forEach(descriptors::addAll);
        }
        descriptors.addAll(strategy.getTagCompressorCandidates());
        for (final CompressorDescriptor descriptor : descriptors) {
            Assert.assertTrue(
                    descriptor.method().isAvailableIn(strategy.getCramVersion()),
                    profile + " uses " + descriptor.method() + ", which CRAM " + strategy.getCramVersion()
                            + " does not specify");
        }
    }

    @Test
    public void testNormal30Profile() {
        final CRAMEncodingStrategy strategy = CRAMCompressionProfile.NORMAL_3_0.toStrategy();
        Assert.assertEquals(strategy.getCramVersion(), CramVersions.CRAM_v3);
        Assert.assertEquals(strategy.getGZIPCompressionLevel(), 5);
        Assert.assertEquals(strategy.getReadsPerSlice(), 10_000);
        Assert.assertNull(strategy.getTrialCandidatesMap());
    }

    @Test
    public void normal30IsFoundByName() {
        Assert.assertEquals(
                CRAMCompressionProfile.valueOfCaseInsensitive("normal_3_0"), CRAMCompressionProfile.NORMAL_3_0);
    }

    @Test
    public void strategyRefusesToWriteCram21() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new CRAMEncodingStrategy()
                .setCramVersion(CramVersions.CRAM_v2_1));
    }

    @Test
    public void nameTokeniserIsNotATagCompressor() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new CRAMEncodingStrategy()
                .setTagCompressorCandidates(List.of(new CompressorDescriptor(BlockCompressionMethod.NAME_TOKENISER))));
    }

    @Test
    public void fqzcompIsNotATagCompressor() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new CRAMEncodingStrategy()
                .setTagCompressorCandidates(List.of(new CompressorDescriptor(BlockCompressionMethod.FQZCOMP))));
    }
}
