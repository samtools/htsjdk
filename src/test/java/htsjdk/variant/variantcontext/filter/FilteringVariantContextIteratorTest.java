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

package htsjdk.variant.variantcontext.filter;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;

/**
 * Tests for testing the (VariantContext)FilteringVariantContextIterator, and the HeterozygosityFilter
 */

public class FilteringVariantContextIteratorTest {
    final File testDir = new File("src/test/resources/htsjdk/variant");

    @DataProvider
    public Object [][] filteringIteratorData() {
        return new Object[][] {
                {new HeterozygosityFilter(true, "NA00001"), 2},
                {new HeterozygosityFilter(false, "NA00001"), 3},
                {new HeterozygosityFilter(true, null), 2},
                {new HeterozygosityFilter(false, null), 3},
                {new AllPassFilter(), 5},
                {new HeterozygosityFilter(true, "NA00002"), 4},
                {new HeterozygosityFilter(false, "NA00002"), 1},
        };
    }

    @Test(dataProvider = "filteringIteratorData")
    public void testFilteringIterator(final VariantContextFilter filter, final int expectedCount) {

        final File vcf = new File(testDir,"ex2.vcf");
        final VCFFileReader vcfReader = new VCFFileReader(vcf, false);
        final FilteringVariantContextIterator filteringIterator = new FilteringVariantContextIterator(vcfReader.iterator(), filter);
        int count = 0;

        for(final VariantContext vc : filteringIterator) {
            count++;
        }

        Assert.assertEquals(count, expectedCount);
    }

    @DataProvider
    public Object [][] badSampleData() {
        return new Object[][] {
                {"ex2.vcf", "DOES_NOT_EXIST"},
                {"breakpoint.vcf", null},
        };
    }

    @Test(dataProvider = "badSampleData", expectedExceptions = IllegalArgumentException.class)
    public void testMissingSample(final String file, final String sample) {

        final File vcf = new File(testDir, file);
        final VCFFileReader vcfReader = new VCFFileReader(vcf, false);
        final HeterozygosityFilter heterozygosityFilter = new HeterozygosityFilter(true, sample);

        new FilteringVariantContextIterator(vcfReader.iterator(), heterozygosityFilter).next();
    }
}

