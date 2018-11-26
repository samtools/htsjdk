package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.IOTestCases;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class ByteArrayStopCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testByteArrays", dataProviderClass = IOTestCases.class)
    public void codecTest(final byte[] values) throws IOException {
        final byte stopByte = 100;
        Assert.assertFalse(Arrays.asList(values).contains(stopByte));   // sanity check for this test

        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<byte[]> writeCodec = new ByteArrayStopCodec(null, os, stopByte);

            writeCodec.write(values);

            try (final ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray())) {
                final CRAMCodec<byte[]> readCodec = new ByteArrayStopCodec(is, null, stopByte);

                final byte[] actual = readCodec.read();
                Assert.assertEquals(actual, values);
            }
        }
    }
}