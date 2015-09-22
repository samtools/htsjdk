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

import htsjdk.samtools.util.TestUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.util.List;

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

    @Test
    public void testNullHeaderRefName() {
        SAMRecord sam = new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "3S9M", null, 2);
        SAMFileHeader samHeader = sam.getHeader();
        Assert.assertTrue(null != samHeader);

        String origRefName = sam.getReferenceName();

        // setting header to null retains ref name
        sam.setHeader(null);
        Assert.assertTrue(origRefName.equals(sam.getReferenceName()));

        // null header allows ref name set
        sam.setReferenceName("fakeref");
        Assert.assertTrue("fakeref".equals(sam.getReferenceName()));
    }

    @Test
    public void testNullHeaderMateRefName() {
        SAMRecord sam = new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "3S9M", null, 2);
        SAMFileHeader samHeader = sam.getHeader();
        Assert.assertTrue(null != samHeader);

        String origMateRefName = sam.getMateReferenceName();

        // setting header to null retains ref name
        sam.setHeader(null);
        Assert.assertTrue(origMateRefName.equals(sam.getMateReferenceName()));

        // null header allows ref name set
        sam.setMateReferenceName("fakemateref");
        Assert.assertTrue("fakemateref".equals(sam.getMateReferenceName()));
    }

    @Test
    public void testSetNullHeaderGetRefIndex() {
        SAMRecord sam = new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "3S9M", null, 2);
        SAMFileHeader samHeader = sam.getHeader();
        Assert.assertTrue(null != samHeader);

        // setting header to null retains ref index
        sam.setReferenceIndex(3);
        Assert.assertTrue(sam.getReferenceIndex().equals(3));
        sam.setHeader(null);
        Assert.assertTrue(sam.getReferenceIndex().equals(3));
    }

    @Test(expectedExceptions = SAMException.class)
    public void testNullHeaderSetRefIndex() {
        SAMRecord sam = new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "3S9M", null, 2);
        sam.setHeader(null);
        sam.setReferenceIndex(3);
    }

    @Test
    public void testNullHeaderGetMateRefIndex() {
        SAMRecord sam = new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "3S9M", null, 2);
        SAMFileHeader samHeader = sam.getHeader();
        Assert.assertTrue(null != samHeader);

        // setting header to null retains mate ref index
        sam.setMateReferenceIndex(3);
        Assert.assertTrue(sam.getMateReferenceIndex().equals(3));
        sam.setHeader(null);
        Assert.assertTrue(sam.getMateReferenceIndex().equals(3));
    }

    @Test(expectedExceptions = SAMException.class)
    public void testNullHeaderSetMateRefIndex() {
        SAMRecord sam = new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "3S9M", null, 2);
        sam.setHeader(null);
        sam.setMateReferenceIndex(3);
    }

    @Test(expectedExceptions = SAMException.class)
    public void testNullHeaderGetReadGroup() {
        SAMRecord sam = new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "3S9M", null, 2);
        SAMFileHeader samHeader = sam.getHeader();
        Assert.assertTrue(null != samHeader);

        Assert.assertTrue(null != sam.getReadGroup() && sam.getReadGroup().getId().equals("1"));
        sam.setHeader(null);
        sam.getReadGroup();
    }

    @Test(dataProvider = "serializationTestData")
    public void testNullHeaderSerialization(final File inputFile) throws Exception {
        final SamReader reader = SamReaderFactory.makeDefault().open(inputFile);
        final SAMRecord initialSAMRecord = reader.iterator().next();
        reader.close();

        initialSAMRecord.setHeader(null);
        final SAMRecord deserializedSAMRecord = TestUtil.serializeAndDeserialize(initialSAMRecord);
        Assert.assertEquals(deserializedSAMRecord, initialSAMRecord, "Deserialized SAMRecord not equal to original SAMRecord");
    }

    @Test(dataProvider = "offsetAtReferenceData")
    public void testNullHeaderValidation(String cigar, int posInReference, int expectedPosInRead, boolean returnLastBaseIfDeleted) {
        SAMRecord sam = new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, cigar, null, 2);
        sam.setHeader(null);

        List<SAMValidationError> validationErrors = sam.isValid(false);
        boolean foundMissing = false;
        for (SAMValidationError val : validationErrors) {
            if (val.getType() == SAMValidationError.Type.MISSING_HEADER) {
                foundMissing = true;
                break;
            }
        }
        Assert.assertTrue(foundMissing);

        validationErrors = sam.isValid(true);
        Assert.assertTrue(validationErrors != null &&
                            validationErrors.size() != 0 &&
                            validationErrors.get(0)!= null &&
                            validationErrors.get(0).getType() == SAMValidationError.Type.MISSING_HEADER);
    }

}
