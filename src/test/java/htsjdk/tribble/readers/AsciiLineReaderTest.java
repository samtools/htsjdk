package htsjdk.tribble.readers;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.TestUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * User: jacob
 * Date: 2012/05/09
 */
public class AsciiLineReaderTest extends HtsjdkTest {
    /**
     * Test that we read the correct number of lines
     * from a file
     * @throws Exception
     */
    @Test
    public void testReadLines() throws Exception {
        String filePath = TestUtils.DATA_DIR + "gwas/smallp.gwas";
        InputStream is = new FileInputStream(filePath);
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

    @Test public void voidTestLineEndingLength() throws Exception {
        final String input = "Hello\nThis\rIs A Silly Test\r\nSo There";
        final InputStream is = new ByteArrayInputStream(input.getBytes());
        final AsciiLineReader in = new AsciiLineReader(is);

        Assert.assertEquals(in.getLineTerminatorLength(), -1);
        Assert.assertEquals(in.readLine(), "Hello");
        Assert.assertEquals(in.getLineTerminatorLength(), 1);
        Assert.assertEquals(in.readLine(), "This");
        Assert.assertEquals(in.getLineTerminatorLength(), 1);
        Assert.assertEquals(in.readLine(), "Is A Silly Test");
        Assert.assertEquals(in.getLineTerminatorLength(), 2);
        Assert.assertEquals(in.readLine(), "So There");
        Assert.assertEquals(in.getLineTerminatorLength(), 0);
    }

    @Test public void voidTestLineEndingLengthAtEof() throws Exception {
        final String input = "Hello\nWorld\r\n";
        final InputStream is = new ByteArrayInputStream(input.getBytes());
        final AsciiLineReader in = new AsciiLineReader(is);

        Assert.assertEquals(in.getLineTerminatorLength(), -1);
        Assert.assertEquals(in.readLine(), "Hello");
        Assert.assertEquals(in.getLineTerminatorLength(), 1);
        Assert.assertEquals(in.readLine(), "World");
        Assert.assertEquals(in.getLineTerminatorLength(), 2);
    }
}
