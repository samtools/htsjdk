package htsjdk.samtools.cram.encoding.core;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CanoncialHuffmanByteEncodingTest extends HtsjdkTest {
    @Test
    public void testToString() {
        final byte valueSize[] = new byte[] { 2 };
        final int bitLengths[] = new int[] { 3 };

        final CanonicalHuffmanByteEncoding encoding = new CanonicalHuffmanByteEncoding(valueSize, bitLengths);
        Assert.assertTrue(encoding.toString().contains("2"));
        Assert.assertTrue(encoding.toString().contains("3"));
    }
}
