package htsjdk.samtools;

import htsjdk.samtools.util.BinaryCodec;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SAMBinaryTagAndValueUnitTest {

    @DataProvider(name="allowedAttributeTypes")
    public Object[][] allowedTypes() {
        return  new Object[][] {
                {new String("a string")},
                {new Byte((byte) 7)},
                {new Short((short) 8)},
                {new Integer(0)},
                {new Character('C')},
                {new Float(0.1F)},
                // unsigned longs
                {new Long(0)},
                {new Long(BinaryCodec.MAX_UINT)},
                // signed longs
                {new Long(-1L)},
                {new Long(Integer.MAX_VALUE)},
                {new Long(Integer.MIN_VALUE)},
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
        Assert.assertNotNull(new SAMBinaryTagAndValue(SAMTagUtil.getSingleton().makeBinaryTag("UI"), value));
    }

    @DataProvider(name="notAllowedAttributeTypes")
    public Object[][] notAllowedTypes() {
        return  new Object[][] {
                {new Long(BinaryCodec.MAX_UINT + 1L)},
                {new Long(Integer.MIN_VALUE - 1L)},
                {new Double(0.3F)},
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
        new SAMBinaryTagAndValue(SAMTagUtil.getSingleton().makeBinaryTag("ZZ"), value);
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
        final short binaryTag = SAMTagUtil.getSingleton().makeBinaryTag("UI");
        Assert.assertNotNull(new SAMBinaryTagAndUnsignedArrayValue(binaryTag, value));
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
        final short binaryTag = SAMTagUtil.getSingleton().makeBinaryTag("UI");
        new SAMBinaryTagAndUnsignedArrayValue(binaryTag, value);
    }

    @DataProvider(name="hashCopyEquals")
    public Object[][] hashCopyEquals() {
        final short tag = SAMTagUtil.getSingleton().makeBinaryTag("UI");
        return new Object[][] {
                {new SAMBinaryTagAndValue(tag, new String("a string")), new SAMBinaryTagAndValue(tag, new String("a string")), true, true},
                {new SAMBinaryTagAndValue(tag, new String("a string")), new SAMBinaryTagAndValue(tag, new String("different string")), false, false},

                {new SAMBinaryTagAndValue(tag, new Byte((byte) 0)), new SAMBinaryTagAndValue(tag, new Byte((byte) 0)), true, true},
                {new SAMBinaryTagAndValue(tag, new Byte((byte) 0)), new SAMBinaryTagAndValue(tag, new Byte((byte) 1)), false, false},

                {new SAMBinaryTagAndValue(tag, new Short((short) 0)), new SAMBinaryTagAndValue(tag, new Short((short) 0)), true, true},
                {new SAMBinaryTagAndValue(tag, new Short((short) 0)), new SAMBinaryTagAndValue(tag, new Short((short) 1)), false, false},

                {new SAMBinaryTagAndValue(tag, new Integer(0)), new SAMBinaryTagAndValue(tag, new Integer(0)), true, true},
                {new SAMBinaryTagAndValue(tag, new Integer(0)), new SAMBinaryTagAndValue(tag, new Integer(0)), true, true},

                {new SAMBinaryTagAndValue(tag, new Character('C')), new SAMBinaryTagAndValue(tag, new Character('C')), true, true},
                {new SAMBinaryTagAndValue(tag, new Character('C')), new SAMBinaryTagAndValue(tag, new Character('D')), false, false},

                {new SAMBinaryTagAndValue(tag,new Float(0.1F)), new SAMBinaryTagAndValue(tag, new Float(0.1F)), true, true},
                {new SAMBinaryTagAndValue(tag, new Float(0.1F)), new SAMBinaryTagAndValue(tag, new Float(0.2F)), false, false},

                {new SAMBinaryTagAndValue(tag,new Long(37L)), new SAMBinaryTagAndValue(tag, new Long(37L)), true, true},
                {new SAMBinaryTagAndValue(tag, new Long(37L)), new SAMBinaryTagAndValue(tag, new Long(38L)), false, false},

                {new SAMBinaryTagAndValue(tag,new Long(BinaryCodec.MAX_UINT)), new SAMBinaryTagAndValue(tag, new Long(BinaryCodec.MAX_UINT)), true, true},
                {new SAMBinaryTagAndValue(tag, new Long(BinaryCodec.MAX_UINT)), new SAMBinaryTagAndValue(tag, new Long(BinaryCodec.MAX_UINT-1)), false, false},

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
