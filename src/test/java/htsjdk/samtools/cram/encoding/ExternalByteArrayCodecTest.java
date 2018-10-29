package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.io.IOTestCases;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;

public class ExternalByteArrayCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testByteArrays", dataProviderClass = IOTestCases.class)
    public void codecTest(final byte[] values) throws IOException {

        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final BitCodec<byte[]> writeCodec = new ExternalByteArrayCodec(os, null);

            // this parameter is not used - the external block is set in the constructor
            writeCodec.write(null, values);

            try (final InputStream is = new ByteArrayInputStream(os.toByteArray())) {
                final BitCodec<byte[]> readCodec = new ExternalByteArrayCodec(null, is);

                // this parameter is not used - the external block is set in the constructor
                final byte[] actual = readCodec.read(null, values.length);
                Assert.assertEquals(actual, values);
            }
        }
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void readWithoutLength() throws IOException {
        try (final InputStream is = new ByteArrayInputStream(new byte[0])) {
            final BitCodec<byte[]> readCodec = new ExternalByteArrayCodec(null, is);

            // this parameter is not used - the external block is set in the constructor
            readCodec.read(null);
        }
    }

    @Test(expectedExceptions = EOFException.class)
    public void readTooMuch() throws IOException {
        try (final InputStream is = new ByteArrayInputStream(new byte[0])) {
            final BitCodec<byte[]> readCodec = new ExternalByteArrayCodec(null, is);

            // this parameter is not used - the external block is set in the constructor
            readCodec.read(null, 1);
        }
    }
}