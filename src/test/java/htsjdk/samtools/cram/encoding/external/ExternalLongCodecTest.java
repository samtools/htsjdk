package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.IOTestCases;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExternalLongCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testInt64Lists", dataProviderClass = IOTestCases.class)
    public void codecTest(final List<Long> values) throws IOException {
        byte[] written;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<Long> writeCodec = new ExternalLongCodec(null, os);

            for (final long value : values) {
                writeCodec.write(value);
            }
            os.flush();
            written = os.toByteArray();
        }

        final List<Long> actual = new ArrayList<>(values.size());
        try (final ByteArrayInputStream is = new ByteArrayInputStream(written)) {
            final CRAMCodec<Long> readCodec = new ExternalLongCodec(is, null);

            for (int i = 0; i < values.size(); i++) {
                actual.add(readCodec.read());
            }
        }

        Assert.assertEquals(actual, values);
    }
}