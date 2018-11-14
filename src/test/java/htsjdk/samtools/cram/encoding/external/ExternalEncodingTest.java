package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ExternalEncodingTest extends HtsjdkTest {
    @DataProvider(name = "tests")
    public Object[][] testData() {
        return new Object[][] {
                // positive values below the ITF8 single-byte limit (128) are encoded as-is
                {0, new byte[] { 0 }},
                {127, new byte[] { 127 }},

                // 128 (0x80) is ITF8-encoded as (0x80, 0x80)
                {128, new byte[] { (byte) 0x80, (byte) 0x80 }},
        };
    }

    @Test(dataProvider = "tests")
    public void paramsTest(final int externalBlockContentId, final byte[] expected) {

        final ExternalByteEncoding constructedB = new ExternalByteEncoding(externalBlockContentId);
        Assert.assertEquals(constructedB.toByteArray(), expected);

        final ExternalByteEncoding fromParamsB = ExternalByteEncoding.fromParams(expected);
        Assert.assertEquals(fromParamsB.toByteArray(), expected);

        final ExternalByteArrayEncoding constructedBA = new ExternalByteArrayEncoding(externalBlockContentId);
        Assert.assertEquals(constructedBA.toByteArray(), expected);

        final ExternalByteArrayEncoding fromParamsBA = ExternalByteArrayEncoding.fromParams(expected);
        Assert.assertEquals(fromParamsBA.toByteArray(), expected);

        final ExternalIntegerEncoding constructedI = new ExternalIntegerEncoding(externalBlockContentId);
        Assert.assertEquals(constructedI.toByteArray(), expected);

        final ExternalIntegerEncoding fromParamsI = ExternalIntegerEncoding.fromParams(expected);
        Assert.assertEquals(fromParamsI.toByteArray(), expected);

        final ExternalLongEncoding constructedL = new ExternalLongEncoding(externalBlockContentId);
        Assert.assertEquals(constructedL.toByteArray(), expected);

        final ExternalLongEncoding fromParamsL = ExternalLongEncoding.fromParams(expected);
        Assert.assertEquals(fromParamsL.toByteArray(), expected);
    }
}
