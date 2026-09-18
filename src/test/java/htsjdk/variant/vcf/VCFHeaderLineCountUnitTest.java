package htsjdk.variant.vcf;

import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VCFHeaderLineCountUnitTest extends VariantBaseTest {

    @Test
    public void onlyTheSampleDependentCodesVaryBySample() {
        Assert.assertTrue(VCFHeaderLineCount.P.variesBySample());
        Assert.assertTrue(VCFHeaderLineCount.LA.variesBySample());
        Assert.assertTrue(VCFHeaderLineCount.LR.variesBySample());
        Assert.assertTrue(VCFHeaderLineCount.LG.variesBySample());
        Assert.assertTrue(VCFHeaderLineCount.M.variesBySample());
        Assert.assertFalse(VCFHeaderLineCount.INTEGER.variesBySample());
        Assert.assertFalse(VCFHeaderLineCount.A.variesBySample());
        Assert.assertFalse(VCFHeaderLineCount.R.variesBySample());
        Assert.assertFalse(VCFHeaderLineCount.G.variesBySample());
        Assert.assertFalse(VCFHeaderLineCount.UNBOUNDED.variesBySample());
    }

    @Test
    public void everyCodeIsFoundByTheTextItWrites() {
        for (final VCFHeaderLineCount count : VCFHeaderLineCount.values()) {
            if (count != VCFHeaderLineCount.INTEGER) {
                Assert.assertEquals(VCFHeaderLineCount.fromNumberText(count.getNumberText()), count);
            }
        }
    }

    @Test
    public void anIntegerIsNotACode() {
        Assert.assertEquals(VCFHeaderLineCount.fromNumberText("7"), VCFHeaderLineCount.INTEGER);
        Assert.assertNull(VCFHeaderLineCount.INTEGER.getNumberText());
    }
}
