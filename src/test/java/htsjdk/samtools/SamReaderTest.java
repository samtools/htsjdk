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

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.PeekableIterator;
import htsjdk.samtools.util.ProgressLogger;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SamReaderTest extends HtsjdkTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");

    @Test(dataProvider = "variousFormatReaderTestCases")
    public void variousFormatReaderTest(final String inputFile) throws IOException {
        final File input = new File(TEST_DATA_DIR, inputFile);
        try(final SamReader reader = SamReaderFactory.makeDefault().open(input)) {
            for (final SAMRecord rec : reader) {
                //just scan through the lines
            }
        }
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
    public void CRAMIndexTest(final String inputFile, final String referenceFile, QueryInterval queryInterval, String expectedReadName) throws IOException {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final File reference = new File(TEST_DATA_DIR, referenceFile);
        try(final SamReader reader = SamReaderFactory.makeDefault().referenceSequence(reference).open(input)) {
            Assert.assertTrue(reader.hasIndex());

            final CloseableIterator<SAMRecord> iterator = reader.query(new QueryInterval[]{queryInterval}, false);
            Assert.assertTrue(iterator.hasNext());
            SAMRecord r1 = iterator.next();
            Assert.assertEquals(r1.getReadName(), expectedReadName);

        }
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
    public void CRAMNoIndexTest(final String inputFile, final String referenceFile) throws IOException {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final File reference = new File(TEST_DATA_DIR, referenceFile);
        try(final SamReader reader = SamReaderFactory.makeDefault().referenceSequence(reference).open(input)) {
            Assert.assertFalse(reader.hasIndex());
        }
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

        @Override
        public SAMRecord createSAMRecord(final SAMFileHeader header) {
            ++samRecordsCreated;
            return super.createSAMRecord(header);
        }

        @Override
        public BAMRecord createBAMRecord(final SAMFileHeader header, final int referenceSequenceIndex, final int alignmentStart, final short readNameLength, final short mappingQuality, final int indexingBin, final int cigarLen, final int flags, final int readLen, final int mateReferenceSequenceIndex, final int mateAlignmentStart, final int insertSize, final byte[] variableLengthBlock) {
            ++bamRecordsCreated;
            return super.createBAMRecord(header, referenceSequenceIndex, alignmentStart, readNameLength, mappingQuality,
                                         indexingBin, cigarLen, flags, readLen, mateReferenceSequenceIndex,
                                         mateAlignmentStart, insertSize, variableLengthBlock);
        }
    }

    @Test(dataProvider = "variousFormatReaderTestCases")
    public void samRecordFactoryTest(final String inputFile) throws IOException {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final SAMRecordFactoryTester factory = new SAMRecordFactoryTester();
        int i = 0;
        try(final SamReader reader = SamReaderFactory.makeDefault().samRecordFactory(factory).open(input)){
            for (final SAMRecord rec : reader) {
                ++i;
            }
        }

        Assert.assertTrue(i > 0);
        if (inputFile.endsWith(".sam") || inputFile.endsWith(".sam.gz")) {
            Assert.assertEquals(factory.samRecordsCreated, i);
        } else if (inputFile.endsWith(".bam")) {
            Assert.assertEquals(factory.bamRecordsCreated, i);
        }
    }

    @Test(dataProvider = "cramTestCases", expectedExceptions = IllegalStateException.class)
    public void testReferenceRequiredForCRAM(final String inputFile, final String ignoredReferenceFile) throws IOException {
        final File input = new File(TEST_DATA_DIR, inputFile);
        try(final SamReader reader = SamReaderFactory.makeDefault().open(input)) {
            for (final SAMRecord rec : reader) {
            }
        }
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
    public void testIterateCRAMWithIndex(final String inputFile, final String referenceFile) throws IOException {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final File reference = new File(TEST_DATA_DIR, referenceFile);
        try(final SamReader reader = SamReaderFactory.makeDefault().referenceSequence(reference).open(input)) {
            for (final SAMRecord rec : reader) {
            }
        }
    }

    @Test
    public void samRecordFactoryNullHeaderTest() {
        final SAMRecordFactory factory = new DefaultSAMRecordFactory();
        final SAMRecord samRec = factory.createSAMRecord(null);
        Assert.assertTrue(samRec.getHeader() == null);
    }

    @DataProvider(name = "hasValidFileExtensionTestData")
    public Object[][]testHasValidFileExtensionTestData() {
        final Set<SamReader.Type> setOfKnownFileTypes = new HashSet<>();
        setOfKnownFileTypes.add(SamReader.Type.BAM_TYPE);
        setOfKnownFileTypes.add(SamReader.Type.SAM_TYPE);
        setOfKnownFileTypes.add(SamReader.Type.SRA_TYPE);
        setOfKnownFileTypes.add(SamReader.Type.CRAM_TYPE);

        final List<Object[]> list = new ArrayList<>();
        for (final SamReader.Type fileType : setOfKnownFileTypes) {
            // positive expectations:
            list.add(new Object[]{fileType, "test." + fileType.fileExtension(), true});
            list.add(new Object[]{fileType, "." + fileType.fileExtension(), true});
            list.add(new Object[]{fileType, "/path/to/test." + fileType.fileExtension(), true});
            list.add(new Object[]{fileType, "/path/to/." + fileType.fileExtension(), true});
            list.add(new Object[]{fileType, "\\path\\to\\." + fileType.fileExtension(), true});
            list.add(new Object[]{fileType, "../." + fileType.fileExtension(), true});
            list.add(new Object[]{fileType, "../test." + fileType.fileExtension(), true});
            list.add(new Object[]{fileType, "./test." + fileType.fileExtension(), true});
            list.add(new Object[]{fileType, "./." + fileType.fileExtension(), true});

            // negative expectations:
            list.add(new Object[]{fileType, null, false});
            list.add(new Object[]{fileType, fileType.fileExtension(), false});
            list.add(new Object[]{fileType, "test" + fileType.fileExtension(), false});
            list.add(new Object[]{fileType, "test." + fileType.fileExtension() + ".", false});
            list.add(new Object[]{fileType, "test" + fileType.fileExtension().toUpperCase(), false});
            list.add(new Object[]{fileType, "test." + fileType.fileExtension() + ".png", false});
            list.add(new Object[]{fileType, "/dev/null", false});

            for (final SamReader.Type anotherFileType : setOfKnownFileTypes) {
                if (anotherFileType != fileType) {
                    list.add(new Object[]{fileType, "test." + anotherFileType.fileExtension(), false});
                }
            }
        }

        return list.toArray(new Object[list.size()][]);
    }

    @Test(dataProvider = "hasValidFileExtensionTestData")
    public void testHasValidFileExtension(final SamReader.Type type, final String fileName, final boolean expectValidFileExtension) {
        Assert.assertEquals(type.hasValidFileExtension(fileName), expectValidFileExtension);
    }
    @Test
    public void testAssertingIteratorUsesLenientOrdering() {
        // The coordinate comparator's strict sort sorts lower mapping qualities first,
        // so this list is not sorted with respect to that comparator, but it is sorted with respect to the more lenient
        // file order comparator which only checks the position.
        final List<SAMRecord> looselySorted = Arrays.asList(createRecord(1, 10),
                                                            createRecord(1, 1),
                                                            createRecord(2, 1));

        final SAMRecordCoordinateComparator coordinateComparator = new SAMRecordCoordinateComparator();

        //sanity check that this really sorts differently with the file order comparator vs the full ordering coordinate order comparator
        final List<SAMRecord> sortedWithFileOrderComparator = looselySorted.stream()
                .sorted(coordinateComparator::fileOrderCompare)
                .collect(Collectors.toList());
        Assert.assertEquals(sortedWithFileOrderComparator, looselySorted);

        final List<SAMRecord> sortedWithFullOrderComparator = looselySorted.stream()
                .sorted(coordinateComparator)
                .collect(Collectors.toList());

        Assert.assertNotEquals(sortedWithFullOrderComparator, looselySorted);

        final SamReader.AssertingIterator iter = new SamReader.AssertingIterator(new PeekableIterator<>(looselySorted.iterator()));
        iter.assertSorted(SAMFileHeader.SortOrder.coordinate);
        int count = 0;

        while (iter.hasNext()) {
            iter.next();
            count++;
        }
        Assert.assertEquals(count, 3);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testAssertingIteratorCorrectlyFailsWhenOutOfOrder() {
        final List<SAMRecord> unsorted = Arrays.asList(createRecord(10, 1), createRecord(1, 1));
        final SamReader.AssertingIterator iter = new SamReader.AssertingIterator(new PeekableIterator<>(unsorted.iterator()));
        iter.assertSorted(SAMFileHeader.SortOrder.coordinate);

        while (iter.hasNext()) {
            iter.next();
        }
    }

    @DataProvider(name = "bamTestCases")
    public Object[][] bamTestPositiveCases() {
        final Object[][] scenarios = new Object[][]{
                {"compressed.bam"},
                {"NA12878_garvan_head.bam"},
                {"empty_no_empty_gzip_block.bam"},
        };
        return scenarios;
    }

    @Test(dataProvider = "bamTestCases")
    public void perftestBamAsyncIterator(String inputBam) throws IOException {
        final File input = new File(TEST_DATA_DIR, inputBam);
        try(final SamReader asyncReader = SamReaderFactory.makeDefault()
                .setUseAsyncIo(false)
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .enable(SamReaderFactory.Option.EAGERLY_DECODE)
                .open(input)) {
            ProgressLogger logger = new ProgressLogger(Log.getInstance(SamReaderTest.class), 10000000);
            for (SAMRecord r : asyncReader) {
                // performance testing
                logger.record(r);
            }
        }
    }
    @Test(dataProvider = "bamTestCases")
    public void testBamAsyncIterator(String inputBam) throws IOException {
        final File input = new File(TEST_DATA_DIR, inputBam);
        try(final SamReader reader = SamReaderFactory.makeDefault()
                .setUseAsyncIo(false)
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .enable(SamReaderFactory.Option.EAGERLY_DECODE)
                .open(input)) {
            try(final SamReader asyncReader = SamReaderFactory.makeDefault()
                    .setUseAsyncIo(true)
                    .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                    .enable(SamReaderFactory.Option.EAGERLY_DECODE)
                    .open(input)) {
                SAMRecordIterator it = reader.iterator();
                SAMRecordIterator asyncIt = asyncReader.iterator();
                while (it.hasNext()) {
                    Assert.assertTrue(asyncIt.hasNext());
                    // check the records match
                    SAMRecord record = it.next();
                    SAMRecord asyncRecord = asyncIt.next();
                    Assert.assertEquals(record, asyncRecord);
                    // check the BAM file metadata matches
                    BAMFileSpan recordSpan = (BAMFileSpan)record.getFileSource().getFilePointer();
                    BAMFileSpan asyncSpan = (BAMFileSpan)asyncRecord.getFileSource().getFilePointer();
                    Assert.assertEquals(recordSpan.getFirstOffset(), asyncSpan.getFirstOffset());
                }
                Assert.assertEquals(it.hasNext(), asyncIt.hasNext());
            }
        }
    }

    private static SAMRecord createRecord(int start, int mappingQuality) {
        final SAMRecord rec = new SAMRecord(getHeader());
        rec.setReadName("read");
        rec.setReferenceName("1");
        rec.setAlignmentStart(start);
        rec.setMappingQuality(mappingQuality);
        return rec;
    }

    private static SAMFileHeader getHeader() {
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("1", 1000));
        return header;
    }
}
