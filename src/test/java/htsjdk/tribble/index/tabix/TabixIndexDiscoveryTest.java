package htsjdk.tribble.index.tabix;

import htsjdk.HtsjdkTest;
import htsjdk.beta.plugin.variants.VariantsBundle;
import htsjdk.io.HtsPath;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.TabixReader;
import htsjdk.tribble.util.TabixUtils;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.testng.Assert;
import org.testng.annotations.Test;

/** How a tabix index is found when none is named: {@code .csi} first, then {@code .tbi}, as htslib does. */
public class TabixIndexDiscoveryTest extends HtsjdkTest {

    /** A VCF with a CSI index; the caller then arranges the index files it wants. */
    private static Path vcfWithCsi() throws IOException {
        return TestVcfs.write(
                TestVcfs.tempDir("TabixIndexDiscoveryTest"),
                TestVcfs.LARGE_CONTIG_DICTIONARY,
                new VariantContextWriterBuilder()
                        .setOption(Options.INDEX_ON_THE_FLY)
                        .setTabixIndexType(TabixIndexType.CSI),
                "small");
    }

    private static Path sibling(final Path vcf, final String extension) {
        return vcf.resolveSibling(vcf.getFileName() + extension);
    }

    @Test
    public void testCsiIsFoundWhenItIsTheOnlyIndex() throws IOException {
        final Path vcf = vcfWithCsi();
        Assert.assertEquals(
                TabixUtils.findIndex(vcf.toString()),
                sibling(vcf, FileExtensions.CSI).toString());
        Assert.assertTrue(AbstractFeatureReader.isTabix(vcf.toString(), null));
        Assert.assertEquals(
                TestVcfs.queryStarts(vcf, "small", 1, 1_000), TestVcfs.SMALL_CONTIG.startsOverlapping(1, 1_000));
    }

    @Test
    public void testTbiIsFoundWhenItIsTheOnlyIndex() throws IOException {
        final Path vcf = vcfWithCsi();
        Files.move(sibling(vcf, FileExtensions.CSI), sibling(vcf, FileExtensions.TABIX_INDEX));
        Assert.assertEquals(
                TabixUtils.findIndex(vcf.toString()),
                sibling(vcf, FileExtensions.TABIX_INDEX).toString());
        // The file is a CSI under a .tbi name; the reader goes by the magic number, not the name.
        Assert.assertEquals(
                TestVcfs.queryStarts(vcf, "small", 1, 1_000), TestVcfs.SMALL_CONTIG.startsOverlapping(1, 1_000));
    }

    @Test
    public void testCsiIsPreferredWhenBothExist() throws IOException {
        final Path vcf = vcfWithCsi();
        Files.write(sibling(vcf, FileExtensions.TABIX_INDEX), "not an index".getBytes());
        Assert.assertEquals(
                TabixUtils.findIndex(vcf.toString()),
                sibling(vcf, FileExtensions.CSI).toString());
        Assert.assertEquals(
                TestVcfs.queryStarts(vcf, "small", 1, 1_000), TestVcfs.SMALL_CONTIG.startsOverlapping(1, 1_000));
    }

    @Test
    public void testNoIndexIsReportedNamingBothCandidates() throws IOException {
        final Path vcf = vcfWithCsi();
        Files.delete(sibling(vcf, FileExtensions.CSI));
        Assert.assertNull(TabixUtils.findIndex(vcf.toString()));
        Assert.assertFalse(AbstractFeatureReader.isTabix(vcf.toString(), null));
        try (final TabixReader reader = new TabixReader(vcf.toString())) {
            Assert.fail("expected no index to be found");
        } catch (final TribbleException e) {
            Assert.assertTrue(e.getMessage().contains(".csi") && e.getMessage().contains(".tbi"), e.getMessage());
        }
    }

    @Test
    public void testExplicitlyNamedIndexIsUsedAsIs() throws IOException {
        final Path vcf = vcfWithCsi();
        final Path elsewhere = vcf.resolveSibling("elsewhere.index");
        Files.move(sibling(vcf, FileExtensions.CSI), elsewhere, StandardCopyOption.REPLACE_EXISTING);
        try (final TabixReader reader = new TabixReader(vcf.toString(), elsewhere.toString())) {
            Assert.assertTrue(reader.getChromosomes().contains("small"));
        }
    }

    @Test
    public void testVariantsBundleResolvesACsiIndex() throws IOException {
        final Path vcf = vcfWithCsi();
        Assert.assertEquals(
                VariantsBundle.resolveIndex(new HtsPath(vcf.toString()))
                        .orElseThrow()
                        .toPath(),
                sibling(vcf, FileExtensions.CSI));
    }

    @Test
    public void testPlainGzipWithAnIndexBesideItIsRejectedClearly() throws IOException {
        final Path vcf = vcfWithCsi();
        final Path gzip = vcf.resolveSibling("plain.vcf.gz");
        try (final java.util.zip.GZIPOutputStream out =
                        new java.util.zip.GZIPOutputStream(Files.newOutputStream(gzip));
                final java.io.InputStream in = new htsjdk.samtools.util.BlockCompressedInputStream(vcf)) {
            in.transferTo(out);
        }
        Files.copy(sibling(vcf, FileExtensions.CSI), sibling(gzip, FileExtensions.CSI));
        try (final TabixReader reader = new TabixReader(gzip.toString())) {
            Assert.fail("expected plain gzip to be rejected");
        } catch (final TribbleException e) {
            Assert.assertTrue(e.getMessage().contains("BGZF"), e.getMessage());
        }
    }
}
