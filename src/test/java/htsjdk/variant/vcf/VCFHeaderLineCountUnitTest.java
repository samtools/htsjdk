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

    @Test
    public void theCodesEvery4xVersionHasNeedOnly4_0() {
        Assert.assertEquals(VCFHeaderLineCount.INTEGER.minimumVersion(), VCFHeaderVersion.VCF4_0);
        Assert.assertEquals(VCFHeaderLineCount.A.minimumVersion(), VCFHeaderVersion.VCF4_0);
        Assert.assertEquals(VCFHeaderLineCount.G.minimumVersion(), VCFHeaderVersion.VCF4_0);
        Assert.assertEquals(VCFHeaderLineCount.UNBOUNDED.minimumVersion(), VCFHeaderVersion.VCF4_0);
    }

    @Test
    public void perAlleleCountsNeed4_2() {
        Assert.assertEquals(VCFHeaderLineCount.R.minimumVersion(), VCFHeaderVersion.VCF4_2);
    }

    @Test
    public void perGtAlleleCountsNeed4_4() {
        Assert.assertEquals(VCFHeaderLineCount.P.minimumVersion(), VCFHeaderVersion.VCF4_4);
    }

    @Test
    public void localAlleleAndBaseModificationCountsNeed4_5() {
        Assert.assertEquals(VCFHeaderLineCount.LA.minimumVersion(), VCFHeaderVersion.VCF4_5);
        Assert.assertEquals(VCFHeaderLineCount.LR.minimumVersion(), VCFHeaderVersion.VCF4_5);
        Assert.assertEquals(VCFHeaderLineCount.LG.minimumVersion(), VCFHeaderVersion.VCF4_5);
        Assert.assertEquals(VCFHeaderLineCount.M.minimumVersion(), VCFHeaderVersion.VCF4_5);
    }
}
