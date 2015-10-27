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
public class AsynchronousLineReaderTest {

        /**
         * Test that we read the correct number of lines
         * from a file
         * @throws Exception
         */
        @Test
        public void testReadLines() throws Exception {
            final File filePath = new File(TestUtils.DATA_DIR + "large.txt");
            final AsynchronousLineReader reader = new AsynchronousLineReader(new InputStreamReader( new FileInputStream(filePath)));
            final BufferedReader br = new BufferedReader(new InputStreamReader( new FileInputStream(filePath)));

            String nextLine;
            while((nextLine = br.readLine()) != null){
                Assert.assertEquals(nextLine, reader.readLine());
            }
            Assert.assertNull(reader.readLine());
        }
}
