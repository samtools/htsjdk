package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.IOTestCases;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;

public class ExternalByteArrayCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testByteArrays", dataProviderClass = IOTestCases.class)
    public void codecTest(final byte[] values) throws IOException {

        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<byte[]> writeCodec = new ExternalByteArrayCodec(null, os);

            writeCodec.write(values);

            try (final ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray())) {
                final CRAMCodec<byte[]> readCodec = new ExternalByteArrayCodec(is, null);

                final byte[] actual = readCodec.read(values.length);
                Assert.assertEquals(actual, values);
            }
        }
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void readWithoutLength() throws IOException {
        try (final ByteArrayInputStream is = new ByteArrayInputStream(new byte[0])) {
            final CRAMCodec<byte[]> readCodec = new ExternalByteArrayCodec(is, null);

            readCodec.read();
        }
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void readTooMuch() throws IOException {
        try (final ByteArrayInputStream is = new ByteArrayInputStream(new byte[0])) {
            final CRAMCodec<byte[]> readCodec = new ExternalByteArrayCodec(is, null);

            readCodec.read(1);
        }
    }
}