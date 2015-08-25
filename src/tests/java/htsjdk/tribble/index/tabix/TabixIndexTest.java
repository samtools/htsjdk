/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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
package htsjdk.tribble.index.tabix;

import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.tribble.util.LittleEndianOutputStream;
import htsjdk.tribble.util.TabixUtils;
import htsjdk.variant.vcf.VCFFileReader;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Iterator;

import static org.testng.Assert.assertEquals;

public class TabixIndexTest {
    private static final File SMALL_TABIX_FILE = new File("testdata/htsjdk/tribble/tabix/trioDup.vcf.gz.tbi");
    private static final File BIGGER_TABIX_FILE = new File("testdata/htsjdk/tribble/tabix/bigger.vcf.gz.tbi");

    /**
     * Read an existing index from disk, write it to a temp file, read that in, and assert that both in-memory
     * representations are identical.  Disk representations may not be identical due to arbitrary bin order and
     * compression differences.
     */
    @Test(dataProvider = "readWriteTestDataProvider")
    public void readWriteTest(final File tabixFile) throws Exception {
        final TabixIndex index = new TabixIndex(tabixFile);
        final File indexFile = File.createTempFile("TabixIndexTest.", TabixUtils.STANDARD_INDEX_EXTENSION);
        indexFile.deleteOnExit();
        final LittleEndianOutputStream los = new LittleEndianOutputStream(new BlockCompressedOutputStream(indexFile));
        index.write(los);
        los.close();
        final TabixIndex index2 = new TabixIndex(indexFile);
        assertEquals(index, index2);
        // Unfortunately, can't do byte comparison of original file and temp file, because 1) different compression
        // levels; and more importantly, arbitrary order of bins in bin list.
    }

    @DataProvider(name = "readWriteTestDataProvider")
    public Object[][] readWriteTestDataProvider() {
        return new Object[][] {
                {SMALL_TABIX_FILE},
                {BIGGER_TABIX_FILE}
        };
    }

    @Test
    public void testQuery() {
        File inputFile = new File("testdata/htsjdk/tribble/tabix/YRI.trio.2010_07.indel.sites.vcf.gz");
        File indexFile = new File("testdata/htsjdk/tribble/tabix/YRI.trio.2010_07.indel.sites.vcf.gz.tbi");
        VCFFileReader reader = new VCFFileReader(inputFile, indexFile);

        // just somewhere
        assertEquals(42, countIteratedElements(reader.query("1", 868379 - 1, 1006891 + 1)));
        // chromosome start
        assertEquals(13, countIteratedElements(reader.query("1", 1, 836463 + 1)));
        // chromosome end end
        assertEquals(36, countIteratedElements(reader.query("1", 76690833 - 1, 76837502 + 11111111)));
        // where's no one feature
        assertEquals(0, countIteratedElements(reader.query("1", 36606472 + 1, 36623523 - 1)));
        // before chromosome
        assertEquals(0, countIteratedElements(reader.query("1", 1 , 10)));
        // after chromosome
        assertEquals(0, countIteratedElements(reader.query("1", 76837502*15 , 76837502*16)));
    }

    private static int countIteratedElements(Iterator iterator) {
        int counter = 0;
        while(iterator.hasNext()) {
            iterator.next();
            counter++;
        }
        return counter;
    }
}
