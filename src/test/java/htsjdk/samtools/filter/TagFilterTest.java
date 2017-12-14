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
    public void testDefaultPairedEndMatchingTagFilter(final String testName, final String tag, final List<Object> validValues,
                                                      final Object testValue, final boolean firstReadExpectedResult,
                                                      final boolean pairedExpectedResult, final Boolean includeReads,
                                                      final boolean matchPairs) {
        testTagFilter(testName, tag, validValues, testValue, firstReadExpectedResult, pairedExpectedResult, includeReads, matchPairs);
    }

    @Test(dataProvider="dataDefaultNonMatchingPairedFilter")
    public void testDefaultPairedEndNonMatchingTagFilter(final String testName, final String tag, final List<Object> validValues,
                                                         final Object testValue, final boolean firstReadExpectedResult,
                                                         final boolean pairedExpectedResult, final Boolean includeReads,
                                                         final boolean matchPairs) {
        testTagFilter(testName, tag, validValues, testValue, firstReadExpectedResult, pairedExpectedResult, includeReads, matchPairs);
    }
    @Test(dataProvider="dataExcludeMatchingPairedFilter")
    public void testExludePairedEndMatchingTagFilter(final String testName, final String tag, final List<Object> validValues,
                                                      final Object testValue, final boolean firstReadExpectedResult,
                                                      final boolean pairedExpectedResult, final Boolean includeReads,
                                                      final boolean matchPairs) {
        testTagFilter(testName, tag, validValues, testValue, firstReadExpectedResult, pairedExpectedResult, includeReads, matchPairs);
    }

    @Test(dataProvider="dataExcludeNonMatchingPairedFilter")
    public void testExcludePairedEndNonMatchingTagFilter(final String testName, final String tag, final List<Object> validValues,
                                                         final Object testValue, final boolean firstReadExpectedResult,
                                                         final boolean pairedExpectedResult, final Boolean includeReads,
                                                         final boolean matchPairs) {
        testTagFilter(testName, tag, validValues, testValue, firstReadExpectedResult, pairedExpectedResult, includeReads, matchPairs);
    }

    @Test(dataProvider="dataIncludeNonMatchingPairedFilter")
    public void testIncludePairedEndNonMatchingTagFilter(final String testName, final String tag, final List<Object> validValues,
                                                         final Object testValue, final boolean firstReadExpectedResult,
                                                         final boolean pairedExpectedResult, final Boolean includeReads,
                                                         final boolean matchPairs) {
        testTagFilter(testName, tag, validValues, testValue, firstReadExpectedResult, pairedExpectedResult, includeReads, matchPairs);
    }

    @Test(dataProvider="dataIncludeMatchingPairedFilter")
    public void testIncludePairedEndMatchingTagFilter(final String testName, final String tag, final List<Object> validValues,
                                                         final Object testValue, final boolean firstReadExpectedResult,
                                                         final boolean pairedExpectedResult, final Boolean includeReads,
                                                         final boolean matchPairs) {
        testTagFilter(testName, tag, validValues, testValue, firstReadExpectedResult, pairedExpectedResult, includeReads, matchPairs);
    }

    /**
     *
     * @param tag                       The tag to be tested
     * @param validValues               The values the filter should test for
     * @param testValue                 The value to test for in the record
     * @param firstReadExpectedResult   The expected result of the first read (true is the sequence should match the filter, otherwise false)
     * @param pairedExpectedResult      The expected result of the paired reads (true is the sequence should match the filter, otherwise false)
     * @param includeReads              The value of includeReads for the filter
     * @param matchPairs                Whether or not to have matching values for tag the test is testing for
     *
     */

    private void testTagFilter(final String testName, final String tag, final List<Object> validValues,
                               final Object testValue, final boolean firstReadExpectedResult,
                               final boolean pairedExpectedResult, final Boolean includeReads,
                               final boolean matchPairs) {
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

    @DataProvider(name = "dataDefaultMatchingPairedFilter")
    private Object[][] getDefaultMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {"Paired Read Matching Basic positive test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1), 1, true, true, null, true},
                {"Paired Read Matching Multi-value positive test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1, true, true, null, true},
                {"Paired Read Matching Incorrect value negative test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1), 2, false, false,  null, true},
                {"Paired Read Matching Null value negative test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1), null, false, false, null, true}
        };
    }

    @DataProvider(name = "dataDefaultNonMatchingPairedFilter")
    private Object[][] getDefaultNonMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {"Paired Read Non Matching Basic positive test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1), 1, true, false, null, false},
                {"Paired Read Non Matching Multi-value positive test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1, true, false, null, false},
                {"Paired Read Non Matching Incorrect value negative test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1), 2, false, false, null, false},
                {"Paired Read Non Matching Null value negative test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1), null, false, false, null, false},
        };
    }

    @DataProvider(name = "dataExcludeMatchingPairedFilter")
    private Object[][] getExcludeMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {"Paired Read Matching Basic positive test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1), 1, true, true, false, true},
                {"Paired Read Matching Multi-value positive test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1, true, true, false, true},
                {"Paired Read Matching Incorrect value negative test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1), 2, false, false,  false, true},
                {"Paired Read Matching Null value negative test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1), null, false, false, false, true}
        };
    }

    @DataProvider(name = "dataExcludeNonMatchingPairedFilter")
    private Object[][] getExcludeNonMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {"Paired Read Non Matching Basic positive test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1), 1, true, false, false, false},
                {"Paired Read Non Matching Multi-value positive test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1, true, false, false, false},
                {"Paired Read Non Matching Incorrect value negative test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1), 2, false, false, false, false},
                {"Paired Read Non Matching Null value negative test - default includeReads", ReservedTagConstants.XN, Arrays.asList(1), null, false, false, false, false},
        };
    }

    @DataProvider(name = "dataIncludeNonMatchingPairedFilter")
    private Object[][] getIncludeNonMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {"Paired Read Non Matching Basic positive test - includeReads true", ReservedTagConstants.XN, Arrays.asList(1), 1, false, false, true, false},
                {"Paired Read Non Matching Multi-value positive tes - includeReads truet", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1, false, false, true, false},
                {"Paired Read Non Matching Incorrect value negative test - includeReads true", ReservedTagConstants.XN, Arrays.asList(1), 2, true, true, true, false},
                {"Paired Read Non Matching Null value negative test - includeReads true", ReservedTagConstants.XN, Arrays.asList(1), null, true, true, true, false},
        };
    }

    @DataProvider(name = "dataIncludeMatchingPairedFilter")
    private Object[][] getIncludeMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {"Paired Read Matching Basic positive test", ReservedTagConstants.XN, Arrays.asList(1), 1, false, false, true, true},
                {"Paired Read Matching Multi-value positive test", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1, false, false, true, true},
                {"Paired Read Matching Incorrect value negative test", ReservedTagConstants.XN, Arrays.asList(1), 2, true, true, true, true},
                {"Paired Read Matching Null value negative test", ReservedTagConstants.XN, Arrays.asList(1), null, true, true, true, true},
        };
    }

}
