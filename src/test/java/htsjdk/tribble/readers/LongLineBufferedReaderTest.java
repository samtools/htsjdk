package htsjdk.tribble.readers;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.TestUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author mccowan
 */
public class LongLineBufferedReaderTest extends HtsjdkTest {

    /**
     * Test that we read the correct number of lines
     * from a file
     * @throws Exception
     */
    @Test
    public void testReadLines() throws Exception {
        Path filePath = Path.of(TestUtils.DATA_DIR, "large.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(filePath)));
        LongLineBufferedReader testReader =
                new LongLineBufferedReader(new InputStreamReader(Files.newInputStream(filePath)));
        String line;
        while ((line = reader.readLine()) != null) {
            Assert.assertEquals(testReader.readLine(), line);
        }
        Assert.assertNull(testReader.readLine());
    }
}
