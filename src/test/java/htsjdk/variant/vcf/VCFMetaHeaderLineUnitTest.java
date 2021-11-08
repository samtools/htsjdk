package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.TribbleException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VCFMetaHeaderLineUnitTest extends HtsjdkTest {

    @DataProvider(name = "allowedVCFVersions")
    public Object[][] allowedVCFVersions() {
        return new Object[][]{
                {VCFHeaderVersion.VCF4_3}
        };
    }

    @DataProvider(name = "rejectedVCFVersions")
    public Object[][] rejectedVCFVersions() {
        return new Object[][]{
                {VCFHeaderVersion.VCF3_2},
                {VCFHeaderVersion.VCF3_3},
                {VCFHeaderVersion.VCF4_0},
                {VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF4_2},
        };
    }

    private static final String META_STRING = "<ID=id,Description=desc>";

    @Test(dataProvider="allowedVCFVersions")
    public void testAllowedVersions(final VCFHeaderVersion vcfAllowedVersion) {
        final VCFMetaHeaderLine vcfLine = new VCFMetaHeaderLine(META_STRING, vcfAllowedVersion);
        Assert.assertEquals("id", vcfLine.getID());
        Assert.assertEquals("desc", vcfLine.getGenericFieldValue(VCFSimpleHeaderLine.DESCRIPTION_ATTRIBUTE));
    }

    @Test(dataProvider="rejectedVCFVersions",expectedExceptions=TribbleException.class)
    public void testRejectedVersions(final VCFHeaderVersion vcfAllowedVersion) {
        new VCFMetaHeaderLine(META_STRING, vcfAllowedVersion);
    }


}
