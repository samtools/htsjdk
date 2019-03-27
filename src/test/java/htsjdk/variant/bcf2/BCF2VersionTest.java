package htsjdk.variant.bcf2;

import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BCF2VersionTest extends VariantBaseTest {

    @DataProvider(name = "bcfVersionEqualsHashData")
    public Object[][] bcfVersionEqualsHashData() {
        return new Object[][]{
                {
                        new BCFVersion(BCF2Codec.ALLOWED_MAJOR_VERSION, BCF2Codec.ALLOWED_MINOR_VERSION),
                        new BCFVersion(BCF2Codec.ALLOWED_MAJOR_VERSION, BCF2Codec.ALLOWED_MINOR_VERSION),
                        true
                },
                {
                        new BCFVersion(0, 0),
                        new BCFVersion(0, 0),
                        true
                },
                {
                        new BCFVersion(BCF2Codec.ALLOWED_MAJOR_VERSION, BCF2Codec.ALLOWED_MINOR_VERSION),
                        new BCFVersion(0, 0),
                        false
                },
                {
                        new BCFVersion(BCF2Codec.ALLOWED_MAJOR_VERSION, BCF2Codec.ALLOWED_MINOR_VERSION),
                        new BCFVersion(0, BCF2Codec.ALLOWED_MAJOR_VERSION),
                        false
                },
                {
                        new BCFVersion(BCF2Codec.ALLOWED_MAJOR_VERSION, BCF2Codec.ALLOWED_MINOR_VERSION),
                        new BCFVersion(0, BCF2Codec.ALLOWED_MAJOR_VERSION),
                        false
                },
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
        Assert.assertEquals(expected,v1.hashCode() == v2.hashCode());
    }
}
