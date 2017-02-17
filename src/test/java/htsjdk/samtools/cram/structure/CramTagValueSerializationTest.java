/*
 * The MIT License
 *
 * Copyright (c) 2015 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.BinaryTagCodec;
import htsjdk.samtools.SAMBinaryTagAndValue;
import htsjdk.samtools.SAMTagUtil;
import htsjdk.samtools.ValidationStringency;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class CramTagValueSerializationTest {
    @DataProvider(name = "createTagRoundtripInput")
    public Object[][] createTagRoundtripInput() {
        Object[] values = new Object[]{
                // a byte array value:
                "123".getBytes(),
                // an array of short numbers:
                new short[]{(short) 0, (short) 1, (short) -1, Short.MIN_VALUE, Short.MAX_VALUE},
                // an array of int numbers:
                new int[]{0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE},
                // single byte values:
                (byte) 0, (byte) 1, (byte) -1, Byte.MIN_VALUE, Byte.MAX_VALUE,
                // single short values:
                (short) 0, (short) 1, (short) -1, Short.MIN_VALUE, Short.MAX_VALUE,
                // single int values:
                0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE,
                // single long values:
                0L, 1L, -1L, (long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE,
                // a char value:
                'A',
                // a string:
                "STRING"};

        Object[][] result = new Object[values.length][];
        for (int i = 0; i < result.length; i++) {
            result[i] = new Object[]{values[i]};
        }
        return result;
    }

    /**
     * The purpose is to ensure all possible value types survice a roundtrip serialization.
     * The only exception is made for numbers because they may mutate into an int, so numeric values are
     * compared using {@link Number#longValue()} value.
     *
     * @param value
     */
    @Test(dataProvider = "createTagRoundtripInput")
    public void testTagValueRoundtrip(Object value) {
        SAMBinaryTagAndValue tv = new SAMBinaryTagAndValue(SAMTagUtil.getSingleton().makeBinaryTag("AS"), value);
        Assert.assertEquals(tv.value, value);

        byte valueType = CramTagValueSerialization.getTagValueType(tv.value);
        byte[] bytes = CramTagValueSerialization.writeSingleValue(valueType, tv.value, false);
        Assert.assertNotNull(bytes);
        Assert.assertTrue(bytes.length > 0);

        SAMBinaryTagAndValue newTV = CramTagValueSerialization.readTagValue(tv.tag, valueType, bytes, ValidationStringency.STRICT);
        Assert.assertNotNull(newTV);
        if (value instanceof Number) {
            Assert.assertEquals(((Number) newTV.value).longValue(), (Number) ((Number) value).longValue(), "failed for value " + value + ", class: " + value.getClass());
        } else
            Assert.assertEquals(newTV.value, value, "failed for value " + value + ", class: " + value.getClass());
    }


    @DataProvider(name = "createTagMutationExpectations")
    public Object[][] createTagMutationExpectations() {
        List<Object[]> cases = new ArrayList<>();
        // byte values:
        cases.add(new Object[]{(byte) 0, 0});
        cases.add(new Object[]{(byte) 1, 1});
        cases.add(new Object[]{(byte) -1, -1});
        cases.add(new Object[]{Byte.MIN_VALUE, (int) Byte.MIN_VALUE});
        cases.add(new Object[]{Byte.MAX_VALUE, (int) Byte.MAX_VALUE});

        // short values:
        cases.add(new Object[]{(short) 0, 0});
        cases.add(new Object[]{(short) 1, 1});
        cases.add(new Object[]{(short) -1, -1});
        cases.add(new Object[]{Short.MAX_VALUE, (int) Short.MAX_VALUE});
        cases.add(new Object[]{Short.MIN_VALUE, (int) Short.MIN_VALUE});

        // long values:
        cases.add(new Object[]{0L, 0});
        cases.add(new Object[]{1L, 1});
        cases.add(new Object[]{-1L, -1});

        return cases.toArray(new Object[cases.size()][]);
    }

    /**
     * Numeric values are represented as int internally, test these expectations explicitly.
     *
     * @param value
     * @param expectedValue
     */
    @Test(dataProvider = "createTagMutationExpectations")
    public void testTagValueTransformationExpectations(Object value, Object expectedValue) {
        SAMBinaryTagAndValue tv = new SAMBinaryTagAndValue(SAMTagUtil.getSingleton().makeBinaryTag("AS"), value);
        Assert.assertEquals(tv.value, value);

        byte valueType = CramTagValueSerialization.getTagValueType(tv.value);
        byte[] bytes = CramTagValueSerialization.writeSingleValue(valueType, tv.value, false);

        SAMBinaryTagAndValue newTV = CramTagValueSerialization.readTagValue(tv.tag, valueType, bytes, ValidationStringency.STRICT);
        Assert.assertEquals(newTV.value, expectedValue, "failed for value " + value + ", class: " + value.getClass());
    }

    @Test
    public void testGetTagValueType_NonNumeric() {
        Assert.assertEquals(CramTagValueSerialization.getTagValueType('A'), 'A');
        Assert.assertEquals(CramTagValueSerialization.getTagValueType("ABCD"), 'Z');
        Assert.assertEquals(CramTagValueSerialization.getTagValueType("ABCD".getBytes()), 'B');
        Assert.assertEquals(CramTagValueSerialization.getTagValueType(new short[]{1, 2, 3}), 'B');
        Assert.assertEquals(CramTagValueSerialization.getTagValueType(new int[]{1, 2, 3}), 'B');
    }

    @Test
    public void testGetIntegerType() {
        Assert.assertEquals(CramTagValueSerialization.getIntegerType((byte) 1), 'c');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType((short) 1), 'c');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType(1), 'c');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType(1L), 'c');

        Assert.assertEquals(CramTagValueSerialization.getIntegerType(Byte.MIN_VALUE), 'c');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType(Byte.MAX_VALUE), 'c');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType((short) Byte.MAX_VALUE + 1), 'C');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType(Byte.MAX_VALUE + 1), 'C');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType((long) (Byte.MAX_VALUE + 1)), 'C');

        Assert.assertEquals(CramTagValueSerialization.getIntegerType(Short.MAX_VALUE), 's');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType(Short.MAX_VALUE + 1), 'S');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType(Short.MIN_VALUE), 's');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType(Short.MIN_VALUE - 1), 'i');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType(Short.MAX_VALUE * 2 + 1), 'S');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType(Short.MAX_VALUE * 2 + 2), 'i');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType(Integer.MIN_VALUE), 'i');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType(Integer.MIN_VALUE - 1), 'i');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType(Integer.MAX_VALUE), 'i');
        Assert.assertEquals(CramTagValueSerialization.getIntegerType(Integer.MAX_VALUE + 1L), 'I');
    }

    @DataProvider(name = "arrayTestData")
    public Object[][] createArrayTestData() {
        Object[][] data = new Object[7][];
        int i = 0;
        data[i++] = new Object[]{new float[]{0f, .1f, .2f}, false};
        data[i++] = new Object[]{new int[]{1, 2, 3}, false};
        data[i++] = new Object[]{new int[]{1, 2, 3}, true};
        data[i++] = new Object[]{"ABCDE".getBytes(), false};
        data[i++] = new Object[]{"ABCDE".getBytes(), true};
        data[i++] = new Object[]{new short[]{0, 1, 2}, false};
        data[i++] = new Object[]{new short[]{0, 1, 2}, true};
        return data;
    }

    @Test(dataProvider = "arrayTestData")
    public void assertWriteArray(Object array, boolean isUnsigned) {
        ByteBuffer buf = ByteBuffer.allocate(100);
        CramTagValueSerialization.writeArray(array, isUnsigned, buf);
        buf.flip();
        SAMBinaryTagAndValue tv = BinaryTagCodec.readSingleTagValue((short) 0, (byte) 'B', buf, ValidationStringency.STRICT);
        Assert.assertEquals(tv.value, array);
    }
}
