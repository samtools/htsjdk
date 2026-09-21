package htsjdk.variant.bcf2;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.vcf.VCFContigHeaderLine;
import htsjdk.variant.vcf.VCFFilterHeaderLine;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderVersion;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import java.util.LinkedHashSet;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BCFDictionaryTest extends VariantBaseTest {

    // -- FILTER, INFO and FORMAT --

    @Test
    public void passIsIndexZeroWhetherOrNotTheHeaderDeclaresIt() {
        Assert.assertEquals(BCFDictionary.forIDs(header(info("DP"))).getString(0), "PASS");
        Assert.assertEquals(
                BCFDictionary.forIDs(header(info("DP"), filter("PASS"))).getString(0), "PASS");
        Assert.assertEquals(
                BCFDictionary.forIDs(header(info("DP"), filter("PASS", 0))).getString(0), "PASS");
    }

    @Test
    public void linesWithoutIdxAreNumberedInHeaderOrderAfterPass() {
        final BCFDictionary dictionary = BCFDictionary.forIDs(header(info("DP"), filter("q10"), format("GT")));
        Assert.assertEquals(dictionary.getString(1), "DP");
        Assert.assertEquals(dictionary.getString(2), "q10");
        Assert.assertEquals(dictionary.getString(3), "GT");
        Assert.assertEquals(dictionary.size(), 4);
    }

    @Test
    public void idxAttributesAreHonoured() {
        final BCFDictionary dictionary =
                BCFDictionary.forIDs(header(filter("PASS", 0), format("GT", 4), info("DP", 1), info("AF", 2)));
        Assert.assertEquals(dictionary.getString(1), "DP");
        Assert.assertEquals(dictionary.getString(2), "AF");
        Assert.assertEquals(dictionary.getString(4), "GT");
    }

    @Test
    public void aGapBetweenIdxValuesIsAnErrorOnLookup() {
        final BCFDictionary dictionary = BCFDictionary.forIDs(header(info("DP", 1), info("AF", 4)));
        Assert.assertEquals(dictionary.size(), 5);
        Assert.assertEquals(dictionary.getString(4), "AF");
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> dictionary.getString(3));
        Assert.assertTrue(e.getMessage().contains("index 3"), e.getMessage());
    }

    @Test
    public void anIndexPastTheEndIsAnError() {
        final BCFDictionary dictionary = BCFDictionary.forIDs(header(info("DP")));
        Assert.expectThrows(TribbleException.class, () -> dictionary.getString(2));
        Assert.expectThrows(TribbleException.class, () -> dictionary.getString(-1));
    }

    @Test
    public void aLineWithoutIdxAmongLinesWithIdxTakesTheNextIndexAfterTheHighest() {
        final BCFDictionary dictionary = BCFDictionary.forIDs(header(info("DP", 1), info("AF", 4), info("NEW")));
        Assert.assertEquals(dictionary.getString(5), "NEW");
        Assert.assertEquals(dictionary.size(), 6);
    }

    @Test
    public void infoAndFormatLinesWithTheSameIdShareOneIndex() {
        final BCFDictionary dictionary = BCFDictionary.forIDs(header(info("DP", 2), format("GT", 1), format("DP", 2)));
        Assert.assertEquals(dictionary.getString(1), "GT");
        Assert.assertEquals(dictionary.getString(2), "DP");
        Assert.assertEquals(dictionary.size(), 3);
        final BCFDictionary inOrder = BCFDictionary.forIDs(header(info("DP"), format("GT"), format("DP")));
        Assert.assertEquals(inOrder.getString(1), "DP");
        Assert.assertEquals(inOrder.getString(2), "GT");
        Assert.assertEquals(inOrder.size(), 3);
    }

    @Test
    public void twoIdsWithTheSameIdxAreAConflict() {
        final TribbleException e = Assert.expectThrows(
                TribbleException.class, () -> BCFDictionary.forIDs(header(info("DP", 1), info("AF", 1))));
        Assert.assertTrue(e.getMessage().contains("IDX=1"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("DP") && e.getMessage().contains("AF"), e.getMessage());
    }

    @Test
    public void anIdxOfZeroOnALineOtherThanPassIsAConflict() {
        Assert.expectThrows(TribbleException.class, () -> BCFDictionary.forIDs(header(info("DP", 0))));
    }

    @Test
    public void aNonNumericIdxIsAnError() {
        final VCFInfoHeaderLine line =
                new VCFInfoHeaderLine("<ID=DP,Number=1,Type=Integer,Description=\"d\",IDX=x>", VCFHeaderVersion.VCF4_2);
        final TribbleException e =
                Assert.expectThrows(TribbleException.class, () -> BCFDictionary.forIDs(header(line)));
        Assert.assertTrue(e.getMessage().contains("DP") && e.getMessage().contains("x"), e.getMessage());
    }

    @Test
    public void aNegativeIdxIsAnError() {
        Assert.expectThrows(TribbleException.class, () -> BCFDictionary.forIDs(header(info("DP", -1))));
    }

    @Test
    public void anIdxAtOrAboveTheMaximumIsRejected() {
        final TribbleException e = Assert.expectThrows(
                TribbleException.class, () -> BCFDictionary.forIDs(header(info("DP", 2_000_000_000))));
        Assert.assertTrue(e.getMessage().contains("2000000000"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("DP"), e.getMessage());

        Assert.expectThrows(
                TribbleException.class, () -> BCFDictionary.forIDs(header(info("AF", BCFDictionary.MAX_IDX))));
    }

    // IDX=100000 is well below the maximum and verifiable without allocating 16M entries
    @Test
    public void aModeratelyLargeSparseIdxIsAccepted() {
        final BCFDictionary dictionary = BCFDictionary.forIDs(header(info("DP", 100_000)));
        Assert.assertEquals(dictionary.getString(100_000), "DP");
    }

    // -- Contigs --

    @Test
    public void contigsWithoutIdxAreNumberedInHeaderOrder() {
        final BCFDictionary dictionary = BCFDictionary.forContigs(header(contig("chr1"), contig("chr2")));
        Assert.assertEquals(dictionary.getString(0), "chr1");
        Assert.assertEquals(dictionary.getString(1), "chr2");
        Assert.assertEquals(dictionary.size(), 2);
    }

    @Test
    public void contigIdxAttributesAreHonoured() {
        final BCFDictionary dictionary = BCFDictionary.forContigs(header(contig("chr2", 5), contig("chr1", 0)));
        Assert.assertEquals(dictionary.getString(0), "chr1");
        Assert.assertEquals(dictionary.getString(5), "chr2");
        Assert.expectThrows(TribbleException.class, () -> dictionary.getString(1));
    }

    @Test
    public void aContigWithoutIdxAfterOneWithIdxTakesTheNextIndex() {
        final BCFDictionary dictionary = BCFDictionary.forContigs(header(contig("chr1", 3), contig("chr2")));
        Assert.assertEquals(dictionary.getString(3), "chr1");
        Assert.assertEquals(dictionary.getString(4), "chr2");
    }

    @Test
    public void theContigDictionaryIgnoresFilterInfoAndFormatLines() {
        final BCFDictionary dictionary = BCFDictionary.forContigs(header(info("DP", 0), contig("chr1")));
        Assert.assertEquals(dictionary.getString(0), "chr1");
        Assert.assertEquals(dictionary.size(), 1);
    }

    // -- Helpers --

    private static VCFHeader header(final VCFHeaderLine... lines) {
        final Set<VCFHeaderLine> set = new LinkedHashSet<>();
        for (final VCFHeaderLine line : lines) set.add(line);
        return new VCFHeader(set);
    }

    private static VCFInfoHeaderLine info(final String id) {
        return new VCFInfoHeaderLine(
                "<ID=" + id + ",Number=1,Type=Integer,Description=\"d\">", VCFHeaderVersion.VCF4_2);
    }

    private static VCFInfoHeaderLine info(final String id, final int idx) {
        return new VCFInfoHeaderLine(
                "<ID=" + id + ",Number=1,Type=Integer,Description=\"d\",IDX=" + idx + ">", VCFHeaderVersion.VCF4_2);
    }

    private static VCFFormatHeaderLine format(final String id) {
        return new VCFFormatHeaderLine(
                "<ID=" + id + ",Number=1,Type=String,Description=\"d\">", VCFHeaderVersion.VCF4_2);
    }

    private static VCFFormatHeaderLine format(final String id, final int idx) {
        return new VCFFormatHeaderLine(
                "<ID=" + id + ",Number=1,Type=String,Description=\"d\",IDX=" + idx + ">", VCFHeaderVersion.VCF4_2);
    }

    private static VCFFilterHeaderLine filter(final String id) {
        return new VCFFilterHeaderLine("<ID=" + id + ",Description=\"d\">", VCFHeaderVersion.VCF4_2);
    }

    private static VCFFilterHeaderLine filter(final String id, final int idx) {
        return new VCFFilterHeaderLine("<ID=" + id + ",Description=\"d\",IDX=" + idx + ">", VCFHeaderVersion.VCF4_2);
    }

    private static int contigIndex = 0;

    private static VCFContigHeaderLine contig(final String id) {
        return new VCFContigHeaderLine("<ID=" + id + ",length=100>", VCFHeaderVersion.VCF4_2, "contig", contigIndex++);
    }

    private static VCFContigHeaderLine contig(final String id, final int idx) {
        return new VCFContigHeaderLine(
                "<ID=" + id + ",length=100,IDX=" + idx + ">", VCFHeaderVersion.VCF4_2, "contig", contigIndex++);
    }
}
