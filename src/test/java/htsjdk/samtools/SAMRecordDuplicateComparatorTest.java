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
package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The tests listed here are not exhaustive, and duplicate some of the work found in Picard's MarkDuplicates test classes.
 *
 * @author nhomer
 */
public class SAMRecordDuplicateComparatorTest {
    
    private final static SAMRecordDuplicateComparator comparator = new SAMRecordDuplicateComparator();

    protected final static int DEFAULT_BASE_QUALITY = 10;

    private SAMRecordSetBuilder getSAMRecordSetBuilder() {
        return  new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted);
    }
    
    private boolean matchesExpected(final int expected, final int actual) {
        if (expected < 0) return (actual < 0);
        else if (0 == expected) return (0 == actual);
        else return (0 < actual);
    }

    private void assertEquals(List<Integer> expectedValues, final SAMRecordSetBuilder records, boolean fullCompare) {
        SAMRecord previous = null, current;
        
        Assert.assertEquals(expectedValues.size() + 1, records.size());

        Iterator<SAMRecord> iterator = records.getRecords().iterator();
        Iterator<Integer> integerIterator = expectedValues.iterator();
        while (iterator.hasNext()) {
            current = iterator.next();

            if (null != previous) {
                if (fullCompare) {
                    Assert.assertTrue(matchesExpected(integerIterator.next(), comparator.compare(previous, current)));
                }
                else {
                    Assert.assertTrue(matchesExpected(integerIterator.next(), comparator.duplicateSetCompare(previous, current)));
                }
            }

            previous = current;
        }
    }
    
    private void assertEquals(int expected, final SAMRecordSetBuilder records, boolean fullCompare) {
        assertEquals(Collections.singletonList(expected), records, fullCompare);
    }

    /***
     * Tests for comparing duplicate sets only, fragment reads.
     */
    @Test
    public void testFragmentsSamePositive() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();
        
        records.addFrag("READ0", 1, 1, false);
        records.addFrag("READ1", 1, 1, false);

        assertEquals(0, records, false);
    }

    @Test
    public void testFragmentsSameNegative() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addFrag("READ0", 1, 1, true);
        records.addFrag("READ1", 1, 1, true);

        assertEquals(0, records, false);
    }

    @Test
    public void testFragmentsDifferentStrand() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addFrag("READ0", 1, 1, false);
        records.addFrag("READ1", 1, 1, true);

        assertEquals(-1, records, false);
    }

    @Test
    public void testFragmentsDifferentContig() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addFrag("READ0", 1, 1, false);
        records.addFrag("READ1", 2, 1, false);
    
        assertEquals(-1, records, false);
    }

    @Test
    public void testFragmentsDifferentStart() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addFrag("READ0", 1, 1, false);
        records.addFrag("READ1", 1, 2, false);

        assertEquals(-1, records, false);
    }
    
    @Test
    public void testFragmentsDifferentCigar() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addFrag("READ0", 1, 1, false, false, "50M", null, DEFAULT_BASE_QUALITY);
        records.addFrag("READ1", 1, 1, false, false, "51M", null, DEFAULT_BASE_QUALITY);

        assertEquals(0, records, false);
    }

    @Test
    public void testFragmentsOneUnmapped() {

        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addFrag("READ0", 1, 1, false, false, "50M", null, DEFAULT_BASE_QUALITY);
        records.addFrag("READ1", 1, 1, false, true, null, null, DEFAULT_BASE_QUALITY);

        assertEquals(-1, records, false);
    }

    @Test
    public void testFragmentsOppositeStrandsLessThan() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addFrag("READ1", 1, 100, false, false, "50M", null, DEFAULT_BASE_QUALITY);
        records.addFrag("READ0", 1, 51, true, false, "50M", null, DEFAULT_BASE_QUALITY);

        assertEquals(-1, records, false);
    }

    @Test
    public void testFragmentsOppositeStrandsGreaterThan() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addFrag("READ0", 1, 50, true, false, "50M", null, DEFAULT_BASE_QUALITY);
        records.addFrag("READ1", 1, 101, false, false, "50M", null, DEFAULT_BASE_QUALITY);
        
        assertEquals(-1, records, false);
    }

    @Test
    public void testFragmentsWithSoftClipping() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addFrag("READ0", 1, 55, false, false, "5S50M", null, DEFAULT_BASE_QUALITY);
        records.addFrag("READ1", 1, 60, false, false, "10S50M", null, DEFAULT_BASE_QUALITY);
        
        assertEquals(0, records, false);
    }

    @Test
    public void testFragmentsReverseStrandWithSoftClipping() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addFrag("READ0", 1, 55, true, false, "50M10S", null, DEFAULT_BASE_QUALITY);
        records.addFrag("READ1", 1, 60, true, false, "50M5S", null, DEFAULT_BASE_QUALITY);
        
        assertEquals(0, records, false);
    }

    /*
    @Test
    public void testPairedSamePositions() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addPair("READ0", 1, 55, 55);
        records.addPair("READ1", 1, 55, 55);

        assertEquals(0, records, false);
    }
    */

    @Test
    public void testPairedFirstEndDifferent() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addPair("READ0", 1, 55, 55);
        records.addPair("READ1", 2, 55, 55);

        assertEquals(Arrays.asList(-1,-1,-1), records, false);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testNullHeaders() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addPair("READ0", 1, 55, 55);
        records.addPair("READ1", 2, 55, 55);
        Collection<SAMRecord> recs = records.getRecords();
        for (SAMRecord rec : recs) {
            rec.setHeader(null);
        }

        assertEquals(Arrays.asList(-1, -1, -1), records, false);
    }

}
