package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExternalIntegerCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testInt32Lists", dataProviderClass = IOTestCases.class)
    public void codecTest(final List<Integer> values) throws IOException {
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<Integer> writeCodec = new ExternalIntegerCodec(null, os);

            for (final int value : values) {
                writeCodec.write(value);
            }

            final List<Integer> actual = new ArrayList<>(values.size());
            try (final ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray())) {
                final CRAMCodec<Integer> readCodec = new ExternalIntegerCodec(is, null);

                for (int i = 0; i < values.size(); i++) {
                    actual.add(readCodec.read());
                }
            }

            Assert.assertEquals(actual, values);
        }
    }
}