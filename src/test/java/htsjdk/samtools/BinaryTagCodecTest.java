package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BinaryTagCodecTest extends HtsjdkTest {

    @Test
    public void testCharTagAbove7FIsReadAsTheCharItWasWrittenFrom() {
        // XC:A with the byte 0xE9, "é" in ISO-8859-1.
        final byte[] tag = {'X', 'C', 'A', (byte) 0xE9};
        final SAMBinaryTagAndValue decoded = BinaryTagCodec.readTags(tag, 0, tag.length, ValidationStringency.STRICT);
        Assert.assertEquals(decoded.value, 'é');
    }
}
