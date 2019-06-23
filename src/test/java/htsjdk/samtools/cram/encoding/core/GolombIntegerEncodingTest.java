package htsjdk.samtools.cram.encoding.core;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class GolombIntegerEncodingTest extends HtsjdkTest {
    @Test
    public void testToString() {
        final GammaIntegerEncoding encoding = new GammaIntegerEncoding(3);
        Assert.assertTrue(encoding.toString().contains("3"));
    }
}
