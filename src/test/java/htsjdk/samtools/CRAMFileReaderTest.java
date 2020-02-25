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
package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.Log;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.*;
import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Additional tests for CRAMFileReader are in CRAMFileIndexTest
 */
public class CRAMFileReaderTest extends HtsjdkTest {

    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");
    private static final File CRAM_WITH_CRAI = new File(TEST_DATA_DIR, "cram_with_crai_index.cram");
    private static final File CRAM_WITHOUT_CRAI = new File(TEST_DATA_DIR, "cram_query_sorted.cram");
    private static final ReferenceSource REFERENCE = createReferenceSource();
    private static final File INDEX_FILE = new File(TEST_DATA_DIR, "cram_with_crai_index.cram.crai");

    private static ReferenceSource createReferenceSource() {
        final byte[] refBases = new byte[10 * 10];
        Arrays.fill(refBases, (byte) 'A');
        InMemoryReferenceSequenceFile rsf = new InMemoryReferenceSequenceFile();
        rsf.add("chr1", refBases);
        return new ReferenceSource(rsf);
    }

    // constructor 1: CRAMFileReader(final File cramFile, final InputStream inputStream)

    @Test(description = "Test CRAMReader 1 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader1_ReferenceRequired() {
        final InputStream bis = null;
        // assumes that reference_fasta property is not set and the download service is not enabled
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, bis);
        reader.getIterator().hasNext();
    }

    // constructor 2: CRAMFileReader(final File cramFile, final InputStream inputStream, final ReferenceSource referenceSource)

    @Test(description = "Test CRAMReader 2 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader2ReferenceRequired() {
        final InputStream bis =  null;
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, bis, null);
        reader.getIterator().hasNext();
    }

    @Test(description = "Test CRAMReader 2 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader2_InputRequired() {
        final File file = null;
        final InputStream bis = null;
        final CRAMFileReader reader = new CRAMFileReader(file, bis, createReferenceSource());
        reader.getIterator().hasNext();
    }

    @Test
    public void testCRAMReader2_ShouldAutomaticallyFindCRAMIndex() {
        final InputStream inputStream = null;
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, inputStream, REFERENCE);
        reader.getIndex();
        Assert.assertTrue(reader.hasIndex(), "Can't find CRAM existing index.");
    }

    @Test(expectedExceptions = SAMException.class)
    public void testCRAMReader2_WithoutCRAMIndex() {
        final InputStream inputStream = null;
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITHOUT_CRAI, inputStream, REFERENCE);
        reader.getIndex();
    }

    // constructor 3: CRAMFileReader(final File cramFile, final File indexFile, final ReferenceSource referenceSource)

    @Test(description = "Test CRAMReader 3 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader3_RequiredReference() {
        final File indexFile = null;
        final ReferenceSource refSource = null;
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, indexFile, refSource);
        reader.getIterator().hasNext();
    }

    @Test(description = "Test CRAMReader 3 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader3_InputRequired() {
        final File inputFile = null;
        final File indexFile = null;
        ReferenceSource refSource = null;
        new CRAMFileReader(inputFile, indexFile, refSource);
    }

    @Test
    public void testCRAMReader3_ShouldAutomaticallyFindCRAMIndex() {
        final File indexFile = null;
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, indexFile, REFERENCE);
        reader.getIndex();
        Assert.assertTrue(reader.hasIndex(), "Can't find CRAM index.");
    }

    @Test
    public void testCRAMReader3_ShouldUseCRAMIndex() {
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, INDEX_FILE, REFERENCE);
        reader.getIndex();
        Assert.assertTrue(reader.hasIndex(), "Can't find CRAM index.");
    }

    @Test(expectedExceptions = SAMException.class)
    public void testCRAMReader3_WithoutCRAMIndex() {
        final File indexFile = null;
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITHOUT_CRAI, indexFile, REFERENCE);
        reader.getIndex();
    }

    // constructor 4: CRAMFileReader(final File cramFile, final ReferenceSource referenceSource)

    @Test(description = "Test CRAMReader 4 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader4_ReferenceRequired() {
        final ReferenceSource refSource = null;
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, refSource);
        reader.getIterator().hasNext();
    }

    @Test(description = "Test CRAMReader 4 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader4_InputRequired() {
        final File inputFile = null;
        new CRAMFileReader(inputFile, createReferenceSource());
    }

    @Test
    public void testCRAMReader4_ShouldAutomaticallyFindCRAMIndex() {
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, REFERENCE);
        reader.getIndex();
        Assert.assertTrue(reader.hasIndex(), "Can't find existing CRAM index.");
    }

    @Test(expectedExceptions = SAMException.class)
    public void testCRAMReader4_WithoutCRAMIndex() {
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITHOUT_CRAI, REFERENCE);
        reader.getIndex();
    }

    // constructor 5: CRAMFileReader(final InputStream inputStream, final SeekableStream indexInputStream,
    //          final ReferenceSource referenceSource, final ValidationStringency validationStringency)
    @Test(description = "Test CRAMReader 5 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader5_ReferenceRequired() throws IOException {
        try (final FileInputStream fis = new FileInputStream(CRAM_WITH_CRAI)) {
            final SeekableFileStream sfs = null;
            final ReferenceSource refSource = null;
            final CRAMFileReader reader = new CRAMFileReader(fis, sfs, refSource, ValidationStringency.STRICT);
            reader.getIterator().hasNext();
        }
    }

    @Test(description = "Test CRAMReader 5 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader5_InputRequired() throws IOException {
        final InputStream bis = null;
        final SeekableFileStream sfs = null;
        new CRAMFileReader(bis, sfs, createReferenceSource(), ValidationStringency.STRICT);
    }

    // constructor 6: CRAMFileReader(final InputStream stream, final File indexFile, final ReferenceSource referenceSource,
    //                final ValidationStringency validationStringency)
    @Test(description = "Test CRAMReader 6 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader6_ReferenceRequired() throws IOException {
        try (final FileInputStream fis = new FileInputStream(CRAM_WITH_CRAI)) {
            final File file = null;
            final ReferenceSource refSource = null;
            final CRAMFileReader reader = new CRAMFileReader(fis, file, refSource, ValidationStringency.STRICT);
            reader.getIterator().hasNext();
        }
    }

    @Test(description = "Test CRAMReader 6 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader6_InputRequired() throws IOException {
        InputStream bis = null;
        File file = null;
        new CRAMFileReader(bis, file, createReferenceSource(), ValidationStringency.STRICT);
    }

    // constructor 7: CRAMFileReader(final File cramFile, final File indexFile, final ReferenceSource referenceSource,
    //                final ValidationStringency validationStringency)
    @Test(description = "Test CRAMReader 7 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader7_ReferenceRequired() throws IOException {
        ReferenceSource refSource = null;
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, CRAM_WITH_CRAI, refSource, ValidationStringency.STRICT);
        reader.getIterator().hasNext();
    }

    @Test
    public void testCRAMReader7_ShouldAutomaticallyFindCRAMIndex()throws IOException {
        File indexFile = null;
        CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, indexFile, REFERENCE, ValidationStringency.STRICT);
        Assert.assertTrue(reader.hasIndex(), "Can't find existing CRAM index.");
    }

    @Test
    public void testCRAMReader7_ShouldUseCRAMIndex() throws IOException {
        CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, INDEX_FILE, REFERENCE, ValidationStringency.STRICT);
        Assert.assertTrue(reader.hasIndex(), "Can't find existing CRAM index.");
    }

    @Test(expectedExceptions = SAMException.class)
    public void testCRAMReader7_WithoutCRAMIndex() throws IOException {
        File indexFile = null;
        CRAMFileReader reader = new CRAMFileReader(CRAM_WITHOUT_CRAI, indexFile, REFERENCE, ValidationStringency.STRICT);
        reader.getIndex();
    }

    @Test
    public void testCramIteratorWithoutCallingHasNextFirst() throws IOException {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted);
        builder.addFrag("1", 0, 2, false);
        final CRAMFileReader reader = CRAMTestUtils.writeAndReadFromInMemoryCram(builder);
        final SAMRecordIterator iterator = reader.getIterator();
        Assert.assertNotNull(iterator.next());
        Assert.assertThrows(NoSuchElementException.class, iterator::next);
    }
}
