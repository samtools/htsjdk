/*
 * The MIT License
 *
 * Copyright (c) 2026 Donncha O'Toole
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

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SAMUtilsReferenceOverlapTest extends HtsjdkTest {
    @DataProvider(name = "references")
    public Object[][] references() {
        return new Object[][] {
            {0, 0, 1051, false, 100},
            {0, 1, 1051, false, 0},
            {1, 0, 1051, false, 0},
            {128, 128, 1051, false, 100},
            {128, 129, 1051, false, 0},
            {0, 0, 1001, false, 150},
            {0, 1, 1001, false, 0},
            {0, 0, 1001, true, 0},
            {0, 1, 1001, true, 0},
            {0, 1, 1151, false, 0},
            {0, 1, 951, false, 0}
        };
    }

    @Test(dataProvider = "references")
    public void testReferenceIdentity(
            final int reference,
            final int mateReference,
            final int mateStart,
            final boolean firstOfPair,
            final int expected) {
        final SAMFileHeader header = new SAMFileHeader();
        for (int i = 0; i < 130; ++i) {
            header.addSequence(new SAMSequenceRecord("chr" + i, 10000));
        }
        final SAMRecord record = new SAMRecord(header);
        record.setReadName("reference-identity");
        record.setReadPairedFlag(true);
        record.setFirstOfPairFlag(firstOfPair);
        record.setSecondOfPairFlag(!firstOfPair);
        record.setReferenceIndex(reference);
        record.setMateReferenceIndex(mateReference);
        record.setAlignmentStart(1001);
        record.setMateAlignmentStart(mateStart);
        record.setCigarString("150M");
        record.setReadString("A".repeat(150));
        Assert.assertEquals(SAMUtils.getNumOverlappingAlignedBasesToClip(record), expected);
        if (reference != mateReference) {
            final SAMRecord clipped = SAMUtils.clipOverlappingAlignedBases(record, true);
            Assert.assertSame(clipped, record);
            Assert.assertEquals(clipped.getCigarString(), "150M");
            Assert.assertFalse(clipped.getReadUnmappedFlag());
        }
    }

    @DataProvider(name = "headerlessReferences")
    public Object[][] headerlessReferences() {
        return new Object[][] {{"chr1", 100}, {"chr2", 0}};
    }

    @Test(dataProvider = "headerlessReferences")
    public void testHeaderlessRecords(final String mateReference, final int expected) {
        final SAMRecord record = new SAMRecord(null);
        record.setReadPairedFlag(true);
        record.setSecondOfPairFlag(true);
        record.setReferenceName("chr1");
        record.setMateReferenceName(mateReference);
        record.setAlignmentStart(1001);
        record.setMateAlignmentStart(1051);
        record.setCigarString("150M");
        Assert.assertEquals(SAMUtils.getNumOverlappingAlignedBasesToClip(record), expected);
    }
}
