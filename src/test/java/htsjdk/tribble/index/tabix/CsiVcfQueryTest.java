package htsjdk.tribble.index.tabix;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Writing a CSI-indexed VCF through {@link VariantContextWriterBuilder} and querying it back. */
public class CsiVcfQueryTest extends HtsjdkTest {

    private static VariantContextWriterBuilder csiBuilder() {
        return new VariantContextWriterBuilder()
                .setOption(Options.INDEX_ON_THE_FLY)
                .setTabixIndexType(TabixIndexType.CSI);
    }

    private static Path writeLargeContigVcf() throws IOException {
        return TestVcfs.write(
                TestVcfs.tempDir("CsiVcfQueryTest"), TestVcfs.LARGE_CONTIG_DICTIONARY, csiBuilder(), "small", "large");
    }

    private static void assertQueryMatchesLayout(
            final Path vcf, final String contig, final TestVcfs.Layout layout, final int start, final int end) {
        Assert.assertEquals(
                TestVcfs.queryStarts(vcf, contig, start, end),
                layout.startsOverlapping(start, end),
                contig + ":" + start + "-" + end);
    }

    @Test
    public void testCsiIndexIsWrittenBesideTheVcf() throws IOException {
        final Path vcf = writeLargeContigVcf();
        Assert.assertTrue(Files.exists(vcf.resolveSibling(vcf.getFileName() + FileExtensions.CSI)));
        Assert.assertFalse(Files.exists(vcf.resolveSibling(vcf.getFileName() + FileExtensions.TABIX_INDEX)));
    }

    @Test
    public void testQueriesBeyondTheTbiLimitReturnTheRightRecords() throws IOException {
        final Path vcf = writeLargeContigVcf();
        for (final int[] region : new int[][] {
            {536_870_000, 536_872_000}, // straddles 2^29
            {600_000_000, 600_100_000},
            {829_000_000, 830_000_000},
            {700_000_000, 700_000_010}, // nothing there
        }) {
            assertQueryMatchesLayout(vcf, "large", TestVcfs.LARGE_CONTIG, region[0], region[1]);
        }
    }

    @Test
    public void testWholeContigQueryReturnsEveryRecord() throws IOException {
        final Path vcf = writeLargeContigVcf();
        assertQueryMatchesLayout(vcf, "large", TestVcfs.LARGE_CONTIG, 1, Integer.MAX_VALUE);
    }

    @Test
    public void testWideQueryAcrossManyBinsReturnsTheRightRecords() throws IOException {
        final Path vcf = writeLargeContigVcf();
        assertQueryMatchesLayout(vcf, "large", TestVcfs.LARGE_CONTIG, 100_000_000, 700_000_000);
    }

    @Test
    public void testQueriesOnTheSmallContigStillWork() throws IOException {
        final Path vcf = writeLargeContigVcf();
        assertQueryMatchesLayout(vcf, "small", TestVcfs.SMALL_CONTIG, 1, 1_000);
        assertQueryMatchesLayout(vcf, "small", TestVcfs.SMALL_CONTIG, 9_999_000, 30_000_000);
        assertQueryMatchesLayout(vcf, "small", TestVcfs.SMALL_CONTIG, 49_000_000, 60_000_000);
    }

    @Test
    public void testTbiAndCsiIndexesOfTheSameFileAgree() throws IOException {
        final Path dir = TestVcfs.tempDir("CsiVcfQueryTest");
        final Path vcf = TestVcfs.write(
                dir,
                TestVcfs.LARGE_CONTIG_DICTIONARY,
                new VariantContextWriterBuilder().setOption(Options.INDEX_ON_THE_FLY),
                "small");
        final Path tbi = vcf.resolveSibling(vcf.getFileName() + FileExtensions.TABIX_INDEX);
        final Path csi = vcf.resolveSibling(vcf.getFileName() + FileExtensions.CSI);
        Assert.assertTrue(Files.exists(tbi));
        final List<List<Integer>> viaTbi = List.of(
                TestVcfs.queryStarts(vcf, "small", 1, 1_000),
                TestVcfs.queryStarts(vcf, "small", 9_999_000, 30_000_000),
                TestVcfs.queryStarts(vcf, "small", 41_000_000, 41_000_500));
        // Re-index the same file as CSI and remove the TBI, so the CSI is what gets used.
        IndexFactory.createTabixIndex(vcf, new htsjdk.variant.vcf.VCFCodec(), TabixFormat.VCF, null, TabixIndexType.CSI)
                .write(csi);
        Files.delete(tbi);
        Assert.assertEquals(TestVcfs.queryStarts(vcf, "small", 1, 1_000), viaTbi.get(0));
        Assert.assertEquals(TestVcfs.queryStarts(vcf, "small", 9_999_000, 30_000_000), viaTbi.get(1));
        Assert.assertEquals(TestVcfs.queryStarts(vcf, "small", 41_000_000, 41_000_500), viaTbi.get(2));
    }

