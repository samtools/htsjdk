package htsjdk.variant.vcf;

import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VCFHeaderVersionTest extends VariantBaseTest {
    @DataProvider(name = "vcfVersionRelationships")
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

    @Test(dataProvider = "vcfVersionRelationships")
    public void testVCFVersionRelationships(
            final VCFHeaderVersion sourceVersion,
            final VCFHeaderVersion targetVersion,
            final boolean expectedIsAtLeastAsRecentAs) {
        Assert.assertEquals(sourceVersion.isAtLeastAsRecentAs(targetVersion), expectedIsAtLeastAsRecentAs);
        Assert.assertNotEquals(targetVersion.isAtLeastAsRecentAs(sourceVersion), expectedIsAtLeastAsRecentAs);
    }

    @Test
    public void versions4_4And4_5AreKnownAndOrdered() {
        Assert.assertEquals(VCFHeaderVersion.toHeaderVersion("VCFv4.4"), VCFHeaderVersion.VCF4_4);
        Assert.assertEquals(VCFHeaderVersion.toHeaderVersion("VCFv4.5"), VCFHeaderVersion.VCF4_5);
        Assert.assertTrue(VCFHeaderVersion.VCF4_5.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_4));
        Assert.assertTrue(VCFHeaderVersion.VCF4_4.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3));
        Assert.assertFalse(VCFHeaderVersion.VCF4_3.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_4));
    }

    @Test
    public void unknownVersionStringsAreNotVersions() {
        Assert.assertNull(VCFHeaderVersion.toHeaderVersion("VCFv4.6"));
        Assert.assertFalse(VCFHeaderVersion.isVersionString("VCFv5.0"));
    }

    @Test
    public void theDefaultVersionIs4_2() {
        Assert.assertEquals(VCFHeaderVersion.DEFAULT_VERSION, VCFHeaderVersion.VCF4_2);
    }

    @Test
    public void isOlderThanIsTheNegationOfIsAtLeastAsRecentAs() {
        for (final VCFHeaderVersion a : VCFHeaderVersion.values()) {
            for (final VCFHeaderVersion b : VCFHeaderVersion.values()) {
                Assert.assertEquals(a.isOlderThan(b), !a.isAtLeastAsRecentAs(b), a + " vs " + b);
            }
        }
        Assert.assertTrue(VCFHeaderVersion.VCF4_2.isOlderThan(VCFHeaderVersion.VCF4_3));
        Assert.assertFalse(VCFHeaderVersion.VCF4_3.isOlderThan(VCFHeaderVersion.VCF4_3));
        Assert.assertFalse(VCFHeaderVersion.VCF4_4.isOlderThan(VCFHeaderVersion.VCF4_3));
    }

    @Test
    public void textIsPercentEncodedFrom4_3On() {
        Assert.assertFalse(VCFHeaderVersion.VCF3_3.percentEncodesText());
        Assert.assertFalse(VCFHeaderVersion.VCF4_0.percentEncodesText());
        Assert.assertFalse(VCFHeaderVersion.VCF4_2.percentEncodesText());
        Assert.assertTrue(VCFHeaderVersion.VCF4_3.percentEncodesText());
        Assert.assertTrue(VCFHeaderVersion.VCF4_4.percentEncodesText());
        Assert.assertTrue(VCFHeaderVersion.VCF4_5.percentEncodesText());
    }

    @Test
    public void aLeadingPhaseIndicatorIsAllowedFrom4_4On() {
        Assert.assertFalse(VCFHeaderVersion.VCF4_2.leadingPhaseAllowed());
        Assert.assertFalse(VCFHeaderVersion.VCF4_3.leadingPhaseAllowed());
        Assert.assertTrue(VCFHeaderVersion.VCF4_4.leadingPhaseAllowed());
        Assert.assertTrue(VCFHeaderVersion.VCF4_5.leadingPhaseAllowed());
    }

    @Test
    public void laaFollowsGtFrom4_5On() {
        Assert.assertFalse(VCFHeaderVersion.VCF4_3.laaFollowsGt());
        Assert.assertFalse(VCFHeaderVersion.VCF4_4.laaFollowsGt());
        Assert.assertTrue(VCFHeaderVersion.VCF4_5.laaFollowsGt());
    }
}
