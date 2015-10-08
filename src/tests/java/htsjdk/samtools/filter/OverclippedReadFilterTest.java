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
package htsjdk.samtools.filter;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordSetBuilder;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class OverclippedReadFilterTest {
    private final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
    private final int unclippedBasesThreshold = 30;

    private SAMRecord buildFrag(final String name, final String cigarString) {
        // for this test, all we care about is the CIGAR
        return builder.addFrag(name, 0, 1, false, false, cigarString, null, 30);
    }

    @Test(dataProvider = "data")
    public void testOverclippedReadFilter(final String name, final String cigar, final boolean filterSingleEndClips, final boolean shouldFail) {
        final OverclippedReadFilter filter = new OverclippedReadFilter(unclippedBasesThreshold, filterSingleEndClips);
        final SAMRecord rec = buildFrag(name, cigar);
        Assert.assertEquals(filter.filterOut(rec), shouldFail);
    }

    @DataProvider(name = "data")
    private Object[][] testData() {
        return new Object[][]{
                {"foo", "1S10M1S", false, true},
                {"foo", "1S10X1S", false, true},
                {"foo", "1H1S10M1S1H", false, true},
                {"foo", "1S40M1S", false, false},
                {"foo", "1S40X1S", false, false},
                {"foo", "1H10M1S", false, false},
                {"foo", "1S10M1H", false, false},
                {"foo", "10M1S", false, false},
                {"foo", "1S10M", false, false},
                {"foo", "10M1S", true, true},
                {"foo", "1S10M", true, true},
                {"foo", "1S10M10D10M1S", false, true},
                {"foo", "1S1M40I1S", false, false},
                {"foo", "1S10I1S", false, true},
                {"foo", "1S40I1S", false, false},
                {"foo", "1S40I1S", true, false},
                {"foo", "25S40I25M", true, false},
                {"foo", "25S25M", true, true},
                {"foo", "25S25X", true, true},
                {"foo", "25S25H", true, true},
                {"foo", "25S25H", false, false},
                {"foo", "25S25M25S", false, true},
                {"foo", "25M25S", true, true},
                {"foo", "25S25M", true, true},
                {"foo", "25S35S", true, true},
                {"foo", "25S35M25S", true, false},
                {"foo", "35M25S", true, false},
                {"foo", "25S35M", true, false}
        };
    }
}
