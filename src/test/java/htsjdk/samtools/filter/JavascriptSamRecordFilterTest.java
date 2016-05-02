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
package htsjdk.samtools.filter;

import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.CloserUtil;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author Pierre Lindenbaum PhD Institut du Thorax - INSERM - Nantes - France
 */

public class JavascriptSamRecordFilterTest {
    final File testDir = new File("./src/test/resources/htsjdk/samtools");

    @DataProvider
    public Object[][] jsData() {
        return new Object[][] { { "unsorted.sam", "samFilter01.js", 8 }, { "unsorted.sam", "samFilter02.js", 10 }, };
    }

    @Test(dataProvider = "jsData")
    public void testJavascriptFilters(final String samFile, final String javascriptFile, final int expectCount) {
        final SamReaderFactory srf = SamReaderFactory.makeDefault();
        final SamReader samReader = srf.open(new File(testDir, samFile));
        final JavascriptSamRecordFilter filter;
        try {
            filter = new JavascriptSamRecordFilter(new File(testDir, javascriptFile),
                    samReader.getFileHeader());    
        } catch (IOException err) {
            Assert.fail("Cannot read script",err);
            return;
        }
        final SAMRecordIterator iter = samReader.iterator();
        int count = 0;
        while (iter.hasNext()) {
            if (filter.filterOut(iter.next())) {
                continue;
            }
            ++count;
        }
        iter.close();
        CloserUtil.close(samReader);
        Assert.assertEquals(count, expectCount, "Expected number of reads " + expectCount + " but got " + count);
    }
}
