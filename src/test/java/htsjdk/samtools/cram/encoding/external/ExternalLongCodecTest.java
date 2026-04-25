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

public class ExternalLongCodecTest extends HtsjdkTest {

    @Test(dataProvider = "testInt64Lists", dataProviderClass = IOTestCases.class)
    public void codecTest(final List<Long> values) throws IOException {
        final CRAMByteWriter os = new CRAMByteWriter();
        final CRAMCodec<Long> writeCodec = new ExternalLongCodec(null, os);

        for (final long value : values) {
            writeCodec.write(value);
        }
        final byte[] written = os.toByteArray();

        final List<Long> actual = new ArrayList<>(values.size());
        final CRAMByteReader is = new CRAMByteReader(written);
        final CRAMCodec<Long> readCodec = new ExternalLongCodec(is, null);

        for (int i = 0; i < values.size(); i++) {
            actual.add(readCodec.read());
        }

        Assert.assertEquals(actual, values);
    }
}
