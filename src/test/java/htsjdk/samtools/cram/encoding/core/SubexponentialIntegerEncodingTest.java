package htsjdk.samtools.cram.encoding.core;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SubexponentialIntegerEncodingTest extends HtsjdkTest {
    @DataProvider(name = "tests")
    public Object[][] testData() {
        return new Object[][] {
                // positive values below the ITF8 single-byte limit (128) are encoded as-is
                {0, 8, new byte[] { 0, 8 }},
                {127, 32, new byte[] { 127, 32 }},

                // offset can be any int
                // 128 (0x80) is ITF8-encoded as (0x80, 0x80)
                {128, 32, new byte[] { (byte) 0x80, (byte) 0x80, 32 }},
                // -1 (0xFFFFFFFF) is ITF8-encoded as (0xFF, 0xFF, 0xFF, 0xFF, 0xFF)
                {-1, 32, new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 32 }},
        };
    }

    @Test(dataProvider = "tests")
    public void paramsTest(final int offset, final int k, final byte[] expected) {

        final SubexponentialIntegerEncoding constructed = new SubexponentialIntegerEncoding(offset, k);
        Assert.assertEquals(constructed.toByteArray(), expected);

        final SubexponentialIntegerEncoding fromParams = SubexponentialIntegerEncoding.fromParams(expected);
        Assert.assertEquals(fromParams.toByteArray(), expected);
    }

    // sanity check for k.  Must be >= 0

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void negativeK() {
        new SubexponentialIntegerEncoding(0, -1);
    }
}
