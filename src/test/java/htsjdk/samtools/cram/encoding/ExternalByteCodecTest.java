package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
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
            final BitCodec<Byte> writeCodec = new ExternalByteCodec(os, null);

            for (final byte value : values) {
                // this parameter is not used - the external block is set in the constructor
                writeCodec.write(null, value);
            }

            final List<Byte> actual = new ArrayList<>(values.size());
            try (final InputStream is = new ByteArrayInputStream(os.toByteArray())) {
                final BitCodec<Byte> readCodec = new ExternalByteCodec(null, is);

                for (int i = 0; i < values.size(); i++) {
                    // this parameter is not used - the external block is set in the constructor
                    actual.add(readCodec.read(null));
                }
            }

            Assert.assertEquals(actual, values);
        }
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void readWithLength() throws IOException {
        try (final InputStream is = new ByteArrayInputStream(new byte[0])) {
            final BitCodec<Byte> readCodec = new ExternalByteCodec(null, is);

            // this parameter is not used - the external block is set in the constructor
            readCodec.read(null, 1);
        }
    }
}