    @Test
    public void testDefaultIndexIsStillTbi() throws IOException {
        final Path vcf = TestVcfs.write(
                TestVcfs.tempDir("CsiVcfQueryTest"),
                TestVcfs.LARGE_CONTIG_DICTIONARY,
                new VariantContextWriterBuilder().setOption(Options.INDEX_ON_THE_FLY),
                "small");
        Assert.assertTrue(Files.exists(vcf.resolveSibling(vcf.getFileName() + FileExtensions.TABIX_INDEX)));
        Assert.assertFalse(Files.exists(vcf.resolveSibling(vcf.getFileName() + FileExtensions.CSI)));
    }

    @Test
    public void testTbiIndexingARecordBeyondItsReachSaysToUseCsi() throws IOException {
        try {
            TestVcfs.write(
                    TestVcfs.tempDir("CsiVcfQueryTest"),
                    TestVcfs.LARGE_CONTIG_DICTIONARY,
                    new VariantContextWriterBuilder().setOption(Options.INDEX_ON_THE_FLY),
                    "large");
            Assert.fail("expected the TBI index to reject a record beyond 2^29");
        } catch (final IllegalArgumentException | TribbleException e) {
            Assert.assertTrue(e.getMessage().contains("CSI"), e.getMessage());
        }
    }

    @Test
    public void testDictionaryDecidesTheCsiBinningScheme() throws IOException {
        final Path vcf = writeLargeContigVcf();
        final TabixIndex index = new TabixIndex(vcf.resolveSibling(vcf.getFileName() + FileExtensions.CSI));
        Assert.assertEquals(index.getIndexType(), TabixIndexType.CSI);
        Assert.assertEquals(index.getBinningIndex().getMinShift(), 14);
        Assert.assertEquals(index.getBinningIndex().getDepth(), 6);
    }

    @Test
    public void testMinShiftIsHonoured() throws IOException {
        final Path vcf = TestVcfs.write(
                TestVcfs.tempDir("CsiVcfQueryTest"),
                TestVcfs.LARGE_CONTIG_DICTIONARY,
                csiBuilder().setCsiMinShift(12),
                "small",
                "large");
        final TabixIndex index = new TabixIndex(vcf.resolveSibling(vcf.getFileName() + FileExtensions.CSI));
        Assert.assertEquals(index.getBinningIndex().getMinShift(), 12);
        assertQueryMatchesLayout(vcf, "large", TestVcfs.LARGE_CONTIG, 600_000_000, 600_100_000);
    }

    @Test
    public void testUserSuppliedIndexCreatorIsUsedForBlockCompressedOutput() throws IOException {
        final Path dir = TestVcfs.tempDir("CsiVcfQueryTest");
        final Path elsewhere = dir.resolve("elsewhere.csi");
        try (final java.io.OutputStream indexOut = Files.newOutputStream(elsewhere)) {
            TestVcfs.write(
                    dir,
                    TestVcfs.LARGE_CONTIG_DICTIONARY,
                    new VariantContextWriterBuilder()
                            .setOption(Options.INDEX_ON_THE_FLY)
                            .setIndexCreator(new StreamBasedTabixIndexCreator(
                                    TestVcfs.LARGE_CONTIG_DICTIONARY, TabixFormat.VCF, TabixIndexType.CSI, indexOut)),
                    "small");
        }
        Assert.assertEquals(new TabixIndex(elsewhere).getIndexType(), TabixIndexType.CSI);
        Assert.assertFalse(Files.exists(dir.resolve("records.vcf.gz" + FileExtensions.TABIX_INDEX)));
    }
}
