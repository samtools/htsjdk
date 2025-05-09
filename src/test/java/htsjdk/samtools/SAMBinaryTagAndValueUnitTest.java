package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BinaryCodec;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SAMBinaryTagAndValueUnitTest extends HtsjdkTest {

    @DataProvider(name="allowedAttributeTypes")
    public Object[][] allowedTypes() {
        return  new Object[][] {
                {"a string"},
                {Byte.valueOf((byte) 7)},
                {Short.valueOf((short) 8)},
                {Integer.valueOf(0)},
                {Character.valueOf('C')},
                {Float.valueOf(0.1F)},
                // unsigned longs
                {Long.valueOf(0)},
                {Long.valueOf(BinaryCodec.MAX_UINT)},
                // signed longs
                {Long.valueOf(-1L)},
                {Long.valueOf(Integer.MAX_VALUE)},
                {Long.valueOf(Integer.MIN_VALUE)},
                // array values
                {new byte[]{0, 1, 2}},
                {new short[]{3, 4, 5}},
                {new int[]{6, 7, 8}},
                {new float[]{0.1F, 0.2F}},
        };
    }

    @Test(dataProvider="allowedAttributeTypes")
    public void test_isAllowedAttribute(final Object value) {
        Assert.assertTrue(SAMBinaryTagAndValue.isAllowedAttributeValue(value));
    }

    @Test(dataProvider="allowedAttributeTypes")
    public void test_isAllowedConstructor(final Object value) {
        Assert.assertNotNull(new SAMBinaryTagAndValue(SAMTag.makeBinaryTag("UI"), value));
    }

    @DataProvider(name="notAllowedAttributeTypes")
    public Object[][] notAllowedTypes() {
        return  new Object[][] {
                {Long.valueOf(BinaryCodec.MAX_UINT + 1L)},
                {Long.valueOf(Integer.MIN_VALUE - 1L)},
                {Double.valueOf(0.3F)},
                {new Object()},
                {new Object[]{}},
                {new Integer[]{}}
        };
    }

    @Test(dataProvider="notAllowedAttributeTypes")
    public void test_isNotAllowedAttribute(final Object value) {
        Assert.assertFalse(SAMBinaryTagAndValue.isAllowedAttributeValue(value));
    }

    @Test(dataProvider="notAllowedAttributeTypes", expectedExceptions=IllegalArgumentException.class)
    public void test_isNotAllowedConstructor(final Object value) {
        new SAMBinaryTagAndValue(SAMTag.makeBinaryTag("ZZ"), value);
    }

    @DataProvider(name="allowedUnsignedArrayTypes")
    public Object[][] allowedUnsignedArrayTypes() {
        return  new Object[][] {
                {new byte[]{0, 1, 2}},
                {new short[]{3, 4, 5}},
                {new int[]{6, 7, 8}},
        };
    }

    @Test(dataProvider="allowedUnsignedArrayTypes")
    public void test_isAllowedUnsignedArrayAttribute(final Object value) {
        final short binaryTag = SAMTag.makeBinaryTag("UI");
        new SAMBinaryTagAndUnsignedArrayValue(binaryTag, value);
    }

    @DataProvider(name="notAllowedUnsignedArrayTypes")
    public Object[][] notAllowedUnsignedArrayTypes() {
        return  new Object[][] {
                {new float[]{0.1F, 0.2F}},
                {new Object[]{}}
        };
    }

    @Test(dataProvider="notAllowedUnsignedArrayTypes", expectedExceptions=IllegalArgumentException.class)
    public void test_isNotAllowedUnsignedArrayAttribute(final Object value) {
        final short binaryTag = SAMTag.makeBinaryTag("UI");
        new SAMBinaryTagAndUnsignedArrayValue(binaryTag, value);
    }

    @DataProvider(name="hashCopyEquals")
    public Object[][] hashCopyEquals() {
        final short tag = SAMTag.makeBinaryTag("UI");
        return new Object[][] {
                {new SAMBinaryTagAndValue(tag, "a string"), new SAMBinaryTagAndValue(tag, "a string"), true, true},
                {new SAMBinaryTagAndValue(tag, "a string"), new SAMBinaryTagAndValue(tag, "different string"), false, false},

                {new SAMBinaryTagAndValue(tag, Byte.valueOf((byte) 0)), new SAMBinaryTagAndValue(tag, Byte.valueOf((byte) 0)), true, true},
                {new SAMBinaryTagAndValue(tag, Byte.valueOf((byte) 0)), new SAMBinaryTagAndValue(tag, Byte.valueOf((byte) 1)), false, false},

                {new SAMBinaryTagAndValue(tag, Short.valueOf((short) 0)), new SAMBinaryTagAndValue(tag, Short.valueOf((short) 0)), true, true},
                {new SAMBinaryTagAndValue(tag, Short.valueOf((short) 0)), new SAMBinaryTagAndValue(tag, Short.valueOf((short) 1)), false, false},

                {new SAMBinaryTagAndValue(tag, Integer.valueOf(0)), new SAMBinaryTagAndValue(tag, Integer.valueOf(0)), true, true},
                {new SAMBinaryTagAndValue(tag, Integer.valueOf(0)), new SAMBinaryTagAndValue(tag, Integer.valueOf(0)), true, true},

                {new SAMBinaryTagAndValue(tag, Character.valueOf('C')), new SAMBinaryTagAndValue(tag, Character.valueOf('C')), true, true},
                {new SAMBinaryTagAndValue(tag, Character.valueOf('C')), new SAMBinaryTagAndValue(tag, Character.valueOf('D')), false, false},

                {new SAMBinaryTagAndValue(tag,Float.valueOf(0.1F)), new SAMBinaryTagAndValue(tag, Float.valueOf(0.1F)), true, true},
                {new SAMBinaryTagAndValue(tag, Float.valueOf(0.1F)), new SAMBinaryTagAndValue(tag, Float.valueOf(0.2F)), false, false},

                {new SAMBinaryTagAndValue(tag,Long.valueOf(37L)), new SAMBinaryTagAndValue(tag, Long.valueOf(37L)), true, true},
                {new SAMBinaryTagAndValue(tag, Long.valueOf(37L)), new SAMBinaryTagAndValue(tag, Long.valueOf(38L)), false, false},

                {new SAMBinaryTagAndValue(tag,Long.valueOf(BinaryCodec.MAX_UINT)), new SAMBinaryTagAndValue(tag, Long.valueOf(BinaryCodec.MAX_UINT)), true, true},
                {new SAMBinaryTagAndValue(tag, Long.valueOf(BinaryCodec.MAX_UINT)), new SAMBinaryTagAndValue(tag, Long.valueOf(BinaryCodec.MAX_UINT-1)), false, false},

                // arrays

                {new SAMBinaryTagAndUnsignedArrayValue(tag, new byte[]{0, 1, 2}), new SAMBinaryTagAndUnsignedArrayValue(tag, new byte[]{0, 1, 2}), true, true},
                {new SAMBinaryTagAndUnsignedArrayValue(tag, new byte[]{0, 1, 2}), new SAMBinaryTagAndUnsignedArrayValue(tag, new byte[]{3, 4, 5}), false, false},

                {new SAMBinaryTagAndUnsignedArrayValue(tag, new short[]{0, 1, 2}), new SAMBinaryTagAndUnsignedArrayValue(tag, new short[]{0, 1, 2}), true, true},
                {new SAMBinaryTagAndUnsignedArrayValue(tag, new short[]{0, 1, 2}), new SAMBinaryTagAndUnsignedArrayValue(tag, new short[]{3, 4, 5}), false, false},

                {new SAMBinaryTagAndUnsignedArrayValue(tag, new int[]{0, 1, 2}), new SAMBinaryTagAndUnsignedArrayValue(tag, new int[]{0, 1, 2}), true, true},
                {new SAMBinaryTagAndUnsignedArrayValue(tag, new int[]{0, 1, 2}), new SAMBinaryTagAndUnsignedArrayValue(tag, new int[]{3, 4, 5}), false, false},

                // mix signed array and unsigned array; hashCodes are equal but objects are not
                {new SAMBinaryTagAndValue(tag, new short[]{0, 1, 2}), new SAMBinaryTagAndUnsignedArrayValue(tag, new short[]{0, 1, 2}), true, false},

                // mix signed array and unsigned array; hashCodes and objects are not equal
                {new SAMBinaryTagAndValue(tag, new short[]{0, 1, 2}), new SAMBinaryTagAndUnsignedArrayValue(tag, new short[]{1, 1, 3}), false, false},
        };
    }

    @Test(dataProvider="hashCopyEquals")
    public void testHashAndEquals(
            final SAMBinaryTagAndValue v1,
            final SAMBinaryTagAndValue v2,
            final boolean hashEquals,
            final boolean isEquals)
    {
        Assert.assertEquals(hashEquals, v1.hashCode() == v2.hashCode());

        Assert.assertEquals(isEquals, v1.equals(v2));
        Assert.assertEquals(isEquals, v2.equals(v1));
    }

    @Test(dataProvider="hashCopyEquals")
    public void testCopy(
            final SAMBinaryTagAndValue v1,
            final SAMBinaryTagAndValue v2,
            final boolean unused_hashEquals,
            final boolean isEquals)
    {
        Assert.assertTrue(v1.equals(v1.copy()));
        Assert.assertTrue(v2.equals(v2.copy()));

        Assert.assertEquals(isEquals, v1.equals(v2.copy()));
        Assert.assertEquals(isEquals, v2.equals(v1.copy()));
    }

    @Test(dataProvider="hashCopyEquals")
    public void testDeepCopy(
            final SAMBinaryTagAndValue v1,
            final SAMBinaryTagAndValue v2,
            final boolean unused_hashEquals,
            final boolean isEquals)
    {
        Assert.assertTrue(v1.equals(v1.deepCopy()));
        Assert.assertTrue(v2.equals(v2.deepCopy()));

        Assert.assertEquals(isEquals, v1.equals(v2.deepCopy()));
        Assert.assertEquals(isEquals, v2.equals(v1.deepCopy()));
    }

}
