package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.CRAMByteReader;
import htsjdk.samtools.cram.io.CRAMByteWriter;
import htsjdk.samtools.cram.io.IOTestCases;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ExternalByteCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testByteLists", dataProviderClass = IOTestCases.class)
    public void codecTest(final List<Byte> values) throws IOException {
        final CRAMByteWriter os = new CRAMByteWriter();
        final CRAMCodec<Byte> writeCodec = new ExternalByteCodec(null, os);

        for (final byte value : values) {
            writeCodec.write(value);
        }
        final byte[] written = os.toByteArray();

        final List<Byte> actual = new ArrayList<>(values.size());
        final CRAMByteReader is = new CRAMByteReader(written);
        final CRAMCodec<Byte> readCodec = new ExternalByteCodec(is, null);

        for (int i = 0; i < values.size(); i++) {
            actual.add(readCodec.read());
        }

        Assert.assertEquals(actual, values);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void readWithLength() throws IOException {
        final CRAMByteReader is = new CRAMByteReader(new byte[0]);
        final CRAMCodec<Byte> readCodec = new ExternalByteCodec(is, null);

        readCodec.read(1);
    }
}
