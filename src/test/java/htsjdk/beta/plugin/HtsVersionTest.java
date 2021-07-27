package htsjdk.beta.plugin;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

public class HtsVersionTest extends HtsjdkTest {

    @DataProvider(name="stringConversionTests")
    public Object[][] getStringConversionTests() {
        return new Object[][] {
                { "1.0.0", new HtsVersion(1, 0, 0) },
                { "1.2.3", new HtsVersion(1, 2, 3) }
        };
    }

    @Test(dataProvider = "stringConversionTests")
    final void testStringConversion(final String versionString, final HtsVersion expectedVersion) {
        Assert.assertEquals(new HtsVersion(versionString), expectedVersion);
    }

    @DataProvider(name="compareTests")
    public Object[][] getCompareTests() {
        return new Object[][] {
                // a == b
                {  new HtsVersion(1, 0, 0), new HtsVersion(1, 0, 0), 0 },
                {  new HtsVersion(1, 2, 0), new HtsVersion(1, 2, 0), 0 },
                {  new HtsVersion(1, 2, 3), new HtsVersion(1, 2, 3), 0 },

                //negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object    @Test(dataProvider = "compareTests")
                // a < b
                {  new HtsVersion(1, 1, 1), new HtsVersion(1, 1, 2), -1 },
                {  new HtsVersion(1, 1, 1), new HtsVersion(1, 2, 0), -1 },
                {  new HtsVersion(1, 1, 1), new HtsVersion(2, 1, 1), -1 },

                // a > b
                {  new HtsVersion(1, 1, 2), new HtsVersion(1, 1, 1), 1 },
                {  new HtsVersion(1, 2, 0), new HtsVersion(1, 1, 1), 1 },
                {  new HtsVersion(2, 1, 1), new HtsVersion(1, 1, 1), 1 },
        };
    }

    @Test(dataProvider = "compareTests")
    final void testCompareTo(final HtsVersion firstVersion, final HtsVersion secondVersion, final int expectedCompare) {
        Assert.assertEquals(firstVersion.compareTo(secondVersion), expectedCompare);
    }


    @Test(dataProvider = "compareTests")
    final void testHashCode(final HtsVersion firstVersion, final HtsVersion secondVersion, final int expectedCompare) {
        final Set<HtsVersion> set = new HashSet<>();
        set.add(firstVersion);
        set.add(secondVersion);
        if (expectedCompare != 0) {
            Assert.assertEquals(set.size(), 2);
        } else {
            Assert.assertEquals(set.size(), 1);
        }
    }

}
