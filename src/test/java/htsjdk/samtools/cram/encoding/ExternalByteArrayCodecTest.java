package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.io.IOTestCases;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;

public class ExternalByteArrayCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testByteArrays", dataProviderClass = IOTestCases.class)
    public void codecTest(byte[] values) throws IOException {

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            BitCodec<byte[]> writeCodec = new ExternalByteArrayCodec(os, null);

            // this parameter is not used - the external block is set in the constructor
            writeCodec.write(null, values);

            try (InputStream is = new ByteArrayInputStream(os.toByteArray())) {
                BitCodec<byte[]> readCodec = new ExternalByteArrayCodec(null, is);

                // this parameter is not used - the external block is set in the constructor
                byte[] actual = readCodec.read(null, values.length);
                Assert.assertEquals(actual, values);
            }
        }
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void readWithoutLength() throws IOException {
        try (InputStream is = new ByteArrayInputStream(new byte[0])) {
            BitCodec<byte[]> readCodec = new ExternalByteArrayCodec(null, is);

            // this parameter is not used - the external block is set in the constructor
            readCodec.read(null);
        }
    }

    @Test(expectedExceptions = EOFException.class)
    public void readTooMuch() throws IOException {
        try (InputStream is = new ByteArrayInputStream(new byte[0])) {
            BitCodec<byte[]> readCodec = new ExternalByteArrayCodec(null, is);

            // this parameter is not used - the external block is set in the constructor
            readCodec.read(null, 1);
        }
    }
}