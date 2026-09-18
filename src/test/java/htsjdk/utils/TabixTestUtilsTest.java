package htsjdk.utils;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TabixTestUtilsTest extends HtsjdkTest {

    @Test
    public void testTabixIsAvailable() {
        Assert.assertTrue(TabixTestUtils.isTabixAvailable());
    }
}
