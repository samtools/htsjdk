package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.CRAMByteReader;
import htsjdk.samtools.cram.io.CRAMByteWriter;
import htsjdk.samtools.cram.io.IOTestCases;
import java.io.IOException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ByteArrayStopCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testByteArrays", dataProviderClass = IOTestCases.class)
    public void codecTest(final byte[] values) throws IOException {
        final byte stopByte = '\t';

        // sanity check this test
        for (final byte v : values) {
            Assert.assertNotEquals(v, stopByte);
        }

        final CRAMByteWriter os = new CRAMByteWriter();
        final CRAMCodec<byte[]> writeCodec = new ByteArrayStopCodec(null, os, stopByte);

        writeCodec.write(values);
        final byte[] written = os.toByteArray();

        final CRAMByteReader is = new CRAMByteReader(written);
        final CRAMCodec<byte[]> readCodec = new ByteArrayStopCodec(is, null, stopByte);

        final byte[] actual = readCodec.read();
        Assert.assertEquals(actual, values);
    }
}
