package htsjdk.samtools;

/*
 * The MIT License
 *
 * Copyright (c) 2009-2016 The Broad Institute
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

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceSource;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;

/**
 * Tests for the implementation of {@link SAMRecordIterator} in {@link SamReader}
 *
 * @author ktibbett@broadinstitute.org
 */
public class SamReaderSortTest extends HtsjdkTest {

    private static final String COORDINATE_SORTED_FILE = "src/test/resources/htsjdk/samtools/coordinate_sorted.sam";
    private static final String QUERYNAME_SORTED_FILE = "src/test/resources/htsjdk/samtools/queryname_sorted.sam";
    private static final String QUERYNAME_SORTED_NO_HEADER_SORT = "src/test/resources/htsjdk/samtools/unsorted.sam";
    private static final String CRAM_FILE = "src/test/resources/htsjdk/samtools/cram_query_sorted.cram";
    private static final String CRAM_REFERENCE = "src/test/resources/htsjdk/samtools/cram_query_sorted.fasta";
    private static final String CRAM_FILE_COORDINATE = "src/test/resources/htsjdk/samtools/cram/ce#tag_depadded.2.1.cram";
    private static final String CRAM_REFERENCE_COORDINATE = "src/test/resources/htsjdk/samtools/cram/ce.fa";
    private static final String CRAM_FILE_UNSORTED = "src/test/resources/htsjdk/samtools/cram/xx#unsorted.3.0.cram";
    private static final String CRAM_REFERENCE_UNSORTED = "src/test/resources/htsjdk/samtools/cram/xx.fa";

    @Test(expectedExceptions = IllegalStateException.class)
    public void testSortsDisagree() throws Exception {
        SAMRecordIterator it = SamReaderFactory.makeDefault().open(new File(COORDINATE_SORTED_FILE)).iterator();
        try {
            it.assertSorted(SAMFileHeader.SortOrder.queryname);
            while (it.hasNext()) {
                it.next();
            }
            Assert.fail("Queryname assertion should have failed on coordinate sorted file but didn't");
        } finally {
            it.close();
        }
    }

    @Test(dataProvider = "validSorts")
    public void testSortAssertionValid(String file, SAMFileHeader.SortOrder order) {
        SAMRecordIterator it = SamReaderFactory.makeDefault().open(new File(file)).iterator();
        try {
            it.assertSorted(order);
            while (it.hasNext()) {
                it.next();
            }
        } finally {
            it.close();
        }
    }

    @DataProvider(name = "validSorts")
    public Object[][] getValidSorts() {
        return new Object[][]{
                {COORDINATE_SORTED_FILE, SAMFileHeader.SortOrder.coordinate},
                {QUERYNAME_SORTED_FILE, SAMFileHeader.SortOrder.queryname},
                {QUERYNAME_SORTED_NO_HEADER_SORT, SAMFileHeader.SortOrder.queryname},
                {COORDINATE_SORTED_FILE, SAMFileHeader.SortOrder.unsorted}
        };
    }


    @Test(dataProvider = "invalidSorts", expectedExceptions = IllegalStateException.class)
    public void testSortAssertionFails(String file, SAMFileHeader.SortOrder order) throws Exception {
        SAMRecordIterator it = SamReaderFactory.makeDefault().open(new File(file)).iterator();
        try {
            it.assertSorted(order);
            while (it.hasNext()) {
                it.next();
            }
            Assert.fail("Iterated successfully over " + file + " with invalid sort assertion: " + order.name());
        } finally {
            it.close();
        }
    }

    private CRAMFileReader getCramFileReader(String file, String fileReference) {
        final ReferenceSource referenceSource = new ReferenceSource(new File(fileReference));
        return new CRAMFileReader(new File(file), referenceSource);
    }

    @Test(dataProvider = "sortsCramWithoutIndex")
    public void testCramSort(String file, String fileReference, SAMFileHeader.SortOrder order) throws Exception {
        final CRAMFileReader cramFileReader = getCramFileReader(file, fileReference);
        final SAMRecordIterator samRecordIterator = cramFileReader.getIterator().assertSorted(order);
        Assert.assertTrue(samRecordIterator.hasNext());
        while (samRecordIterator.hasNext()) {
            Assert.assertNotNull(samRecordIterator.next());
        }
    }

    @Test(dataProvider = "sortsFailCramWithoutIndex", expectedExceptions = IllegalStateException.class)
    public void testCramSortFail(String file, String fileReference, SAMFileHeader.SortOrder order) throws Exception {
        final CRAMFileReader cramFileReader = getCramFileReader(file, fileReference);
        final SAMRecordIterator samRecordIterator = cramFileReader.getIterator().assertSorted(order);
        Assert.assertTrue(samRecordIterator.hasNext());
        while (samRecordIterator.hasNext()) {
            Assert.assertNotNull(samRecordIterator.next());
        }
    }

    @DataProvider(name = "sortsFailCramWithoutIndex")
    public Object[][] getSortsFailCramWithoutIndex() {
        return new Object[][]{
                {CRAM_FILE, CRAM_REFERENCE, SAMFileHeader.SortOrder.coordinate},
                {CRAM_FILE_COORDINATE, CRAM_REFERENCE_COORDINATE, SAMFileHeader.SortOrder.queryname},
                {CRAM_FILE_UNSORTED, CRAM_REFERENCE_UNSORTED, SAMFileHeader.SortOrder.coordinate}
        };
    }

    @DataProvider(name = "sortsCramWithoutIndex")
    public Object[][] getSortsCramWithoutIndex() {
        return new Object[][]{
                {CRAM_FILE, CRAM_REFERENCE, SAMFileHeader.SortOrder.queryname},
                {CRAM_FILE_COORDINATE, CRAM_REFERENCE_COORDINATE, SAMFileHeader.SortOrder.coordinate},
                {CRAM_FILE_UNSORTED, CRAM_REFERENCE_UNSORTED, SAMFileHeader.SortOrder.unsorted}
        };
    }

    @DataProvider(name = "invalidSorts")
    public Object[][] getInvalidSorts() {
        return new Object[][]{
                {QUERYNAME_SORTED_NO_HEADER_SORT, SAMFileHeader.SortOrder.coordinate}
        };
    }
}
