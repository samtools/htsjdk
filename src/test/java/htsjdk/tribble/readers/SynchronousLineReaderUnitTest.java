package htsjdk.tribble.readers;

import htsjdk.tribble.TestUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * @author mccowan
 */
public class SynchronousLineReaderUnitTest {
    @Test
    public void testLineReaderIterator_streamConstructor() throws Exception {
        final File filePath = new File(TestUtils.DATA_DIR + "gwas/smallp.gwas");
        final LineIterator lineIterator = new LineIteratorImpl(new SynchronousLineReader(new PositionalBufferedStream(new FileInputStream(filePath))));
        final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)));

        while (lineIterator.hasNext()) {
            Assert.assertEquals(lineIterator.next(), br.readLine());
        }
        Assert.assertNull(br.readLine());
    }

    @Test
    public void testLineReaderIterator_readerConstructor() throws Exception {
        final File filePath = new File(TestUtils.DATA_DIR + "gwas/smallp.gwas");
        final LineIterator lineIterator = new LineIteratorImpl(new SynchronousLineReader(new InputStreamReader(new PositionalBufferedStream(new FileInputStream(filePath)))));
        final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)));

        while (lineIterator.hasNext()) {
            Assert.assertEquals(lineIterator.next(), br.readLine());
        }
        Assert.assertNull(br.readLine());
    }
}
