package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.CRAMByteReader;
import htsjdk.samtools.cram.io.CRAMByteWriter;
import htsjdk.samtools.cram.io.IOTestCases;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ExternalIntegerCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testInt32Lists", dataProviderClass = IOTestCases.class)
    public void codecTest(final List<Integer> values) throws IOException {
        final CRAMByteWriter os = new CRAMByteWriter();
        final CRAMCodec<Integer> writeCodec = new ExternalIntegerCodec(null, os);

        for (final int value : values) {
            writeCodec.write(value);
        }
        final byte[] written = os.toByteArray();

        final List<Integer> actual = new ArrayList<>(values.size());
        final CRAMByteReader is = new CRAMByteReader(written);
        final CRAMCodec<Integer> readCodec = new ExternalIntegerCodec(is, null);

        for (int i = 0; i < values.size(); i++) {
            actual.add(readCodec.read());
        }

        Assert.assertEquals(actual, values);
    }
}
