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
}
