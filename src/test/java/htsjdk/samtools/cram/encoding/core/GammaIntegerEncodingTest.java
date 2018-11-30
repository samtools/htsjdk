package htsjdk.samtools.cram.encoding.core;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class GammaIntegerEncodingTest extends HtsjdkTest {
    @DataProvider(name = "tests")
    public Object[][] testData() {
        return new Object[][] {
                // positive values below the ITF8 single-byte limit (128) are encoded as-is
                {0, new byte[] { 0 }},
                {127, new byte[] { 127 }},

                // offset can be any int
                // 128 (0x80) is ITF8-encoded as (0x80, 0x80)
                {128, new byte[] { (byte) 0x80, (byte) 0x80 }},
                // -1 (0xFFFFFFFF) is ITF8-encoded as (0xFF, 0xFF, 0xFF, 0xFF, 0xFF)
                {-1, new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }},
        };
    }

    @Test(dataProvider = "tests")
    public void paramsTest(final int offset, final byte[] expected) {

        final GammaIntegerEncoding constructed = new GammaIntegerEncoding(offset);
        Assert.assertEquals(constructed.toByteArray(), expected);

        final GammaIntegerEncoding fromParams = GammaIntegerEncoding.fromParams(expected);
        Assert.assertEquals(fromParams.toByteArray(), expected);
    }
}
