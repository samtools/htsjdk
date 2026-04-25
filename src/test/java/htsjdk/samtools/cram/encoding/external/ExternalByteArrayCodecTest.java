package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.CRAMByteReader;
import htsjdk.samtools.cram.io.CRAMByteWriter;
import htsjdk.samtools.cram.io.IOTestCases;
import java.io.*;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ExternalByteArrayCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testByteArrays", dataProviderClass = IOTestCases.class)
    public void codecTest(final byte[] values) throws IOException {
        final CRAMByteWriter os = new CRAMByteWriter();
        final CRAMCodec<byte[]> writeCodec = new ExternalByteArrayCodec(null, os);

        writeCodec.write(values);
        final byte[] written = os.toByteArray();

        final CRAMByteReader is = new CRAMByteReader(written);
        final CRAMCodec<byte[]> readCodec = new ExternalByteArrayCodec(is, null);

        final byte[] actual = readCodec.read(values.length);
        Assert.assertEquals(actual, values);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void readWithoutLength() throws IOException {
        final CRAMByteReader is = new CRAMByteReader(new byte[0]);
        final CRAMCodec<byte[]> readCodec = new ExternalByteArrayCodec(is, null);

        readCodec.read();
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void readTooMuch() throws IOException {
        final CRAMByteReader is = new CRAMByteReader(new byte[0]);
        final CRAMCodec<byte[]> readCodec = new ExternalByteArrayCodec(is, null);

        readCodec.read(1);
    }
}
