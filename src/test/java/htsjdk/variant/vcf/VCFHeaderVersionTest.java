package htsjdk.variant.vcf;

import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VCFHeaderVersionTest extends VariantBaseTest {
    @DataProvider(name="vcfVersionRelationships")
    public Object[][] vcfVersionRelationships() {
        return new Object[][] {
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_2, true},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_1, true},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_0, true},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF3_3, true},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF3_2, true},

                {VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_1, true},
                {VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_0, true},
                {VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF3_3, true},
                {VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF3_2, true},

                {VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF4_0, true},
                {VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF3_3, true},
                {VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF3_2, true},

                {VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF3_3, true},
                {VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF3_2, true},

                {VCFHeaderVersion.VCF3_3, VCFHeaderVersion.VCF3_2, true},
        };
    }

    @Test(dataProvider="vcfVersionRelationships")
    public void testVCFVersionRelationships(
            final VCFHeaderVersion sourceVersion,
            final VCFHeaderVersion targetVersion,
            final boolean expectedIsAtLeastAsRecentAs) {
        Assert.assertEquals(sourceVersion.isAtLeastAsRecentAs(targetVersion), expectedIsAtLeastAsRecentAs);
        Assert.assertNotEquals(targetVersion.isAtLeastAsRecentAs(sourceVersion), expectedIsAtLeastAsRecentAs);
    }

}
