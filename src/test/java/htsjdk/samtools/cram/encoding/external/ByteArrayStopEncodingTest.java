package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ByteArrayStopEncodingTest extends HtsjdkTest {
    @DataProvider(name = "tests")
    public Object[][] testData() {
        return new Object[][] {
                // the stop byte (first) is stored as-is
                // positive values below the ITF8 single-byte limit (128) are encoded as-is
                {(byte) 0, 127, new byte[] { 0, 127 }},
                {(byte) -128, 0, new byte[] { -128, 0 }},

                // -128 in signed byte context is stored as-is (0x80)
                // 128 in unsigned int context (also 0x80) is ITF8-encoded as (0x80, 0x80)
                {(byte) -128, 128, new byte[] { -128, (byte) 0x80, (byte) 0x80 }},
        };
    }

    @Test(dataProvider = "tests")
    public void paramsTest(final byte stopByte, final int externalBlockContentId, final byte[] expected) {

        final ByteArrayStopEncoding constructed = new ByteArrayStopEncoding(stopByte, externalBlockContentId);
        Assert.assertEquals(constructed.toByteArray(), expected);

        final ByteArrayStopEncoding fromParams = ByteArrayStopEncoding.fromParams(expected);
        Assert.assertEquals(fromParams.toByteArray(), expected);
    }
}
