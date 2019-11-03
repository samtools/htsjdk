package htsjdk.samtools.cram.encoding.core;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CanonicalHuffmanIntegerEncodingTest extends HtsjdkTest {
    @Test
    public void testToString() {
        final int valueSize[] = new int[] { 2 };
        final int bitLengths[] = new int[] { 3 };

        final CanonicalHuffmanIntegerEncoding encoding = new CanonicalHuffmanIntegerEncoding(valueSize, bitLengths);
        Assert.assertTrue(encoding.toString().contains("2"));
        Assert.assertTrue(encoding.toString().contains("3"));
    }
}
