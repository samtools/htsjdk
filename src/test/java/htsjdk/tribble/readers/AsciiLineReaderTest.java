package htsjdk.tribble.readers;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BlockCompressedInputStream;
import java.io.ByteArrayInputStream;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Verifies the deprecated {@link AsciiLineReader} shim still compiles and delegates correctly. */
public class AsciiLineReaderTest extends HtsjdkTest {

    @Test
    public void deprecatedFromReturnsAnAsciiLineReader() throws Exception {
        final AsciiLineReader reader = AsciiLineReader.from(new ByteArrayInputStream("hello\n".getBytes()));
        Assert.assertTrue(reader instanceof Utf8LineReader);
        Assert.assertEquals(reader.readLine(), "hello");
        Assert.assertNull(reader.readLine());
    }

    @Test
    public void deprecatedFromOnBgzfReturnsAnAsciiLineReader() {
        final AsciiLineReader reader =
                AsciiLineReader.from(new BlockCompressedInputStream(new ByteArrayInputStream(new byte[10])));
        Assert.assertTrue(reader instanceof AsciiLineReader);
    }
}
