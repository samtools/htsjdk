package htsjdk.samtools.cram.encoding.core;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.core.experimental.GolombRiceIntegerEncoding;
import org.testng.Assert;
import org.testng.annotations.Test;

public class GolombRiceIntegerEncodingTest extends HtsjdkTest {
    @Test
    public void testToString() {
        final GolombRiceIntegerEncoding encoding = new GolombRiceIntegerEncoding(3, 4);
        Assert.assertTrue(encoding.toString().contains("3"));
    }
}
