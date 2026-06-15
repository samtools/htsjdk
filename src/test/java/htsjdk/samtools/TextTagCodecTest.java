package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TextTagCodecTest extends HtsjdkTest {

    @DataProvider
    public Object[][] getArraysToEncode() {
        return new Object[][] {
            {new byte[0], true, "xa:B:c"},
            {new byte[0], false, "xa:B:C"},
            {new byte[] {1, 2, 3}, true, "xa:B:c,1,2,3"},
            {new byte[] {1, 2, 3}, false, "xa:B:C,1,2,3"},
            {new short[0], true, "xa:B:s"},
            {new short[0], false, "xa:B:S"},
            {new short[] {1, 2, 3}, true, "xa:B:s,1,2,3"},
            {new short[] {1, 2, 3}, false, "xa:B:S,1,2,3"},
            {new int[0], true, "xa:B:i"},
            {new int[0], false, "xa:B:I"},
            {new int[] {1, 2, 3}, true, "xa:B:i,1,2,3"},
            {new int[] {1, 2, 3}, false, "xa:B:I,1,2,3"},
            {new float[0], true, "xa:B:f"},
            {new float[] {1.0f, 2.0f, 3.0f}, true, "xa:B:f,1.0,2.0,3.0"},
        };
    }

    @Test(dataProvider = "getArraysToEncode")
    public void testEmptyAndNonEmptyArrayEncoding(Object array, boolean isSigned, String expectedTag) {
        final TextTagCodec textTagCodec = new TextTagCodec();
        final String tagName = "xa";
        final String encodedTag =
                isSigned ? textTagCodec.encode(tagName, array) : textTagCodec.encodeUnsignedArray(tagName, array);
        Assert.assertEquals(encodedTag, expectedTag);
    }

    @DataProvider
    public Object[][] getArraysToDecode() {
        return new Object[][] {
            {"xa:B:c", new byte[0]},
            {"xa:B:C", new TagValueAndUnsignedArrayFlag(new byte[0], true)},
            {"xa:B:c,1,2,3", new byte[] {1, 2, 3}},
            {"xa:B:C,1,2,3", new TagValueAndUnsignedArrayFlag(new byte[] {1, 2, 3}, true)},
            {"xa:B:c,", new byte[0]},
            {"xa:B:C,", new TagValueAndUnsignedArrayFlag(new byte[0], true)},
            {"xa:B:s", new short[0]},
            {"xa:B:S", new TagValueAndUnsignedArrayFlag(new short[0], true)},
            {"xa:B:s,1,2,3", new short[] {1, 2, 3}},
            {"xa:B:S,1,2,3", new TagValueAndUnsignedArrayFlag(new short[] {1, 2, 3}, true)},
            {"xa:B:s,", new short[0]},
            {"xa:B:S,", new TagValueAndUnsignedArrayFlag(new short[0], true)},
            {"xa:B:i", new int[0]},
            {"xa:B:I", new TagValueAndUnsignedArrayFlag(new int[0], true)},
            {"xa:B:i,1,2,3", new int[] {1, 2, 3}},
            {"xa:B:I,1,2,3", new TagValueAndUnsignedArrayFlag(new int[] {1, 2, 3}, true)},
            {"xa:B:i,", new int[0]},
            {"xa:B:I,", new TagValueAndUnsignedArrayFlag(new int[0], true)},
            {"xa:B:f", new float[0]},
            {"xa:B:f,", new float[0]},
            {"xa:B:f,1.0,2.0,3.0", new float[] {1.0f, 2.0f, 3.0f}},
        };
    }

    @Test(dataProvider = "getArraysToDecode")
    public void testEmptyAndNonEmptyArrayDecoding(String tag, Object expectedValue) {
        final TextTagCodec textTagCodec = new TextTagCodec();
        final Map.Entry<String, Object> decoded = textTagCodec.decode(tag);

        Assert.assertEquals(decoded.getKey(), "xa");

        final Object value = decoded.getValue();
        if (value instanceof TagValueAndUnsignedArrayFlag) {
            Assert.assertTrue(expectedValue instanceof TagValueAndUnsignedArrayFlag);
            final TagValueAndUnsignedArrayFlag typedValue = (TagValueAndUnsignedArrayFlag) value;
            final TagValueAndUnsignedArrayFlag typedExpected = (TagValueAndUnsignedArrayFlag) expectedValue;
            Assert.assertEquals(typedValue.value, typedExpected.value);
            Assert.assertEquals(typedValue.isUnsignedArray, typedExpected.isUnsignedArray);
        } else {
            Assert.assertEquals(value, expectedValue);
        }
    }

    @DataProvider
    public Object[][] getBadArrayTags() {
        return new Object[][] {
            {"xa:B"}, // no colon
            {"xa:B:F"}, // there is no such thing as unsigned floating point
            {"xa:B:F,1.0,2.0"}, // same as above but empty arrays have a special path
            {"xa:B:,1,2,3"}, // missing type
            {"xa:B:c,700"}, // out of bounds
            {"xa:B:C,-10"}, // negative in unsigned array
        };
    }

    @Test(dataProvider = "getBadArrayTags", expectedExceptions = SAMFormatException.class)
    public void testBadArrayTags(String badTag) {
        final TextTagCodec textTagCodec = new TextTagCodec();
        textTagCodec.decode(badTag);
    }

    // -- byte-based decodeValue: malformed-tag boundary cases (regression tests for crash on len < 5) ---

    @Test
    public void testDecodeValueBytesLen4ZeroValueZTag() {
        // "XY:Z" (no trailing colon) is treated as a Z-type with empty value, mirroring the
        // String overload. Previously the byte overload crashed with NegativeArraySize because
        // it computed valueLen = len - 5 = -1.
        final TextTagCodec codec = new TextTagCodec();
        final byte[] buf = "XY:Z".getBytes();
        codec.decodeValue(buf, 0, buf.length);
        Assert.assertEquals(codec.getLastValue(), "");
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testDecodeValueBytesRejectsLen3Tag() {
        final TextTagCodec codec = new TextTagCodec();
        final byte[] buf = "XY:".getBytes();
        codec.decodeValue(buf, 0, buf.length);
    }

    @Test
    public void testDecodeValueBytesAcceptsEmptyZValue() {
        // "XY:Z:" -- valid: Z-type with empty string value.
        final TextTagCodec codec = new TextTagCodec();
        final byte[] buf = "XY:Z:".getBytes();
        codec.decodeValue(buf, 0, buf.length);
        Assert.assertEquals(codec.getLastValue(), "");
    }

    // --- byte-based decodeValue: unsigned 32-bit i-type values via the byte path -----------------

    @Test
    public void testDecodeValueBytesUnsignedIntMax() {
        // 4294967295 is the largest spec-allowed i value; it doesn't fit in a signed int and
        // should be returned as a Long.
        final TextTagCodec codec = new TextTagCodec();
        final byte[] buf = "XX:i:4294967295".getBytes();
        codec.decodeValue(buf, 0, buf.length);
        Assert.assertEquals(codec.getLastValue(), 4294967295L);
    }

    @Test
    public void testDecodeValueBytesSignedIntMinAsLong() {
        final TextTagCodec codec = new TextTagCodec();
        final byte[] buf = "XX:i:-2147483648".getBytes();
        codec.decodeValue(buf, 0, buf.length);
        Assert.assertEquals(codec.getLastValue(), Integer.MIN_VALUE);
    }
}
