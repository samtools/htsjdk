/*
 * The MIT License
 *
 * Copyright (c) 2015 Pierre Lindenbaum @yokofakun Institut du Thorax - Nantes - France
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

import htsjdk.variant.vcf.VCFFileReader;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author Pierre Lindenbaum PhD Institut du Thorax - INSERM - Nantes - France
 */

public class JavascriptVariantFilterTest {
    final File testDir = new File("src/test/resources/htsjdk/variant");

    @DataProvider
    public Object[][] jsData() {
        return new Object[][] {
                { "ILLUMINA.wex.broad_phase2_baseline.20111114.both.exome.genotypes.1000.vcf", "variantFilter01.js",61 },
                { "ILLUMINA.wex.broad_phase2_baseline.20111114.both.exome.genotypes.1000.vcf", "variantFilter02.js",38 }, };
    }

    @Test(dataProvider = "jsData")
    public void testJavascriptFilters(final String vcfFile, final String javascriptFile, final int expectCount) {
        final File vcfInput = new File(testDir, vcfFile);
        final File jsInput = new File(testDir, javascriptFile);
        final VCFFileReader vcfReader = new VCFFileReader(vcfInput, false);
        final JavascriptVariantFilter filter;
        try {
            filter = new JavascriptVariantFilter(jsInput, vcfReader.getFileHeader());
        } catch (IOException err) {
            Assert.fail("cannot read script "+jsInput, err);
            vcfReader.close();
            return;
        }
        final FilteringVariantContextIterator iter = new FilteringVariantContextIterator(vcfReader.iterator(), filter);
        int count = 0;
        while (iter.hasNext()) {
            iter.next();
            ++count;
        }
        iter.close();
        vcfReader.close();
        Assert.assertEquals(count, expectCount, "Expected number of variants " + expectCount + " but got " + count);
    }
}
