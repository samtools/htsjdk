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
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SAMUtilsExtendedCigarOverlapTest extends HtsjdkTest {
    private SAMRecord record(final String cigar, final int mateStart) {
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("chr1", 10000));
        final SAMRecord record = new SAMRecord(header);
        record.setReadName("overlap");
        record.setReadPairedFlag(true);
        record.setSecondOfPairFlag(true);
        record.setReferenceIndex(0);
        record.setMateReferenceIndex(0);
        record.setAlignmentStart(1001);
        record.setMateAlignmentStart(mateStart);
        record.setCigarString(cigar);
        record.setReadString("A".repeat(record.getCigar().getReadLength()));
        return record;
    }

    @DataProvider(name = "overlapCounts")
    public Object[][] overlapCounts() {
        final List<Object[]> cases = new ArrayList<>();
        for (final String operator : new String[] {"M", "=", "X"}) {
            cases.add(new Object[] {"150" + operator, 1001, 150});
            cases.add(new Object[] {"150" + operator, 1002, 149});
            cases.add(new Object[] {"150" + operator, 1051, 100});
            cases.add(new Object[] {"150" + operator, 1150, 1});
            cases.add(new Object[] {"150" + operator, 1151, 0});
        }
        cases.add(new Object[] {"40=20X90=", 1051, 100});
        cases.add(new Object[] {"60=5I90=", 1051, 105});
        cases.add(new Object[] {"60=5D90=", 1051, 100});
        cases.add(new Object[] {"40=5I110=", 1051, 100});
        cases.add(new Object[] {"40=5D110=", 1051, 105});
        cases.add(new Object[] {"40=10N110=", 1046, 110});
        cases.add(new Object[] {"5H10S150=10S5H", 1051, 100});
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "overlapCounts")
    public void testOverlapCount(final String cigar, final int mateStart, final int expected) {
        Assert.assertEquals(SAMUtils.getNumOverlappingAlignedBasesToClip(record(cigar, mateStart)), expected);
    }

    @DataProvider(name = "clippedCigars")
    public Object[][] clippedCigars() {
        return new Object[][] {
            {"150M", "50M100S"},
            {"150=", "50=100S"},
            {"150X", "50X100S"},
            {"40=20X90=", "40=10X100S"},
            {"60=5I90=", "50=105S"},
            {"60=5D90=", "50=100S"},
            {"40=5I110=", "40=5I10=100S"},
            {"40=5D110=", "40=5D5=105S"},
            {"10S150=10S", "10S50=110S"},
            {"5H150=", "5H50=100S"}
        };
    }

    @Test(dataProvider = "clippedCigars")
    public void testClippedCigar(final String cigar, final String expected) {
        final SAMRecord original = record(cigar, 1051);
        final SAMRecord clipped = SAMUtils.clipOverlappingAlignedBases(original, true);
        Assert.assertEquals(clipped.getCigarString(), expected);
        Assert.assertEquals(original.getCigarString(), cigar);
        Assert.assertEquals(clipped.getAlignmentStart(), original.getAlignmentStart());
        Assert.assertEquals(clipped.getReadLength(), original.getReadLength());
        Assert.assertFalse(clipped.getReadUnmappedFlag());
    }
}
