/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.function.Consumer;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class SAMRecordQueryNameComparatorTest extends HtsjdkTest {

    private final static SAMRecordQueryNameComparator COMPARATOR = new SAMRecordQueryNameComparator();

    // this test is separated to be able to use the data provider for the SAMRecordQueryHashComparator
    @Test
    public void testCompareDifferentNames() throws Exception {
        final SAMRecord a = new SAMRecord(null);
        a.setReadName("A");
        final SAMRecord b = new SAMRecord(null);
        b.setReadName("B");
        Assert.assertTrue(COMPARATOR.compare(a, b) < 0);
        Assert.assertTrue(COMPARATOR.compare(b, a) > 0);
    }

    private static SAMRecord copyAndSet(final SAMRecord record, final Consumer<SAMRecord> setParams) {
        final SAMRecord copy = record.deepCopy();
        setParams.accept(copy);
        return copy;
    }


    // this test cases are separated for the different names comparison for re-use with SAMRecordQueryHashComparator
    @DataProvider
    public static Object[][] equalNameComparisonData() {
        // base record with the information used in the comparator:
        // - read name A
        // - positive strand -> default
        // - unpaired (without first/second of pair flags) -> explicitly set
        // - primary alignment (and not supplementary) -> explicitly set
        // - no hit index (HI tag) -> default
        final SAMRecord record = new SAMRecord(null);
        record.setReadName("A");
        record.setReadPairedFlag(false);
        record.setFirstOfPairFlag(false);
        record.setSecondOfPairFlag(false);
        // primary/secondary/supplementary alignments
        record.setNotPrimaryAlignmentFlag(false);
        record.setSupplementaryAlignmentFlag(false);

        // record1, record2, comparison value
        return new Object[][] {
                // same record is equals after comparing all the fields
                {record, record, 0},

                // upaired vs. paired
                {record, copyAndSet(record, (r) -> r.setReadPairedFlag(true)), 1},
                {copyAndSet(record, (r) -> r.setReadPairedFlag(true)), record, -1},
                // first/second of pair in natural order
                {copyAndSet(record, r -> {r.setReadPairedFlag(true); r.setFirstOfPairFlag(true);}),
                        copyAndSet(record, r -> {r.setReadPairedFlag(true); r.setSecondOfPairFlag(true);}),
                        -1},
                {copyAndSet(record, r -> {r.setReadPairedFlag(true); r.setSecondOfPairFlag(true);}),
                    copyAndSet(record, r -> {r.setReadPairedFlag(true); r.setFirstOfPairFlag(true);}),
                        1},

                // negative strand is the last
                {record, copyAndSet(record, r -> r.setReadNegativeStrandFlag(true)), -1},

                // primary alignment is first compared to not primary
                {record, copyAndSet(record, r -> r.setNotPrimaryAlignmentFlag(true)), -1},
                {copyAndSet(record, r -> r.setNotPrimaryAlignmentFlag(true)), record, 1},
                // secondary alignment is last compared to primary
                {record, copyAndSet(record, r -> r.setSupplementaryAlignmentFlag(true)), -1},
                {copyAndSet(record, r -> r.setSupplementaryAlignmentFlag(true)), record, 1},

                // the one with HI tag is first if the other is null
                {record, copyAndSet(record, r -> r.setAttribute(SAMTag.HI.name(), 1)), -1},
                {copyAndSet(record, r -> r.setAttribute(SAMTag.HI.name(), 1)), record, 1},
                // if both have HI tag, order by it
                {copyAndSet(record, r -> r.setAttribute(SAMTag.HI.name(), 1)),
                        copyAndSet(record, r -> r.setAttribute(SAMTag.HI.name(), 1)), 0},
                {copyAndSet(record, r -> r.setAttribute(SAMTag.HI.name(), 1)),
                        copyAndSet(record, r -> r.setAttribute(SAMTag.HI.name(), 2)), -1},
                {copyAndSet(record, r -> r.setAttribute(SAMTag.HI.name(), 16)),
                        copyAndSet(record, r -> r.setAttribute(SAMTag.HI.name(), 5)), 1}
        };
    }



    @Test(dataProvider = "equalNameComparisonData")
    public void testCompareEqualNames(final SAMRecord record1, final SAMRecord record2, final int sign) throws Exception {
        final int comparisonResult = COMPARATOR.compare(record1, record2);
        Assert.assertEquals(Integer.signum(comparisonResult),sign);
    }

}