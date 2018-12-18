package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.IOTestCases;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ByteArrayStopCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testByteArrays", dataProviderClass = IOTestCases.class)
    public void codecTest(final byte[] values) throws IOException {
        final byte stopByte = '\t';

        // sanity check this test
        for (final byte v : values) {
            Assert.assertNotEquals(v, stopByte);
        }

        byte[] written;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<byte[]> writeCodec = new ByteArrayStopCodec(null, os, stopByte);

            writeCodec.write(values);
            os.flush();
            written = os.toByteArray();
        }

        try (final ByteArrayInputStream is = new ByteArrayInputStream(written)) {
            final CRAMCodec<byte[]> readCodec = new ByteArrayStopCodec(is, null, stopByte);

            final byte[] actual = readCodec.read();
            Assert.assertEquals(actual, values);
        }
    }
}