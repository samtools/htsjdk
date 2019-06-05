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
        Assert.assertEquals(constructedB.toSerializedEncodingParams(), expected);

        final ExternalByteEncoding paramsB = ExternalByteEncoding.fromSerializedEncodingParams(expected);
        Assert.assertEquals(paramsB.toSerializedEncodingParams(), expected);

        final ExternalByteArrayEncoding constructedBA = new ExternalByteArrayEncoding(externalBlockContentId);
        Assert.assertEquals(constructedBA.toSerializedEncodingParams(), expected);

        final ExternalByteArrayEncoding paramsBA = ExternalByteArrayEncoding.fromSerializedEncodingParams(expected);
        Assert.assertEquals(paramsBA.toSerializedEncodingParams(), expected);

        final ExternalIntegerEncoding constructedI = new ExternalIntegerEncoding(externalBlockContentId);
        Assert.assertEquals(constructedI.toSerializedEncodingParams(), expected);

        final ExternalIntegerEncoding paramsI = ExternalIntegerEncoding.fromSerializedEncodingParams(expected);
        Assert.assertEquals(paramsI.toSerializedEncodingParams(), expected);

        final ExternalLongEncoding constructedL = new ExternalLongEncoding(externalBlockContentId);
        Assert.assertEquals(constructedL.toSerializedEncodingParams(), expected);

        final ExternalLongEncoding paramsL = ExternalLongEncoding.fromSerializedEncodingParams(expected);
        Assert.assertEquals(paramsL.toSerializedEncodingParams(), expected);
    }
}
