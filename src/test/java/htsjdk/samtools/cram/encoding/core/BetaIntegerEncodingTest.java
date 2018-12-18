package htsjdk.samtools.cram.encoding.core;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BetaIntegerEncodingTest extends HtsjdkTest {
    @DataProvider(name = "tests")
    public Object[][] testData() {
        return new Object[][] {
                // positive values below the ITF8 single-byte limit (128) are encoded as-is
                {0, 0, new byte[] { 0, 0 }},
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
    public void paramsTest(final int offset, final int bitLength, final byte[] expected) {

        final BetaIntegerEncoding constructed = new BetaIntegerEncoding(offset, bitLength);
        Assert.assertEquals(constructed.toByteArray(), expected);

        final BetaIntegerEncoding fromParams = BetaIntegerEncoding.fromParams(expected);
        Assert.assertEquals(fromParams.toByteArray(), expected);
    }


    // sanity checks for bitsPerValue.  Must be between 0 and 32, inclusive

    @DataProvider(name = "bitsPerValue")
    public Object[][] bitsPerValueData() {
        return new Object[][] {
                {-1},
                {33}
        };
    }

    @Test(dataProvider = "bitsPerValue", expectedExceptions = IllegalArgumentException.class)
    public void bitsPerValue(final int bitsPerValue) {
        new BetaIntegerEncoding(0, bitsPerValue);
    }
}
