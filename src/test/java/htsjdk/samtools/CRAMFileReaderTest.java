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

import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.Log;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Additional tests for CRAMFileReader are in CRAMFileIndexTest
 */
public class CRAMFileReaderTest {

    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");

    @BeforeClass
    public void initClass() {
        Log.setGlobalLogLevel(Log.LogLevel.ERROR);
    }

    private ReferenceSource createReferenceSource() {
        byte[] refBases = new byte[10 * 10];
        Arrays.fill(refBases, (byte) 'A');
        InMemoryReferenceSequenceFile rsf = new InMemoryReferenceSequenceFile();
        rsf.add("chr1", refBases);
        return new ReferenceSource(rsf);
    }

    // constructor 1: CRAMFileReader(final File cramFile, final InputStream inputStream)

    @Test(description = "Test CRAMReader 1 reference required", expectedExceptions = IllegalStateException.class)
    public void testCRAMReader1_ReferenceRequired() {
        File file = new File(TEST_DATA_DIR, "cram_with_crai_index.cram");
        InputStream bis = null;
        // assumes that reference_fasta property is not set and the download service is not enabled
        new CRAMFileReader(file, bis);
    }

    // constructor 2: CRAMFileReader(final File cramFile, final InputStream inputStream, final ReferenceSource referenceSource)

    @Test(description = "Test CRAMReader 2 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader2ReferenceRequired() {
        File file = new File(TEST_DATA_DIR, "cram_with_crai_index.cram");
        InputStream bis =  null;
        new CRAMFileReader(file, bis, null);
    }

    @Test(description = "Test CRAMReader 2 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader2_InputRequired() {
        File file = null;
        InputStream bis =  null;
        new CRAMFileReader(file, bis, createReferenceSource());
    }

    // constructor 3: CRAMFileReader(final File cramFile, final File indexFile, final ReferenceSource referenceSource)

    @Test(description = "Test CRAMReader 3 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader3_RequiredReference() {
        File inputFile = new File(TEST_DATA_DIR, "cram_with_crai_index.cram");
        File indexFile = null;
        ReferenceSource refSource = null;
        new CRAMFileReader(inputFile, indexFile, refSource);
    }

    @Test(description = "Test CRAMReader 3 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader3_InputRequirted() {
        File inputFile = null;
        File indexFile = null;
        ReferenceSource refSource = null;
        new CRAMFileReader(inputFile, indexFile, refSource);
    }

    // constructor 4: CRAMFileReader(final File cramFile, final ReferenceSource referenceSource)

    @Test(description = "Test CRAMReader 4 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader4_ReferenceRequired() {
        File inputFile = new File(TEST_DATA_DIR, "cram_with_crai_index.cram");
        ReferenceSource refSource = null;
        new CRAMFileReader(inputFile, refSource);
    }

    @Test(description = "Test CRAMReader 4 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader4_InputRequired() {
        File inputFile = null;
        new CRAMFileReader(inputFile, createReferenceSource());
    }

    // constructor 5: CRAMFileReader(final InputStream inputStream, final SeekableStream indexInputStream,
    //          final ReferenceSource referenceSource, final ValidationStringency validationStringency)
    @Test(description = "Test CRAMReader 5 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader5_ReferenceRequired() throws IOException {
        InputStream bis = new ByteArrayInputStream(new byte[0]);
        SeekableFileStream sfs = null;
        ReferenceSource refSource = null;
        new CRAMFileReader(bis, sfs, refSource, ValidationStringency.STRICT);
    }

    @Test(description = "Test CRAMReader 5 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader5_InputRequired() throws IOException {
        InputStream bis = null;
        SeekableFileStream sfs = null;
        new CRAMFileReader(bis, sfs, createReferenceSource(), ValidationStringency.STRICT);
    }

    // constructor 6: CRAMFileReader(final InputStream stream, final File indexFile, final ReferenceSource referenceSource,
    //                final ValidationStringency validationStringency)
    @Test(description = "Test CRAMReader 6 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader6_ReferenceRequired() throws IOException {
        InputStream bis = new ByteArrayInputStream(new byte[0]);
        File file = null;
        ReferenceSource refSource = null;
        new CRAMFileReader(bis, file, refSource, ValidationStringency.STRICT);
    }

    @Test(description = "Test CRAMReader 6 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader6_InputRequired() throws IOException {
        InputStream bis = null;
        File file = null;
        ReferenceSource refSource = null;
        new CRAMFileReader(bis, file, createReferenceSource(), ValidationStringency.STRICT);
    }

    // constructor 7: CRAMFileReader(final File cramFile, final File indexFile, final ReferenceSource referenceSource,
    //                final ValidationStringency validationStringency)
    @Test(description = "Test CRAMReader 7 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader7_ReferenceRequired() throws IOException {
        InputStream bis = new ByteArrayInputStream(new byte[0]);
        File file = new File(TEST_DATA_DIR, "cram_with_crai_index.cram");
        ReferenceSource refSource = null;
        new CRAMFileReader(file, file, refSource, ValidationStringency.STRICT);
    }

}
