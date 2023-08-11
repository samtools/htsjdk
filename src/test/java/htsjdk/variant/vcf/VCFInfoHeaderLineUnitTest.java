package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.TribbleException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test conditions that are unique to INFO lines (not covered by VCFCompoundHeaderLineUnitTest).
 */
public class VCFInfoHeaderLineUnitTest extends HtsjdkTest {

    @Test
    public void testRepairInfoLineFlagTypeWithNonzeroCount() {
        final VCFInfoHeaderLine infoLine = new VCFInfoHeaderLine("<ID=FOO,Number=27,Type=Flag,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION);
        Assert.assertEquals(0, infoLine.getCount());
    }

    @DataProvider(name = "mergeCompatibleInfoLines")
    public Object[][] getMergeCompatibleInfoLines() {
        return new Object[][]{
                {
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=1,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">", VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=A,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">", VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=.,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">", VCFHeader.DEFAULT_VCF_VERSION)
                }
        };
    }

    @Test(dataProvider = "mergeCompatibleInfoLines")
    public void testMergeCompatibleInfoLines(
            final VCFInfoHeaderLine infoHeaderLine1,
            final VCFInfoHeaderLine infoHeaderLine2,
            final VCFInfoHeaderLine expectedHeaderLine) {
        Assert.assertEquals(
                VCFInfoHeaderLine.getMergedInfoHeaderLine(
                        infoHeaderLine1,
                        infoHeaderLine2,
                        new VCFHeaderMerger.HeaderMergeConflictWarnings(true)),
                expectedHeaderLine);
    }

    @DataProvider(name = "mergeIncompatibleInfoLines")
    public Object[][] getMergeIncompatibleInfoLines() {
        return new Object[][]{
                // 2 lines to merge, expected result
                {
                        // mixed number AND number type (multiple different attributes)
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=A,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=1,Type=Integer,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION)
                },
                {
                        // mixed number AND number type  (multiple different attributes), reverse direction
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=1,Type=Integer,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=A,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION)
                }
        };
    }

    @Test
    public void testAllow1000GKey() {
        final VCFInfoHeaderLine line = new VCFInfoHeaderLine(
            "INFO=<ID=1000G,Number=0,Type=Flag,Description=1000G>",
            VCFHeader.DEFAULT_VCF_VERSION
        );

        // TODO change to VCFHeader.DEFAULT_VCF_VERSION
        Assert.assertFalse(line.validateForVersion(VCFHeaderVersion.VCF4_3).isPresent());
    }

    @Test(dataProvider = "mergeIncompatibleInfoLines", expectedExceptions= TribbleException.class)
    public void testMergeIncompatibleInfoLines(
            final VCFInfoHeaderLine infoHeaderLine1,
            final VCFInfoHeaderLine infoHeaderLine2) {
        VCFInfoHeaderLine.getMergedInfoHeaderLine(
                infoHeaderLine1,
                infoHeaderLine2,
                new VCFHeaderMerger.HeaderMergeConflictWarnings(true));
    }

}
