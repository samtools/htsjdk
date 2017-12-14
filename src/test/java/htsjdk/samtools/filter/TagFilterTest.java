/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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
package htsjdk.samtools.filter;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.ReservedTagConstants;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordSetBuilder;
import htsjdk.samtools.util.CloseableIterator;
import org.scalactic.Bool;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for the TagFilter class
 */
public class TagFilterTest extends HtsjdkTest {

    /**
        Tests
     */
    @Test(dataProvider="dataDefaultMatchingPairedFilter")
    public void testDefaultPairedEndMatchingTagFilter(final int commonValuesIndex, final boolean firstReadExpectedResult,
                                                      final boolean pairedExpectedResult, final Boolean includeReads,
                                                      final boolean matchPairs) {
        testTagFilter(commonValuesIndex, firstReadExpectedResult, pairedExpectedResult, includeReads, matchPairs);
    }

    @Test(dataProvider="dataDefaultNonMatchingPairedFilter")
    public void testDefaultPairedEndNonMatchingTagFilter(final int commonValuesIndex, final boolean firstReadExpectedResult,
                                                         final boolean pairedExpectedResult, final Boolean includeReads,
                                                         final boolean matchPairs) {
        testTagFilter(commonValuesIndex, firstReadExpectedResult, pairedExpectedResult, includeReads, matchPairs);
    }
    @Test(dataProvider="dataExcludeMatchingPairedFilter")
    public void testExludePairedEndMatchingTagFilter(final int commonValuesIndex, final boolean firstReadExpectedResult,
                                                     final boolean pairedExpectedResult, final Boolean includeReads,
                                                     final boolean matchPairs) {
        testTagFilter(commonValuesIndex, firstReadExpectedResult, pairedExpectedResult, includeReads, matchPairs);
    }

    @Test(dataProvider="dataExcludeNonMatchingPairedFilter")
    public void testExcludePairedEndNonMatchingTagFilter(final int commonValuesIndex, final boolean firstReadExpectedResult,
                                                         final boolean pairedExpectedResult, final Boolean includeReads,
                                                         final boolean matchPairs) {
        testTagFilter(commonValuesIndex, firstReadExpectedResult, pairedExpectedResult, includeReads, matchPairs);
    }

    @Test(dataProvider="dataIncludeNonMatchingPairedFilter")
    public void testIncludePairedEndNonMatchingTagFilter(final int commonValuesIndex, final boolean firstReadExpectedResult,
                                                         final boolean pairedExpectedResult, final Boolean includeReads,
                                                         final boolean matchPairs) {
        testTagFilter(commonValuesIndex, firstReadExpectedResult, pairedExpectedResult, includeReads, matchPairs);
    }

    @Test(dataProvider="dataIncludeMatchingPairedFilter")
    public void testIncludePairedEndMatchingTagFilter(final int commonValuesIndex, final boolean firstReadExpectedResult,
                                                      final boolean pairedExpectedResult, final Boolean includeReads,
                                                      final boolean matchPairs) {
        testTagFilter(commonValuesIndex, firstReadExpectedResult, pairedExpectedResult, includeReads, matchPairs);
    }

    /**
     *
     * @param commonValuesIndex         The index of the common values to grab from commonTestValues
     * @param firstReadExpectedResult   The expected result of the first read (true is the sequence should match the filter, otherwise false)
     * @param pairedExpectedResult      The expected result of the paired reads (true is the sequence should match the filter, otherwise false)
     * @param includeReads              The value of includeReads for the filter
     * @param matchPairs                Whether or not to have matching values for tag the test is testing for
     *
     */

    private void testTagFilter(final int commonValuesIndex, final boolean firstReadExpectedResult,
                               final boolean pairedExpectedResult, final Boolean includeReads,
                               final boolean matchPairs) {

        final String testName = (String) commonTestValues[commonValuesIndex][0];
        final String tag = (String) commonTestValues[commonValuesIndex][1];
        final List<Object> validValues = (List<Object>) commonTestValues[commonValuesIndex][2];
        final Object testValue = commonTestValues[commonValuesIndex][3];

        final TagFilter filter = new TagFilter(tag, validValues, includeReads);
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
        builder.addPair("Paired", 1, 100, 200);
        CloseableIterator<SAMRecord> iterator = builder.iterator();
        final SAMRecord record1 = iterator.next();
        if (testValue != null) {
            record1.setAttribute(tag, testValue);
        }
        // Test first read in pair
        Assert.assertEquals(filter.filterOut(record1), firstReadExpectedResult, testName);

        final SAMRecord record2 = iterator.next();
        if (matchPairs && testValue != null) {
            record2.setAttribute(tag, testValue);
        } else if (!matchPairs){
            record2.setAttribute(tag, 0);
        }
        // Test paired reads
        Assert.assertEquals(filter.filterOut(record1, record2), pairedExpectedResult, testName);
    }

    /**
     * Data for various sequences for test
     */

    private final Object[][] commonTestValues =
            new Object[][]{
                    {"Paired Read Matching Basic positive test", ReservedTagConstants.XN, Arrays.asList(1), 1},
                    {"Paired Read Matching Multi-value positive test", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1},
                    {"Paired Read Matching Incorrect value negative test", ReservedTagConstants.XN, Arrays.asList(1), 2},
                    {"Paired Read Matching Null value negative test", ReservedTagConstants.XN, Arrays.asList(1), null}
            };

    @DataProvider(name = "dataDefaultMatchingPairedFilter")
    private Object[][] getDefaultMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {0, true, true, null, true},
                {1, true, true, null, true},
                {2, false, false,  null, true},
                {3, false, false, null, true}
        };
    }

    @DataProvider(name = "dataDefaultNonMatchingPairedFilter")
    private Object[][] getDefaultNonMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {0, true, false, null, false},
                {1, true, false, null, false},
                {2, false, false, null, false},
                {3, false, false, null, false},
        };
    }

    @DataProvider(name = "dataExcludeMatchingPairedFilter")
    private Object[][] getExcludeMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {0, true, true, false, true},
                {1, true, true, false, true},
                {2, false, false,  false, true},
                {3, false, false, false, true}
        };
    }

    @DataProvider(name = "dataExcludeNonMatchingPairedFilter")
    private Object[][] getExcludeNonMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {0, true, false, false, false},
                {1, true, false, false, false},
                {2, false, false, false, false},
                {3, false, false, false, false},
        };
    }

    @DataProvider(name = "dataIncludeNonMatchingPairedFilter")
    private Object[][] getIncludeNonMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {0, false, false, true, false},
                {1, false, false, true, false},
                {2, true, true, true, false},
                {3, true, true, true, false},
        };
    }

    @DataProvider(name = "dataIncludeMatchingPairedFilter")
    private Object[][] getIncludeMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {0, false, false, true, true},
                {1, false, false, true, true},
                {2, true, true, true, true},
                {3, true, true, true, true},
        };
    }

}
