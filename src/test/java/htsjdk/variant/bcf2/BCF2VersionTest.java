package htsjdk.variant.bcf2;

import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.vcf.VCFHeaderVersion;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BCF2VersionTest extends VariantBaseTest {

    @DataProvider(name = "bcfVersionEqualsHashData")
    public Object[][] bcfVersionEqualsHashData() {
        return new Object[][] {
            {new BCFVersion(2, 1), new BCFVersion(2, 1), true},
            {new BCFVersion(0, 0), new BCFVersion(0, 0), true},
            {new BCFVersion(2, 1), new BCFVersion(0, 0), false},
            {new BCFVersion(2, 1), new BCFVersion(0, 2), false},
            {new BCFVersion(2, 2), new BCFVersion(2, 1), false},
        };
    }

    @Test(dataProvider = "bcfVersionEqualsHashData")
    private final void testBCFVersionEquals(final BCFVersion v1, BCFVersion v2, boolean expected) {
        Assert.assertEquals(expected, v1.equals(v2));
        Assert.assertEquals(expected, v2.equals(v1));
    }

    @Test(dataProvider = "bcfVersionEqualsHashData")
    private final void testBCFVersionHash(final BCFVersion v1, BCFVersion v2, boolean expected) {
        // given the small space the test data is drawn from, assume not equals => different
        // hash codes just for this test
        Assert.assertEquals(expected, v1.hashCode() == v2.hashCode());
    }

    @Test
    public void onlyBcf21And22AreSupported() {
        Assert.assertTrue(BCFVersion.BCF_2_1.isSupported());
        Assert.assertTrue(BCFVersion.BCF_2_2.isSupported());
        Assert.assertFalse(new BCFVersion(2, 0).isSupported());
        Assert.assertFalse(new BCFVersion(2, 3).isSupported());
        Assert.assertFalse(new BCFVersion(3, 1).isSupported());
    }

    @Test
    public void bcf22IsBgzfCompressedAndBcf21IsNot() {
        Assert.assertTrue(BCFVersion.BCF_2_2.isBgzfCompressed());
        Assert.assertFalse(BCFVersion.BCF_2_1.isBgzfCompressed());
    }

    @Test
    public void bcf22PadsWithEndOfVectorAndBcf21DoesNot() {
        Assert.assertTrue(BCFVersion.BCF_2_2.padsWithEndOfVector());
        Assert.assertFalse(BCFVersion.BCF_2_1.padsWithEndOfVector());
    }

    @Test
    public void onlyBcf21WritesStringListsWithALeadingComma() {
        Assert.assertTrue(BCFVersion.BCF_2_1.stringListsHaveLeadingComma());
        Assert.assertFalse(BCFVersion.BCF_2_2.stringListsHaveLeadingComma());
    }

    @Test
    public void bcf21CarriesVcfUpTo4_2() {
        Assert.assertTrue(BCFVersion.BCF_2_1.canCarry(VCFHeaderVersion.VCF4_2));
        Assert.assertFalse(BCFVersion.BCF_2_1.canCarry(VCFHeaderVersion.VCF4_3));
    }

    @Test
    public void bcf22CarriesEveryVcfVersion() {
        Assert.assertTrue(BCFVersion.BCF_2_2.canCarry(VCFHeaderVersion.VCF4_2));
        Assert.assertTrue(BCFVersion.BCF_2_2.canCarry(VCFHeaderVersion.VCF4_5));
    }
}
