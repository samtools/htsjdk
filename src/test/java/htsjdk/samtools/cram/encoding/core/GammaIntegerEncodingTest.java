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
        Assert.assertEquals(constructed.toSerializedEncodingParams(), expected);

        final GammaIntegerEncoding params = GammaIntegerEncoding.fromSerializedEncodingParams(expected);
        Assert.assertEquals(params.toSerializedEncodingParams(), expected);
    }

    @Test
    public void testToString() {
        final GammaIntegerEncoding encoding = new GammaIntegerEncoding(3);
        Assert.assertTrue(encoding.toString().contains("3"));
    }
}
