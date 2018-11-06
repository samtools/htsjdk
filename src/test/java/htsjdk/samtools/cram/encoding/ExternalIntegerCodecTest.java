package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.io.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ExternalIntegerCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testInt32Lists", dataProviderClass = IOTestCases.class)
    public void codecTest(final List<Integer> values) throws IOException {
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final BitCodec<Integer> writeCodec = new ExternalIntegerCodec(os, null);

            for (final int value : values) {
                // this parameter is not used - the external block is set in the constructor
                writeCodec.write(null, value);
            }

            final List<Integer> actual = new ArrayList<>(values.size());
            try (final InputStream is = new ByteArrayInputStream(os.toByteArray())) {
                final BitCodec<Integer> readCodec = new ExternalIntegerCodec(null, is);

                for (int i = 0; i < values.size(); i++) {
                    // this parameter is not used - the external block is set in the constructor
                    actual.add(readCodec.read(null));
                }
            }

            Assert.assertEquals(actual, values);
        }
    }
}