package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Map;

public class TextTagCodecTest extends HtsjdkTest {

    @DataProvider
    public Object[][] getArraysToEncode(){
        return new Object[][]{
                {new byte[0], true, "xa:B:c"},
                {new byte[0], false, "xa:B:C"},
                {new byte[]{1, 2, 3}, true, "xa:B:c,1,2,3"},
                {new byte[]{1, 2, 3}, false, "xa:B:C,1,2,3"},

                {new short[0], true, "xa:B:s"},
                {new short[0], false, "xa:B:S"},
                {new short[]{1, 2, 3}, true, "xa:B:s,1,2,3"},
                {new short[]{1, 2, 3}, false, "xa:B:S,1,2,3"},

                {new int[0], true, "xa:B:i"},
                {new int[0], false, "xa:B:I"},
                {new int[]{1, 2, 3}, true, "xa:B:i,1,2,3"},
                {new int[]{1, 2, 3}, false, "xa:B:I,1,2,3"},

                {new float[0], true, "xa:B:f"},
                {new float[]{1.0f, 2.0f, 3.0f}, true, "xa:B:f,1.0,2.0,3.0"},

        };
    }

    @Test(dataProvider = "getArraysToEncode")
    public void testEmptyAndNonEmptyArrayEncoding(Object array, boolean isSigned, String expectedTag){
        final TextTagCodec textTagCodec = new TextTagCodec();
        final String tagName = "xa";
        final String encodedTag = isSigned
                ? textTagCodec.encode(tagName, array)
                : textTagCodec.encodeUnsignedArray(tagName, array);
        Assert.assertEquals(encodedTag, expectedTag);

    }

    @DataProvider
    public Object[][] getArraysToDecode(){
        return new Object[][]{
                {"xa:B:c", new byte[0]},
                {"xa:B:C", new TagValueAndUnsignedArrayFlag(new byte[0], true)},
                {"xa:B:c,1,2,3", new byte[]{1, 2, 3}},
                {"xa:B:C,1,2,3", new TagValueAndUnsignedArrayFlag(new byte[]{1, 2, 3}, true)},
                {"xa:B:c,", new byte[0]},
                {"xa:B:C,", new TagValueAndUnsignedArrayFlag(new byte[0], true)},

                {"xa:B:s", new short[0]},
                {"xa:B:S", new TagValueAndUnsignedArrayFlag(new short[0], true)},
                {"xa:B:s,1,2,3", new short[]{1, 2, 3}},
                {"xa:B:S,1,2,3", new TagValueAndUnsignedArrayFlag(new short[]{1, 2, 3}, true)},
                {"xa:B:s,", new short[0]},
                {"xa:B:S,", new TagValueAndUnsignedArrayFlag(new short[0], true)},

                {"xa:B:i", new int[0]},
                {"xa:B:I", new TagValueAndUnsignedArrayFlag(new int[0], true)},
                {"xa:B:i,1,2,3", new int[]{1, 2, 3}},
                {"xa:B:I,1,2,3", new TagValueAndUnsignedArrayFlag(new int[]{1, 2, 3}, true)},
                {"xa:B:i,", new int[0]},
                {"xa:B:I,", new TagValueAndUnsignedArrayFlag(new int[0], true)},

                {"xa:B:f", new float[0]},
                {"xa:B:f,", new float[0]},
                {"xa:B:f,1.0,2.0,3.0", new float[]{1.0f, 2.0f, 3.0f}},
        };
    }

    @Test(dataProvider = "getArraysToDecode")
    public void testEmptyAndNonEmptyArrayDecoding(String tag, Object expectedValue) {
        final TextTagCodec textTagCodec = new TextTagCodec();
        final Map.Entry<String, Object> decoded = textTagCodec.decode(tag);

        Assert.assertEquals(decoded.getKey(), "xa");

        final Object value = decoded.getValue();
        if( value instanceof TagValueAndUnsignedArrayFlag){
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
    public Object[][] getBadArrayTags(){
        return new Object[][]{
                {"xa:B"}, // no colon
                {"xa:B:F"}, // there is no such thing as unsigned floating point
                {"xa:B:F,1.0,2.0"}, // same as above but empty arrays have a special path
                {"xa:B:,1,2,3"}, // missing type
                {"xa:B:c,700"}, //out of bounds
                {"xa:B:C,-10"}, // negative in unsigned array
        };
    }

    @Test(dataProvider = "getBadArrayTags", expectedExceptions = SAMFormatException.class)
    public void testBadArrayTags(String badTag){
        final TextTagCodec textTagCodec = new TextTagCodec();
        textTagCodec.decode(badTag);
    }
}