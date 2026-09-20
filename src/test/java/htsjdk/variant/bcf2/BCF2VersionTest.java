package htsjdk.variant.bcf2;

import htsjdk.variant.VariantBaseTest;
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
}
