package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.core.BetaIntegerEncoding;
import htsjdk.samtools.cram.encoding.external.ExternalByteArrayEncoding;
import htsjdk.samtools.cram.structure.EncodingID;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ByteArrayLenEncodingTest extends HtsjdkTest {

    @DataProvider(name = "tests")
    public Object[][] testData() {
        return new Object[][] {

                // 1. BetaIntegerEncoding ID
                // 2. length of the BetaIntegerEncoding params, encoded via ITF8
                // 3. BetaIntegerEncoding params, encoded via ITF8
                // 4. ExternalByteArrayEncoding ID
                // 5. length of the ExternalByteArrayEncoding params, encoded via ITF8
                // 6. ExternalByteArrayEncoding params, encoded via ITF8

                // see also: BetaIntegerEncodingTest and ExternalByteArrayEncodingTest

                {0, 8, 127, new byte[] {
                        (byte) EncodingID.BETA.getId(),
                        2,
                        0, 8,
                        (byte) EncodingID.EXTERNAL.getId(),
                        1,
                        127 }},

                {127, 32, 0, new byte[] {
                        (byte) EncodingID.BETA.getId(),
                        2,
                        127, 32,
                        (byte) EncodingID.EXTERNAL.getId(),
                        1,
                        0 }},

                {128, 32, -1, new byte[] {
                        (byte) EncodingID.BETA.getId(),
                        3,
                        // 128 in ITF8
                        (byte) 0x80, (byte) 0x80,
                        32,
                        (byte) EncodingID.EXTERNAL.getId(),
                        5,
                        // -1 in ITF8
                        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }},
        };
    }

    @Test(dataProvider = "tests")
    public void paramsTest(final int offset, final int bitsPerValue, final int externalBlockContentId, final byte[] expected) {
        // arbitrary choice here: any Integer, byte[] encoding pair will do

        final CRAMEncoding<Integer> constructedLength = new BetaIntegerEncoding(offset, bitsPerValue);
        final CRAMEncoding<byte[]> constructedValues = new ExternalByteArrayEncoding(externalBlockContentId);
        final ByteArrayLenEncoding constructed = new ByteArrayLenEncoding(constructedLength, constructedValues);
        Assert.assertEquals(constructed.toByteArray(), expected);

        final ByteArrayLenEncoding fromParams = ByteArrayLenEncoding.fromParams(expected);
        Assert.assertEquals(fromParams.toByteArray(), expected);
    }
}
