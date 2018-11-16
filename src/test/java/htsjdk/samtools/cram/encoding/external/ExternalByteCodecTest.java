package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.IOTestCases;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ExternalByteCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testByteLists", dataProviderClass = IOTestCases.class)
    public void codecTest(final List<Byte> values) throws IOException {
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<Byte> writeCodec = new ExternalByteCodec(null, os);

            for (final byte value : values) {
                writeCodec.write(value);
            }

            final List<Byte> actual = new ArrayList<>(values.size());
            try (final InputStream is = new ByteArrayInputStream(os.toByteArray())) {
                final CRAMCodec<Byte> readCodec = new ExternalByteCodec(is, null);

                for (int i = 0; i < values.size(); i++) {
                    actual.add(readCodec.read());
                }
            }

            Assert.assertEquals(actual, values);
        }
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void readWithLength() throws IOException {
        try (final InputStream is = new ByteArrayInputStream(new byte[0])) {
            final CRAMCodec<Byte> readCodec = new ExternalByteCodec(is, null);

            readCodec.read(1);
        }
    }
}