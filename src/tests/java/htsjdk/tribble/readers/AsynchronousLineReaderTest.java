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
public class AsynchronousLineReaderTest {

        /**
         * Test that we read the correct number of lines
         * from a file
         * @throws Exception
         */
        @Test
        public void testReadLines() throws Exception {
            final File filePath = new File(TestUtils.DATA_DIR + "large.txt");
            final AsynchronousLineReader reader = new AsynchronousLineReader(new InputStreamReader( IOUtil.getInputStream(filePath)));
            final BufferedReader br = new BufferedReader(new InputStreamReader(IOUtil.getInputStream(filePath)));

            String nextLine;
            while((nextLine = br.readLine()) != null){
                Assert.assertEquals(nextLine, reader.readLine());
            }
            Assert.assertNull(reader.readLine());
        }
}
