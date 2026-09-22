package htsjdk.tribble.readers;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.TestUtils;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author mccowan
 */
public class SynchronousLineReaderUnitTest extends HtsjdkTest {
    @Test
    public void testLineReaderIterator_streamConstructor() throws Exception {
        final Path filePath = Paths.get(TestUtils.DATA_DIR + "gwas/smallp.gwas");
        final LineIterator lineIterator = new LineIteratorImpl(
                new SynchronousLineReader(new PositionalBufferedStream(Files.newInputStream(filePath))));
        final BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(filePath)));

        while (lineIterator.hasNext()) {
            Assert.assertEquals(lineIterator.next(), br.readLine());
        }
        Assert.assertNull(br.readLine());
    }

    @Test
    public void testLineReaderIterator_readerConstructor() throws Exception {
        final Path filePath = Paths.get(TestUtils.DATA_DIR + "gwas/smallp.gwas");
        final LineIterator lineIterator = new LineIteratorImpl(new SynchronousLineReader(
                new InputStreamReader(new PositionalBufferedStream(Files.newInputStream(filePath)))));
        final BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(filePath)));

        while (lineIterator.hasNext()) {
            Assert.assertEquals(lineIterator.next(), br.readLine());
        }
        Assert.assertNull(br.readLine());
    }

    // UTF-8 decoding

    @Test
    public void utf8DecodesCorrectlyViaInputStream() {
        final byte[] utf8 = "café\nSchön\n".getBytes(StandardCharsets.UTF_8);
        final SynchronousLineReader reader = new SynchronousLineReader(new ByteArrayInputStream(utf8));
        Assert.assertEquals(reader.readLine(), "café");
        Assert.assertEquals(reader.readLine(), "Schön");
        Assert.assertNull(reader.readLine());
    }

    @Test
    public void bareLatin1ByteDecodesAsReplacementCharacter() {
        // 0xE9 alone is not valid UTF-8
        final byte[] bytes = {'h', 'i', (byte) 0xE9, '\n'};
        final SynchronousLineReader reader = new SynchronousLineReader(new ByteArrayInputStream(bytes));
        Assert.assertEquals(reader.readLine(), "hi�");
    }
}
