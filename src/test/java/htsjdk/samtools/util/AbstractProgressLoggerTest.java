package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AbstractProgressLoggerTest extends HtsjdkTest {

    @DataProvider
    Object[][] testPadData() {
        return new Object[][]{
                {"hello", 10, "     hello"},
                {"hello", 6, " hello"},
                {"hello", 5, "hello"},
                {"hello", 4, "hello"},
                {"hello", -1, "hello"}
        };
    }

    @Test(dataProvider = "testPadData")
    public void testPad(final String in, final int length, final String expected) {
        Assert.assertEquals(AbstractProgressLogger.pad(in, length), expected);
    }
}
