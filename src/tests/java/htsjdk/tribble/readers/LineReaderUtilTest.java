package htsjdk.tribble.readers;

import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.TestUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author mccowan
 */
public class LineReaderUtilTest {
    @Test
    public void testLineReaderIterator() throws Exception {
        final File filePath = new File(TestUtils.DATA_DIR + "gwas/smallp.gwas");
        final LineIterator lineIterator = new LineIteratorImpl(LineReaderUtil.fromBufferedStream(new PositionalBufferedStream(IOUtil.getInputStream(filePath))));
        final BufferedReader br = new BufferedReader(new InputStreamReader(IOUtil.getInputStream(filePath)));

        while (lineIterator.hasNext()) {
            Assert.assertEquals(lineIterator.next(), br.readLine());
        }
        Assert.assertNull(br.readLine());
    }
}
