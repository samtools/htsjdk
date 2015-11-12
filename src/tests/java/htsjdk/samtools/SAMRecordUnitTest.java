/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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

package htsjdk.samtools;

import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.TestUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.util.Arrays;

public class SAMRecordUnitTest {

    @DataProvider(name = "serializationTestData")
    public Object[][] getSerializationTestData() {
        return new Object[][] {
                { new File("testdata/htsjdk/samtools/serialization_test.sam") },
                { new File("testdata/htsjdk/samtools/serialization_test.bam") }
        };
    }

    @Test(dataProvider = "serializationTestData")
    public void testSAMRecordSerialization( final File inputFile ) throws Exception {
        final SamReader reader = SamReaderFactory.makeDefault().open(inputFile);
        final SAMRecord initialSAMRecord = reader.iterator().next();
        reader.close();

        final SAMRecord deserializedSAMRecord = TestUtil.serializeAndDeserialize(initialSAMRecord);

        Assert.assertEquals(deserializedSAMRecord, initialSAMRecord, "Deserialized SAMRecord not equal to original SAMRecord");
    }

    @DataProvider
    public Object [][] offsetAtReferenceData() {
        return new Object[][]{
                {"3S9M",   7, 10, false},
                {"3S9M",   0,  0, false},
                {"3S9M",  -1,  0, false},
                {"3S9M",  13,  0, false},
                {"4M1D6M", 4,  4, false},
                {"4M1D6M", 4,  4, true},
                {"4M1D6M", 5,  0, false},
                {"4M1D6M", 5,  4, true},
                {"4M1I6M", 5,  6, false},
                {"4M1I6M", 11, 0, false},
        };
    }

