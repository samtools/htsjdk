package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.RuntimeEOFException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class InputStreamUtilsTest extends HtsjdkTest {

    @Test
    public void testSkipFully() throws IOException {
        byte[] data = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 };
        // use a BufferedInputStream with a small buffer to check that skipFully will fill the buffer as needed
        InputStream in = new BufferedInputStream(new ByteArrayInputStream(data), 4);
        InputStreamUtils.skipFully(in, 6);
        Assert.assertEquals(in.read(), 6);
        Assert.assertEquals(in.read(), 7);
        Assert.assertEquals(in.read(), -1); // EOF
    }

    @Test(expectedExceptions = RuntimeEOFException.class)
    public void testSkipFullyPastEOF() {
        byte[] data = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 };
        InputStream in = new BufferedInputStream(new ByteArrayInputStream(data), 4);
        InputStreamUtils.skipFully(in, 10);
    }
}
