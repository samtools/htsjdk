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
     *
     * @param testModule                Used with testName to uniquely identify tests
     * @param commonValuesIndex         The index of the common values to grab from commonTestValues
     * @param firstReadExpectedResult   The expected result of the first read (true is the sequence should match the filter, otherwise false)
     * @param pairedExpectedResult      The expected result of the paired reads (true is the sequence should match the filter, otherwise false)
     * @param includeReads              The value of includeReads for the filter
     * @param matchPairs                Whether or not to have matching values for tag the test is testing for
     *
     */

    @Test(dataProvider="testData")
    public void testTagFilter(final String testModule, final int commonValuesIndex, final boolean firstReadExpectedResult,
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
        Assert.assertEquals(filter.filterOut(record1), firstReadExpectedResult, testModule + " - " + testName);

        final SAMRecord record2 = iterator.next();
        if (matchPairs && testValue != null) {
            record2.setAttribute(tag, testValue);
        } else if (!matchPairs){
            record2.setAttribute(tag, 0);
        }
        // Test paired reads
        Assert.assertEquals(filter.filterOut(record1, record2), pairedExpectedResult, testModule + " - " + testName);
    }

    /**
     * Data for various sequences which may or may not match the filter.
     */

    private final Object[][] commonTestValues =
            new Object[][]{
                    {"Paired Read Matching Basic positive test", ReservedTagConstants.XN, Arrays.asList(1), 1},
                    {"Paired Read Matching Multi-value positive test", ReservedTagConstants.XN, Arrays.asList(1,2,3), 1},
                    {"Paired Read Matching Incorrect value negative test", ReservedTagConstants.XN, Arrays.asList(1), 2},
                    {"Paired Read Matching Null value negative test", ReservedTagConstants.XN, Arrays.asList(1), null}
            };

    @DataProvider(name = "testData")
    private Object[][] getTestData()
    {
        return new Object[][]{
                {"dataDefaultMatchingPairedFilter", 0, true, true, null, true},
                {"dataDefaultMatchingPairedFilter", 1, true, true, null, true},
                {"dataDefaultMatchingPairedFilter", 2, false, false,  null, true},
                {"dataDefaultMatchingPairedFilter",3, false, false, null, true},
                {"dataDefaultNonMatchingPairedFilter", 0, true, false, null, false},
                {"dataDefaultNonMatchingPairedFilter", 1, true, false, null, false},
                {"dataDefaultNonMatchingPairedFilter", 2, false, false, null, false},
                {"dataDefaultNonMatchingPairedFilter", 3, false, false, null, false},
                {"dataExcludeMatchingPairedFilter", 0, true, true, false, true},
                {"dataExcludeMatchingPairedFilter", 1, true, true, false, true},
                {"dataExcludeMatchingPairedFilter", 2, false, false,  false, true},
                {"dataExcludeMatchingPairedFilter", 3, false, false, false, true},
                {"dataExcludeNonMatchingPairedFilter", 0, true, false, false, false},
                {"dataExcludeNonMatchingPairedFilter", 1, true, false, false, false},
                {"dataExcludeNonMatchingPairedFilter", 2, false, false, false, false},
                {"dataExcludeNonMatchingPairedFilter", 3, false, false, false, false},
                {"dataIncludeNonMatchingPairedFilter", 0, false, false, true, false},
                {"dataIncludeNonMatchingPairedFilter", 1, false, false, true, false},
                {"dataIncludeNonMatchingPairedFilter", 2, true, true, true, false},
                {"dataIncludeNonMatchingPairedFilter", 3, true, true, true, false},
                {"dataIncludeMatchingPairedFilter", 0, false, false, true, true},
                {"dataIncludeMatchingPairedFilter", 1, false, false, true, true},
                {"dataIncludeMatchingPairedFilter", 2, true, true, true, true},
                {"dataIncludeMatchingPairedFilter", 3, true, true, true, true}
        };
    }
}
