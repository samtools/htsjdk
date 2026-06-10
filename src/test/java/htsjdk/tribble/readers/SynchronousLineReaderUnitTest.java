package htsjdk.tribble.readers;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.TestUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
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
}
