package htsjdk.tribble.readers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.TestUtils;

import java.io.InputStream;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * User: jacob
 * Date: 2012/05/09
 */
public class AsciiLineReaderTest {
    @BeforeMethod
    public void setUp() throws Exception {

    }

    @AfterMethod
    public void tearDown() throws Exception {

    }

    /**
     * Test that we read the correct number of lines
     * from a file
     * @throws Exception
     */
    @Test
    public void testReadLines() throws Exception {
        String filePath = TestUtils.DATA_DIR + "gwas/smallp.gwas";
        InputStream is = IOUtil.getInputStream(IOUtil.getFile(filePath));
        AsciiLineReader reader = new AsciiLineReader(is);
        int actualLines = 0;
        int expectedNumber = 20;
        String nextLine = "";

        while((nextLine = reader.readLine()) != null && actualLines < (expectedNumber + 5)){
            actualLines++;
            //This particular test file has no empty lines
            assertTrue(nextLine.length() > 0);
        }

        assertEquals(expectedNumber, actualLines);

    }
}
