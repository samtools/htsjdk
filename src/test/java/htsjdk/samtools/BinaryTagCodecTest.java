package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import java.util.LinkedHashMap;
import java.util.Map;
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

    // A record may carry a tag twice. As in SAM text, the later value is kept, and the record's other tags must be too.

    /** Decodes a run of A-type tags, each given as its two-letter name followed by its one-character value. */
    private static Map<String, Object> decodeCharTags(final String... tagsAndValues) {
        final byte[] bytes = new byte[tagsAndValues.length * 4];
        for (int i = 0; i < tagsAndValues.length; i++) {
            bytes[i * 4] = (byte) tagsAndValues[i].charAt(0);
            bytes[i * 4 + 1] = (byte) tagsAndValues[i].charAt(1);
            bytes[i * 4 + 2] = 'A';
            bytes[i * 4 + 3] = (byte) tagsAndValues[i].charAt(2);
        }
        final Map<String, Object> decoded = new LinkedHashMap<>();
        for (SAMBinaryTagAndValue tag = BinaryTagCodec.readTags(bytes, 0, bytes.length, ValidationStringency.STRICT);
                tag != null;
                tag = tag.getNext()) {
            decoded.put(SAMTag.makeStringTag(tag.tag), tag.value);
        }
        return decoded;
    }

    @Test
    public void testTagsAfterADuplicateOfTheLastTagAreKept() {
        Assert.assertEquals(decodeCharTags("XAa", "XAb", "XBc"), Map.of("XA", 'b', "XB", 'c'));
    }

    @Test
    public void testTagsAfterADuplicateOfTheLastTagAreKeptWhenTagsAreOutOfOrder() {
        Assert.assertEquals(decodeCharTags("XBb", "XAa", "XBc", "XCd"), Map.of("XA", 'a', "XB", 'c', "XC", 'd'));
    }

    @Test
    public void testADuplicateOfAnEarlierTagKeepsTheLaterValue() {
        Assert.assertEquals(decodeCharTags("XAa", "XCc", "XAb", "XDd"), Map.of("XA", 'b', "XC", 'c', "XD", 'd'));
    }
}
