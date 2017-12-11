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
     * Basic positive and negative tests for the TagFilter
     *
     * @param tag               The tag to be tested
     * @param validValues       The values the filter should test for
     * @param testValue         The value to test for in the record
     * @param expectedResult    The expected result (true is the sequence should match the filter, otherwise false)
     */
    @Test(dataProvider="dataIncludeFilter")
    public void testIncludeTagFilter(final String testName, final String tag, final List<Object> validValues,
                              final Object testValue, final boolean expectedResult, final Boolean includeReads) {
        testTagFilter(testName, tag, validValues, testValue, expectedResult, includeReads);
    }

    @Test(dataProvider="dataExcludeFilter")
    public void testExcludeTagFilter(final String testName, final String tag, final List<Object> validValues,
                                     final Object testValue, final boolean expectedResult, Boolean includeReads) {
        testTagFilter(testName, tag, validValues, testValue, expectedResult, includeReads);
    }

    @Test(dataProvider="dataDefaultFilter")
    public void testDefaultTagFilter(final String testName, final String tag, final List<Object> validValues,
                                     final Object testValue, final boolean expectedResult, Boolean includeReads) {
        testTagFilter(testName, tag, validValues, testValue, expectedResult, includeReads);
    }

    /**
     * Data for various sequences which may or may not match the explicit includeReads=true filter.
     */
    @DataProvider(name = "dataIncludeFilter")
    private Object[][] getInclueTagFilterTestData()
    {
        return new Object[][]{
                {"Basic positive test - includeReads true", ReservedTagConstants.XN, Arrays.asList(1), 1, false, true},
                {"Multi-value positive test - includeReads true", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1, false, true},
                {"Incorrect value negative test - includeReads true", ReservedTagConstants.XN, Arrays.asList(1), 2, true, true},
                {"Null value negative test - includeReads true", ReservedTagConstants.XN, Arrays.asList(1), null, true, true}
        };
    }

    /**
     * Data for various sequences which may or may not match the explicit includeReads=false filter.
     */
    @DataProvider(name = "dataExcludeFilter")
    private Object[][] getExcludeTagFilterTestData()
    {
        return new Object[][]{
                {"Basic positive test - includeReads false", ReservedTagConstants.XN, Arrays.asList(1), 1, true, false},
                {"Multi-value positive test - includeReads false", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1, true, false},
                {"Incorrect value negative test - includeReads false", ReservedTagConstants.XN, Arrays.asList(1), 2, false, false},
                {"Null value negative test - includeReads false", ReservedTagConstants.XN, Arrays.asList(1), null, false, false}
        };
    }

    /**
     * Data for various sequences which may or may not match the default filter.
     */
    @DataProvider(name = "dataDefaultFilter")
    private Object[][] getDefaultTagFilterTestData()
    {
        return new Object[][]{
                {"Basic positive test - includeReads false", ReservedTagConstants.XN, Arrays.asList(1), 1, true, null},
                {"Multi-value positive test - includeReads false", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1, true, null},
                {"Incorrect value negative test - includeReads false", ReservedTagConstants.XN, Arrays.asList(1), 2, false, null},
                {"Null value negative test - includeReads false", ReservedTagConstants.XN, Arrays.asList(1), null, false, null}
        };
    }

    // Base single record test
    private void testTagFilter(final String testName, final String tag, final List<Object> validValues,
                                     final Object testValue, final boolean expectedResult, final Boolean includeReads) {
        final TagFilter filter = new TagFilter(tag, validValues, includeReads);
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
        builder.addUnmappedFragment("testfrag");
        final SAMRecord record = builder.iterator().next();
        if (testValue != null) {
            record.setAttribute(tag, testValue);
        }
        Assert.assertEquals(filter.filterOut(record), expectedResult, testName);
    }

    /**

        Start Paired Tests

     */
    @Test(dataProvider="dataDefaultMatchingPairedFilter")
    public void testDefaultPairedEndMatchingTagFilter(final String testName, final String tag, final List<Object> validValues,
                                                          final Object testValue, final boolean expectedResult, Boolean includeReads,
                                                          final boolean matchPairs) {
        testPairedReadTagFilter(testName, tag, validValues, testValue, expectedResult, includeReads, matchPairs);
    }

    @Test(dataProvider="dataDefaultNonMatchingPairedFilter")
    public void testDefaultPairedEndNonMatchingTagFilter(final String testName, final String tag, final List<Object> validValues,
                                                          final Object testValue, final boolean expectedResult, Boolean includeReads,
                                                          final boolean matchPairs) {
        testPairedReadTagFilter(testName, tag, validValues, testValue, expectedResult, includeReads, matchPairs);
    }

    @Test(dataProvider="dataIncludeNonMatchingPairedFilter")
    public void testIncludePairedEndNonMatchingTagFilter(final String testName, final String tag, final List<Object> validValues,
                                                             final Object testValue, final boolean expectedResult, Boolean includeReads,
                                                             final boolean matchPairs) {
        testPairedReadTagFilter(testName, tag, validValues, testValue, expectedResult, includeReads, matchPairs);
    }

    /**
     * Data for various sequences which may or may not match the default filter with paired reads.
     */
    @DataProvider(name = "dataDefaultMatchingPairedFilter")
    private Object[][] getDefaultMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {"Paired Read Matching Basic positive test - includeReads false", ReservedTagConstants.XN, Arrays.asList(1), 1, true, null, true},
                {"Paired Read Matching Multi-value positive test - includeReads false", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1, true, null, true},
                {"Paired Read Matching Incorrect value negative test - includeReads false", ReservedTagConstants.XN, Arrays.asList(1), 2, false, null, true},
                {"Paired Read Matching Null value negative test - includeReads false", ReservedTagConstants.XN, Arrays.asList(1), null, false, null, true}
        };
    }

    /**
     * Data for various sequences which may or may not match the default filter with paired reads.
     */
    @DataProvider(name = "dataDefaultNonMatchingPairedFilter")
    private Object[][] getDefaultNonMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {"Paired Read Non Matching Basic positive test- includeReads false", ReservedTagConstants.XN, Arrays.asList(1), 1, false, null, false},
                {"Paired Read Non Matching Multi-value positive test- includeReads false", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1, false, null, false},
                {"Paired Read Non Matching Incorrect value negative test- includeReads false", ReservedTagConstants.XN, Arrays.asList(1), 2, false, null, false},
                // this is looking for value `null` which in our test cases
                {"Paired Read Non Matching Null value negative test- includeReads false", ReservedTagConstants.XN, Arrays.asList(1), null, false, null, false},
        };
    }

    /**
     * Data for various sequences which may or may not match the includeReads=true filter with paired reads.
     */
    @DataProvider(name = "dataIncludeNonMatchingPairedFilter")
    private Object[][] getIncludeNonMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {"Paired Read Non Matching Basic positive test - includeReads true", ReservedTagConstants.XN, Arrays.asList(1), 1, false, true, false},
                {"Paired Read Non Matching Multi-value positive tes - includeReads truet", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1, false, true, false},
                {"Paired Read Non Matching Incorrect value negative test - includeReads true", ReservedTagConstants.XN, Arrays.asList(1), 2, true, true, false},
                // this is looking for value `null` which in our test cases
                {"Paired Read Non Matching Null value negative test - includeReads true", ReservedTagConstants.XN, Arrays.asList(1), null, true, true, false},
        };
    }

    /**
     * Data for various sequences which may or may not match the includeReads=true default filter with paired reads.
     */
    @DataProvider(name = "dataIncludeMatchingPairedFilter")
    private Object[][] getIncludeMatchingPairedTagFilterTestData()
    {
        return new Object[][]{
                {"Paired Read Matching Basic positive test", ReservedTagConstants.XN, Arrays.asList(1), 1, false, true, true},
                {"Paired Read Matching Multi-value positive test", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1, false, true, true},
                {"Paired Read Matching Incorrect value negative test", ReservedTagConstants.XN, Arrays.asList(1), 2, true, true, true},
                // this is looking for value `null` which in our test cases
                {"Paired Read Matching Null value negative test", ReservedTagConstants.XN, Arrays.asList(1), null, true, true, true},
        };
    }

    // Base paired read test
    private void testPairedReadTagFilter(final String testName, final String tag, final List<Object> validValues,
                                         final Object testValue, final boolean expectedResult, final Boolean includeReads,
                                         final boolean matchPairs) {
        final TagFilter filter = new TagFilter(tag, validValues, includeReads);
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
        builder.addPair("Paired", 1, 100, 200);
        CloseableIterator<SAMRecord> iterator = builder.iterator();
        final SAMRecord record1 = iterator.next();
        if (testValue != null) {
            record1.setAttribute(tag, testValue);
        }
        final SAMRecord record2 = iterator.next();
        if (matchPairs && testValue != null) {
            record2.setAttribute(tag, testValue);
        } else if (!matchPairs){
            record2.setAttribute(tag, 0);
        }
        Assert.assertEquals(filter.filterOut(record1, record2), expectedResult, testName);
    }
}