    @Test(dataProvider = "offsetAtReferenceData")
    public void testOffsetAtReference(String cigar, int posInReference, int expectedPosInRead, boolean returnLastBaseIfDeleted) {

            SAMRecord sam = new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, cigar, null, 2);
            Assert.assertEquals(SAMRecord.getReadPositionAtReferencePosition(sam, posInReference, returnLastBaseIfDeleted), expectedPosInRead);
    }

    @DataProvider
    public Object [][] referenceAtReadData() {
        return new Object[][]{
                {"3S9M", 7, 10},
                {"3S9M", 0, 0},
                {"3S9M", 0, 13},
                {"4M1D6M", 4, 4},
                {"4M1D6M", 6, 5},
                {"4M1I6M", 0, 5},
                {"4M1I6M", 5, 6},
        };
    }

    @Test(dataProvider = "referenceAtReadData")
    public void testOffsetAtRead(String cigar, int expectedReferencePos, int posInRead) {

            SAMRecord sam = new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, cigar, null, 2);
            Assert.assertEquals(sam.getReferencePositionAtReadPosition(posInRead), expectedReferencePos);
    }

    @DataProvider(name = "deepCopyTestData")
    public Object [][] deepCopyTestData() {
        return new Object[][]{
                { new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "3S9M", null, 2) },
                { new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "4M1I6M", null, 2) }
        };
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepCopyRef(final SAMRecord sam) {
        testDeepCopy(sam);
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepCopyMutate(final SAMRecord sam) {
        final byte[] initialBaseQualityCopy = Arrays.copyOf(sam.getBaseQualities(), sam.getBaseQualities().length);
        final int initialStart = sam.getAlignmentStart();

        final SAMRecord deepCopy = testDeepCopy(sam);
        Assert.assertTrue(Arrays.equals(sam.getBaseQualities(), deepCopy.getBaseQualities()));
        Assert.assertTrue(sam.getAlignmentStart() == deepCopy.getAlignmentStart());

        // mutate copy and make sure original remains unchanged
        final byte[] copyBaseQuals = deepCopy.getBaseQualities();
        for (int i = 0; i < copyBaseQuals.length; i++) {
            copyBaseQuals[i]++;
        }
        deepCopy.setBaseQualities(copyBaseQuals);
        deepCopy.setAlignmentStart(initialStart + 1);
        Assert.assertTrue(Arrays.equals(sam.getBaseQualities(), initialBaseQualityCopy));
        Assert.assertTrue(sam.getAlignmentStart() == initialStart);
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepByteAttributes( final SAMRecord sam ) throws Exception {
        // Note that "samRecord.deepCopy().equals(samRecord)" fails with attributes due to
        // SAMBinaryTagAndValue.equals using reference equality on attribute values.
        SAMRecord deepCopy = testDeepCopy(sam);
        Assert.assertTrue(sam.equals(deepCopy));

        final byte bytes[] = { -2, -1, 0, 1, 2 };
        sam.setAttribute("BY", bytes);
        deepCopy = sam.deepCopy();

        // validate reference inequality and content equality
        final byte samBytes[] = sam.getByteArrayAttribute("BY");
        final byte copyBytes[] = deepCopy.getByteArrayAttribute("BY");
        Assert.assertFalse(copyBytes == samBytes);
        Assert.assertTrue(Arrays.equals(copyBytes, samBytes));

        // validate mutation independence
        final byte testByte = -1;
        Assert.assertTrue(samBytes[2] != testByte);  // ensure initial test condition
        Assert.assertTrue(copyBytes[2] != testByte); // ensure initial test condition
        samBytes[2] = testByte;                      // mutate original
        Assert.assertTrue(samBytes[2] == testByte);
        Assert.assertTrue(copyBytes[2] != testByte);
        sam.setAttribute("BY", samBytes);
        Assert.assertTrue(sam.getByteArrayAttribute("BY")[2] != deepCopy.getByteArrayAttribute("BY")[2]);

        // now unsigned...
        sam.setUnsignedArrayAttribute("BY", bytes);
        deepCopy = sam.deepCopy();
        final byte samUBytes[] = sam.getUnsignedByteArrayAttribute("BY");
        final byte copyUBytes[] = deepCopy.getUnsignedByteArrayAttribute("BY");
        Assert.assertFalse(copyUBytes == bytes);
        Assert.assertTrue(Arrays.equals(copyUBytes, samUBytes));

        // validate mutation independence
        final byte uByte = 1;
        Assert.assertTrue(samUBytes[2] != uByte); //  ensure initial test condition
        Assert.assertTrue(samUBytes[2] != uByte); //  ensure initial test condition
        samUBytes[2] = uByte;  // mutate original
        Assert.assertTrue(samUBytes[2] == uByte);
        Assert.assertTrue(copyUBytes[2] != uByte);
        sam.setUnsignedArrayAttribute("BY", samBytes);
        Assert.assertTrue(sam.getUnsignedByteArrayAttribute("BY")[2] != deepCopy.getUnsignedByteArrayAttribute("BY")[2]);
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepShortAttributes( final SAMRecord sam ) throws Exception {
        // Note that "samRecord.deepCopy().equals(samRecord)" fails with attributes due to
        // SAMBinaryTagAndValue.equals using reference equality on attribute values.
        SAMRecord deepCopy = testDeepCopy(sam);
        Assert.assertTrue(sam.equals(deepCopy));

        final short shorts[] = { -20, -10, 0, 10, 20 };
        sam.setAttribute("SH", shorts);
        deepCopy = sam.deepCopy();

        // validate reference inequality, content equality
        final short samShorts[] = sam.getSignedShortArrayAttribute("SH");
        final short copyShorts[] = deepCopy.getSignedShortArrayAttribute("SH");
        Assert.assertFalse(copyShorts == samShorts);
        Assert.assertTrue(Arrays.equals(copyShorts, samShorts));

        // validate mutation independence
        final short testShort = -1;
        Assert.assertTrue(samShorts[2] != testShort); //  ensure initial test condition
        Assert.assertTrue(samShorts[2] != testShort); //  ensure initial test condition
        samShorts[2] = testShort;  // mutate original
        Assert.assertTrue(samShorts[2] == testShort);
        Assert.assertTrue(copyShorts[2] != testShort);
        sam.setAttribute("SH", samShorts);
        Assert.assertTrue(sam.getSignedShortArrayAttribute("SH")[2] != deepCopy.getSignedShortArrayAttribute("SH")[2]);

        // now unsigned...
        sam.setUnsignedArrayAttribute("SH", shorts);
        deepCopy = sam.deepCopy();

        final short samUShorts[] = sam.getUnsignedShortArrayAttribute("SH");
        final short copyUShorts[] = deepCopy.getUnsignedShortArrayAttribute("SH");
        Assert.assertFalse(copyUShorts == shorts);
        Assert.assertTrue(Arrays.equals(copyUShorts, samUShorts));

        // validate mutation independence
        final byte uShort = 1;
        Assert.assertTrue(samUShorts[2] != uShort); //  ensure initial test condition
        Assert.assertTrue(samUShorts[2] != uShort); //  ensure initial test condition
        samUShorts[2] = uShort;  // mutate original
        Assert.assertTrue(samUShorts[2] == uShort);
        Assert.assertTrue(copyUShorts[2] != uShort);
        sam.setUnsignedArrayAttribute("SH", samShorts);
        Assert.assertTrue(sam.getUnsignedShortArrayAttribute("SH")[2] != deepCopy.getUnsignedShortArrayAttribute("SH")[2]);
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepIntAttributes( final SAMRecord sam ) throws Exception {
        // Note that "samRecord.deepCopy().equals(samRecord)" fails with attributes due to
        // SAMBinaryTagAndValue.equals using reference equality on attribute values.
        SAMRecord deepCopy = testDeepCopy(sam);
        Assert.assertTrue(sam.equals(deepCopy));

        final int ints[] = { -200, -100, 0, 100, 200 };
        sam.setAttribute("IN", ints);
        deepCopy = sam.deepCopy();

        // validate reference inequality and content equality
        final  int samInts[] = sam.getSignedIntArrayAttribute("IN");
        final  int copyInts[] = deepCopy.getSignedIntArrayAttribute("IN");
        Assert.assertFalse(copyInts == ints);
        Assert.assertTrue(Arrays.equals(copyInts, samInts));

        // validate mutation independence
        final short testInt = -1;
        Assert.assertTrue(samInts[2] != testInt); //  ensure initial test condition
        Assert.assertTrue(samInts[2] != testInt); //  ensure initial test condition
        samInts[2] = testInt;  // mutate original
        Assert.assertTrue(samInts[2] == testInt);
        Assert.assertTrue(copyInts[2] != testInt);
        sam.setAttribute("IN", samInts);
        Assert.assertTrue(sam.getSignedIntArrayAttribute("IN")[2] != deepCopy.getSignedIntArrayAttribute("IN")[2]);

        // now unsigned...
        sam.setUnsignedArrayAttribute("IN", ints);
        deepCopy = sam.deepCopy();

        final int samUInts[] = sam.getUnsignedIntArrayAttribute("IN");
        final int copyUInts[] = deepCopy.getUnsignedIntArrayAttribute("IN");
        Assert.assertFalse(copyUInts == ints);
        Assert.assertTrue(Arrays.equals(copyUInts, samUInts));

        // validate mutation independence
        byte uInt = 1;
        Assert.assertTrue(samUInts[2] != uInt); //  ensure initial test condition
        Assert.assertTrue(samUInts[2] != uInt); //  ensure initial test condition
        samInts[2] = uInt;  // mutate original
        Assert.assertTrue(samUInts[2] == uInt);
        Assert.assertTrue(copyUInts[2] != uInt);
        sam.setUnsignedArrayAttribute("IN", samInts);
        Assert.assertTrue(sam.getUnsignedIntArrayAttribute("IN")[2] != deepCopy.getUnsignedIntArrayAttribute("IN")[2]);
    }

    @Test(dataProvider = "deepCopyTestData")
    public void testDeepFloatAttributes( final SAMRecord sam ) throws Exception {
        // Note that "samRecord.deepCopy().equals(samRecord)" fails with attributes due to
        // SAMBinaryTagAndValue.equals using reference equality on attribute values.
        SAMRecord deepCopy = testDeepCopy(sam);
        Assert.assertTrue(sam.equals(deepCopy));

        final float floats[] = { -2.4f, -1.2f, 0, 2.3f, 4.6f };
        sam.setAttribute("FL", floats);
        deepCopy = sam.deepCopy();

        // validate reference inequality and content equality
        final float samFloats[] = sam.getFloatArrayAttribute("FL");
        final float copyFloats[] = deepCopy.getFloatArrayAttribute("FL");
        Assert.assertFalse(copyFloats == floats);
        Assert.assertFalse(copyFloats == samFloats);
        Assert.assertTrue(Arrays.equals(copyFloats, samFloats));

        // validate mutation independence
        final float testFloat = -1.0f;
        Assert.assertTrue(samFloats[2] != testFloat); //  ensure initial test condition
        Assert.assertTrue(samFloats[2] != testFloat); //  ensure initial test condition
        samFloats[2] = testFloat;  // mutate original
        Assert.assertTrue(samFloats[2] == testFloat);
        Assert.assertTrue(copyFloats[2] != testFloat);
        sam.setAttribute("FL", samFloats);
        Assert.assertTrue(sam.getFloatArrayAttribute("FL")[2] != deepCopy.getFloatArrayAttribute("FL")[2]);
    }

    private SAMRecord testDeepCopy(SAMRecord sam) {
        final SAMRecord deepCopy = sam.deepCopy();

        // force the indexing bins to be computed in order to satisfy equality test
        sam.setIndexingBin(sam.computeIndexingBin());
        deepCopy.setIndexingBin(deepCopy.computeIndexingBin());
        Assert.assertTrue(sam.equals(deepCopy));

        return deepCopy;
    }

    @Test
    public void test_getUnsignedIntegerAttribute_valid() {
        final String stringTag = "UI";
        final short binaryTag = SAMTagUtil.getSingleton().makeBinaryTag(stringTag);
        SAMFileHeader header = new SAMFileHeader();
        SAMRecord record = new SAMRecord(header);
        Assert.assertNull(record.getUnsignedIntegerAttribute(stringTag));
        Assert.assertNull(record.getUnsignedIntegerAttribute(binaryTag));

        record.setAttribute("UI", 0L);
        Assert.assertEquals(new Long(0L), record.getUnsignedIntegerAttribute(stringTag));
        Assert.assertEquals(new Long(0L), record.getUnsignedIntegerAttribute(binaryTag));

        record.setAttribute("UI", BinaryCodec.MAX_UINT);
        Assert.assertEquals(new Long(BinaryCodec.MAX_UINT), record.getUnsignedIntegerAttribute(stringTag));
        Assert.assertEquals(new Long(BinaryCodec.MAX_UINT), record.getUnsignedIntegerAttribute(binaryTag));

        final SAMBinaryTagAndValue tv_zero = new SAMBinaryTagAndUnsignedArrayValue(binaryTag, 0L);
        record = new SAMRecord(header){
            {
                setAttributes(tv_zero);
            }
        };
        Assert.assertEquals(new Long(0L), record.getUnsignedIntegerAttribute(stringTag));
        Assert.assertEquals(new Long(0L), record.getUnsignedIntegerAttribute(binaryTag));

        final SAMBinaryTagAndValue tv_max = new SAMBinaryTagAndUnsignedArrayValue(binaryTag, BinaryCodec.MAX_UINT);
        record = new SAMRecord(header){
            {
                setAttributes(tv_max);
            }
        };
        Assert.assertEquals(new Long(BinaryCodec.MAX_UINT), record.getUnsignedIntegerAttribute(stringTag));
        Assert.assertEquals(new Long(BinaryCodec.MAX_UINT), record.getUnsignedIntegerAttribute(binaryTag));
    }

    /**
     * This is an alternative to test_getUnsignedIntegerAttribute_valid().
     * The purpose is to ensure that the hacky way of setting arbitrary tag values works ok.
     * This is required for testing invalid (out of range) unsigned integer value.
     */
    @Test
    public void test_getUnsignedIntegerAttribute_valid_alternative() {
        final short tag = SAMTagUtil.getSingleton().makeBinaryTag("UI");
        SAMFileHeader header = new SAMFileHeader();
        SAMRecord record;

        record = new SAMRecord(header) {
            {
                setAttributes(new SAMBinaryTagAndUnsignedArrayValue(tag, 0L));
            }
        };
        Assert.assertEquals(new Long(0L), record.getUnsignedIntegerAttribute(tag));

        record = new SAMRecord(header) {
            {
                setAttributes(new SAMBinaryTagAndUnsignedArrayValue(tag, BinaryCodec.MAX_UINT));
            }
        };
        Assert.assertEquals(new Long(BinaryCodec.MAX_UINT), record.getUnsignedIntegerAttribute(tag));

        // the following works because we bypass value checks implemented in SAMRecord:
        record = new SAMRecord(header) {
            {
                setAttributes(new SAMBinaryTagAndUnsignedArrayValue(tag, BinaryCodec.MAX_UINT+1L));
            }
        };
        // check that the invalid value is still there:
        Assert.assertEquals(new Long(BinaryCodec.MAX_UINT+1L), (Long)record.getBinaryAttributes().value);
    }

    @Test(expectedExceptions = SAMException.class)
    public void test_getUnsignedIntegerAttribute_negative() {
        short tag = 0;
        SAMRecord record = null;
        try {
            tag = SAMTagUtil.getSingleton().makeBinaryTag("UI");
            SAMFileHeader header = new SAMFileHeader();
            final SAMBinaryTagAndValue tv = new SAMBinaryTagAndUnsignedArrayValue(tag, -1L);
            record = new SAMRecord(header) {
                {
                    setAttributes(tv);
                }
            };
        } catch (Exception e) {
            Assert.fail("Unexpected exception", e);
        }
        record.getUnsignedIntegerAttribute(tag);
    }

    @Test(expectedExceptions = SAMException.class)
    public void test_getUnsignedIntegerAttribute_tooLarge() {
        short tag = 0;
        SAMRecord record = null;
        try {
            tag = SAMTagUtil.getSingleton().makeBinaryTag("UI");
            SAMFileHeader header = new SAMFileHeader();
            final SAMBinaryTagAndValue tv = new SAMBinaryTagAndUnsignedArrayValue(tag, BinaryCodec.MAX_UINT + 1);
            record = new SAMRecord(header) {
                {
                    setAttributes(tv);
                }
            };
        } catch (Exception e) {
            Assert.fail("Unexpected exception", e);
        }

        record.getUnsignedIntegerAttribute(tag);
    }

    @Test
    public void test_isAllowedAttributeDataType() {
        Assert.assertTrue(SAMRecord.isAllowedAttributeValue(new Byte((byte) 0)));
        Assert.assertTrue(SAMRecord.isAllowedAttributeValue(new Short((short) 0)));
        Assert.assertTrue(SAMRecord.isAllowedAttributeValue(new Integer(0)));
        Assert.assertTrue(SAMRecord.isAllowedAttributeValue("a string"));
        Assert.assertTrue(SAMRecord.isAllowedAttributeValue(new Character('C')));
        Assert.assertTrue(SAMRecord.isAllowedAttributeValue(new Float(0.1F)));
        Assert.assertTrue(SAMRecord.isAllowedAttributeValue(new byte[]{0}));
        Assert.assertTrue(SAMRecord.isAllowedAttributeValue(new short[]{0}));
        Assert.assertTrue(SAMRecord.isAllowedAttributeValue(new int[]{0}));
        Assert.assertTrue(SAMRecord.isAllowedAttributeValue(new float[]{0.1F}));

        // unsigned integers:
        Assert.assertTrue(SAMRecord.isAllowedAttributeValue(new Long(0)));
        Assert.assertTrue(SAMRecord.isAllowedAttributeValue(new Long(BinaryCodec.MAX_UINT)));
        Assert.assertTrue(SAMRecord.isAllowedAttributeValue(new Long(-1L)));
        Assert.assertFalse(SAMRecord.isAllowedAttributeValue(new Long(BinaryCodec.MAX_UINT + 1L)));
        Assert.assertFalse(SAMRecord.isAllowedAttributeValue(new Long(Integer.MIN_VALUE - 1L)));

    }

    @Test(expectedExceptions = SAMException.class)
    public void test_setAttribute_unsigned_int_negative() {
        short tag = 0;
        SAMRecord record = null;
        try {
            tag = SAMTagUtil.getSingleton().makeBinaryTag("UI");
            SAMFileHeader header = new SAMFileHeader();
            record = new SAMRecord(header);
            Assert.assertNull(record.getUnsignedIntegerAttribute(tag));
        } catch (SAMException e) {
            Assert.fail("Unexpected exception", e);
        }

        record.setAttribute(tag, (long)Integer.MIN_VALUE-1L);
    }

    @Test(expectedExceptions = SAMException.class)
    public void test_setAttribute_unsigned_int_tooLarge() {
        short tag = 0;
        SAMRecord record = null;
        try {
            tag = SAMTagUtil.getSingleton().makeBinaryTag("UI");
            SAMFileHeader header = new SAMFileHeader();
            record = new SAMRecord(header);
            Assert.assertNull(record.getUnsignedIntegerAttribute(tag));
        } catch (SAMException e) {
            Assert.fail("Unexpected exception", e);
        }

        record.setAttribute(tag, BinaryCodec.MAX_UINT + 1L);
    }

    @Test
    public void test_setAttribute_null_removes_tag() {
        final short tag = SAMTagUtil.getSingleton().makeBinaryTag("UI");
        SAMFileHeader header = new SAMFileHeader();
        SAMRecord record = new SAMRecord(header);
        Assert.assertNull(record.getUnsignedIntegerAttribute(tag));

        record.setAttribute(tag, BinaryCodec.MAX_UINT);
        Assert.assertEquals(new Long(BinaryCodec.MAX_UINT), record.getUnsignedIntegerAttribute(tag));

        record.setAttribute(tag, null);
        Assert.assertNull(record.getUnsignedIntegerAttribute(tag));
    }
}
