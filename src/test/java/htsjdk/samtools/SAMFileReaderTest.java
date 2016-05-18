/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
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

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;

public class SAMFileReaderTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");

    @Test(dataProvider = "variousFormatReaderTestCases")
    public void variousFormatReaderTest(final String inputFile) {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final SamReader reader = SamReaderFactory.makeDefault().open(input);
        for (final SAMRecord rec : reader) {
        }
        CloserUtil.close(reader);
    }

    @DataProvider(name = "variousFormatReaderTestCases")
    public Object[][] variousFormatReaderTestCases() {
        final Object[][] scenarios = new Object[][]{
                {"block_compressed.sam.gz"},
                {"uncompressed.sam"},
                {"compressed.sam.gz"},
                {"compressed.bam"},
        };
        return scenarios;
    }

    // tests for CRAM indexing

    @Test(dataProvider = "SmallCRAMTest")
    public void CRAMIndexTest(final String inputFile, final String referenceFile, QueryInterval queryInterval, String expectedReadName) {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final File reference = new File(TEST_DATA_DIR, referenceFile);
        final SamReader reader = SamReaderFactory.makeDefault().referenceSequence(reference).open(input);
        Assert.assertTrue(reader.hasIndex());

        final CloseableIterator<SAMRecord> iterator = reader.query(new QueryInterval[]{queryInterval}, false);
        Assert.assertTrue(iterator.hasNext());
        SAMRecord r1 = iterator.next();
        Assert.assertEquals(r1.getReadName(), expectedReadName);

        CloserUtil.close(reader);
    }

    @DataProvider(name = "SmallCRAMTest")
    public Object[][] CRAMIndexTestData() {
        final Object[][] testFiles = new Object[][]{
                {"cram/test.cram", "cram/auxf.fa", new QueryInterval(0, 12, 13), "Jim"},
                {"cram_with_bai_index.cram", "hg19mini.fasta", new QueryInterval(3, 700, 0), "k"},
                {"cram_with_crai_index.cram", "hg19mini.fasta", new QueryInterval(2, 350, 0), "i"},
        };
        return testFiles;
    }

    @Test(dataProvider = "NoIndexCRAMTest")
    public void CRAMNoIndexTest(final String inputFile, final String referenceFile) {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final File reference = new File(TEST_DATA_DIR, referenceFile);
        final SamReader reader = SamReaderFactory.makeDefault().referenceSequence(reference).open(input);
        Assert.assertFalse(reader.hasIndex());
        CloserUtil.close(reader);
    }

    @DataProvider(name = "NoIndexCRAMTest")
    public Object[][] CRAMNoIndexTestData() {
        final Object[][] testFiles = new Object[][]{
                {"cram/test2.cram", "cram/auxf.fa"},
        };
        return testFiles;
    }

    // Tests for the SAMRecordFactory usage
    class SAMRecordFactoryTester extends DefaultSAMRecordFactory {
        int samRecordsCreated;
        int bamRecordsCreated;

        public SAMRecord createSAMRecord(final SAMFileHeader header) {
            ++samRecordsCreated;
            return super.createSAMRecord(header);
        }

        public BAMRecord createBAMRecord(final SAMFileHeader header, final int referenceSequenceIndex, final int alignmentStart, final short readNameLength, final short mappingQuality, final int indexingBin, final int cigarLen, final int flags, final int readLen, final int mateReferenceSequenceIndex, final int mateAlignmentStart, final int insertSize, final byte[] variableLengthBlock) {
            ++bamRecordsCreated;
            return super.createBAMRecord(header, referenceSequenceIndex, alignmentStart, readNameLength, mappingQuality, indexingBin, cigarLen, flags, readLen, mateReferenceSequenceIndex, mateAlignmentStart, insertSize, variableLengthBlock);
        }
    }

    @Test(dataProvider = "variousFormatReaderTestCases")
    public void samRecordFactoryTest(final String inputFile) {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final SAMRecordFactoryTester factory = new SAMRecordFactoryTester();
        final SamReader reader = SamReaderFactory.makeDefault().samRecordFactory(factory).open(input);

        int i = 0;
        for (final SAMRecord rec : reader) {
            ++i;
        }
        CloserUtil.close(reader);

        Assert.assertTrue(i > 0);
        if (inputFile.endsWith(".sam") || inputFile.endsWith(".sam.gz")) Assert.assertEquals(factory.samRecordsCreated, i);
        else if (inputFile.endsWith(".bam")) Assert.assertEquals(factory.bamRecordsCreated, i);
    }

    @Test(dataProvider = "cramTestCases", expectedExceptions=IllegalStateException.class)
    public void testReferenceRequiredForCRAM(final String inputFile, final String ignoredReferenceFile) {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final SamReader reader = SamReaderFactory.makeDefault().open(input);
        for (final SAMRecord rec : reader) {
        }
        CloserUtil.close(reader);
    }

    @DataProvider(name = "cramTestCases")
    public Object[][] cramTestPositiveCases() {
        final Object[][] scenarios = new Object[][]{
                {"cram_with_bai_index.cram", "hg19mini.fasta"},
                {"cram_with_crai_index.cram", "hg19mini.fasta"},
        };
        return scenarios;
    }

    @Test(dataProvider = "cramTestCases")
    public void testIterateCRAMWithIndex(final String inputFile, final String referenceFile) {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final File reference = new File(TEST_DATA_DIR, referenceFile);
        final SamReader reader = SamReaderFactory.makeDefault().referenceSequence(reference).open(input);
        for (final SAMRecord rec : reader) {
        }
        CloserUtil.close(reader);
    }

    @Test
    public void samRecordFactoryNullHeaderTest() {
        final SAMRecordFactory factory = new DefaultSAMRecordFactory();
        final SAMRecord samRec = factory.createSAMRecord(null);
        Assert.assertTrue(samRec.getHeader() == null);
    }

}
