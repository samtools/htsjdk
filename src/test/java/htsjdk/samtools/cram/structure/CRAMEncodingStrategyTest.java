package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import java.util.EnumMap;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CRAMEncodingStrategyTest extends HtsjdkTest {

    private static void assertSameCodecs(final CRAMEncodingStrategy actual, final CRAMEncodingStrategy expected) {
        Assert.assertEquals(actual.getCramVersion(), expected.getCramVersion());
        Assert.assertEquals(actual.getCompressorMap(), expected.getCompressorMap());
        Assert.assertEquals(actual.getTrialCandidatesMap(), expected.getTrialCandidatesMap());
        Assert.assertEquals(actual.getTagCompressorCandidates(), expected.getTagCompressorCandidates());
    }

    @Test
    public void defaultStrategyAskedForCram30UsesTheNormal30Codecs() {
        final CRAMEncodingStrategy strategy = new CRAMEncodingStrategy().setCramVersion(CramVersions.CRAM_v3);
        assertSameCodecs(strategy, CRAMCompressionProfile.NORMAL_3_0.toStrategy());
    }

    @Test
    public void normal30StrategyAskedForCram31UsesTheNormalCodecs() {
        final CRAMEncodingStrategy strategy =
                CRAMCompressionProfile.NORMAL_3_0.toStrategy().setCramVersion(CramVersions.CRAM_v3_1);
        assertSameCodecs(strategy, CRAMCompressionProfile.NORMAL.toStrategy());
    }

    @Test
    public void switchingCodecsForTheVersionKeepsOtherSettings() {
        final CRAMEncodingStrategy strategy = new CRAMEncodingStrategy()
                .setReadsPerSlice(1234)
                .setGZIPCompressionLevel(9)
                .setCramVersion(CramVersions.CRAM_v3);
        Assert.assertEquals(strategy.getReadsPerSlice(), 1234);
        Assert.assertEquals(strategy.getGZIPCompressionLevel(), 9);
    }

    @Test
    public void codecsSetByTheCallerAreKeptWhenTheVersionChanges() {
        final EnumMap<DataSeries, CompressorDescriptor> custom =
                new EnumMap<>(new CRAMEncodingStrategy().getCompressorMap());
        custom.put(DataSeries.BF_BitFlags, new CompressorDescriptor(BlockCompressionMethod.GZIP, 1));
        final CRAMEncodingStrategy strategy =
                new CRAMEncodingStrategy().setCompressorMap(custom).setCramVersion(CramVersions.CRAM_v3);
        Assert.assertEquals(strategy.getCompressorMap(), custom);
    }

    @Test
    public void tagCodecsSetByTheCallerAreKeptWhenTheVersionChanges() {
        final List<CompressorDescriptor> tagCodecs = List.of(new CompressorDescriptor(BlockCompressionMethod.GZIP, 1));
        final CRAMEncodingStrategy strategy =
                new CRAMEncodingStrategy().setTagCompressorCandidates(tagCodecs).setCramVersion(CramVersions.CRAM_v3);
        Assert.assertEquals(strategy.getTagCompressorCandidates(), tagCodecs);
    }

    @Test
    public void defaultStrategyAskedForCram30AndBackUsesTheNormalCodecs() {
        final CRAMEncodingStrategy strategy =
                new CRAMEncodingStrategy().setCramVersion(CramVersions.CRAM_v3).setCramVersion(CramVersions.CRAM_v3_1);
        assertSameCodecs(strategy, CRAMCompressionProfile.NORMAL.toStrategy());
    }

    @Test
    public void trialCandidatesSetByTheCallerAreKeptWhenTheVersionChanges() {
        final EnumMap<DataSeries, List<CompressorDescriptor>> trials = new EnumMap<>(DataSeries.class);
        trials.put(DataSeries.QS_QualityScore, List.of(new CompressorDescriptor(BlockCompressionMethod.GZIP, 1)));
        final CRAMEncodingStrategy strategy =
                new CRAMEncodingStrategy().setTrialCandidatesMap(trials).setCramVersion(CramVersions.CRAM_v3);
        Assert.assertEquals(strategy.getTrialCandidatesMap(), trials);
    }

    @Test
    public void changingTheReturnedCompressorMapDoesNotChangeTheStrategy() {
        final CRAMEncodingStrategy strategy = new CRAMEncodingStrategy();
        strategy.getCompressorMap()
                .put(DataSeries.BF_BitFlags, new CompressorDescriptor(BlockCompressionMethod.GZIP, 1));
        Assert.assertEquals(
                strategy.getCompressorMap(),
                CRAMCompressionProfile.NORMAL.toStrategy().getCompressorMap());
    }

    @Test
    public void aProfileWithoutACram30CounterpartKeepsItsCodecs() {
        final CRAMEncodingStrategy strategy =
                CRAMCompressionProfile.ARCHIVE.toStrategy().setCramVersion(CramVersions.CRAM_v3);
        Assert.assertEquals(
                strategy.getCompressorMap(),
                CRAMCompressionProfile.ARCHIVE.toStrategy().getCompressorMap());
    }
}
