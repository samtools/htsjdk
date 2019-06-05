package htsjdk.samtools.cram.encoding.core;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.core.experimental.GolombLongEncoding;
import org.testng.Assert;
import org.testng.annotations.Test;

public class GolombLongEncodingTest extends HtsjdkTest {
    @Test
    public void testToString() {
        final GolombLongEncoding encoding = new GolombLongEncoding(3, 4);
        Assert.assertTrue(encoding.toString().contains("3"));
    }
}
