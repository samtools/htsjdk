package htsjdk.tribble.readers;

import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.TestUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author mccowan
 */
public class LongLineBufferedReaderTest {

    /**
     * Test that we read the correct number of lines
     * from a file
     * @throws Exception
     */
    @Test
    public void testReadLines() throws Exception {
        String filePath = TestUtils.DATA_DIR + "large.txt";
        BufferedReader reader = new BufferedReader(new InputStreamReader(IOUtil.getInputStream(IOUtil.getFile(filePath))));
        LongLineBufferedReader testReader = new LongLineBufferedReader(new InputStreamReader(IOUtil.getInputStream(IOUtil.getFile(filePath))));
        String line;
        while((line = reader.readLine()) != null){
            Assert.assertEquals(testReader.readLine(), line);
        }
        Assert.assertNull(testReader.readLine());
    }
}
