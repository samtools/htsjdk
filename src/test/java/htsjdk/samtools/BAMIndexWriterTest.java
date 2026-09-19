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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import htsjdk.HtsjdkTest;
import htsjdk.index.BinningIndex;
import htsjdk.index.FileBackedBinningIndex;
import htsjdk.index.ReferenceBins;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.IOUtil;
import htsjdk.utils.SamtoolsTestUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test BAM file index creation
 */
public class BAMIndexWriterTest extends HtsjdkTest {
    // Two input files for basic test
    private final String BAM_FILE_LOCATION = "src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam";
    private final String BAI_FILE_LOCATION = "src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam.bai";
    private final Path BAM_FILE = Paths.get(BAM_FILE_LOCATION);

    private final boolean mVerbose = true;

    /** A BAI of {@link #BAM_FILE} built by htsjdk, and one built by samtools; skips the test without samtools. */
    private Path[] baiFromHtsjdkAndFromSamtools() throws IOException {
        if (!SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("samtools is not available");
        }
        final Path ours = Files.createTempFile("htsjdk.", ".bai");
        final Path theirs = Files.createTempFile("samtools.", ".bai");
        IOUtil.deleteOnExit(ours);
        IOUtil.deleteOnExit(theirs);
        try (SamReader bam = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(BAM_FILE)) {
            BAMIndexer.createIndex(bam, ours);
        }
        SamtoolsTestUtils.executeSamToolsCommand("index -b -o " + theirs + " " + BAM_FILE);
        return new Path[] {ours, theirs};
    }

    /** samtools writes a reference's bins in no particular order, so the indexes are compared as read, not as bytes. */
    @Test
    public void testBaiHasTheContentOfTheOneSamtoolsBuilds() throws IOException {
        final Path[] bais = baiFromHtsjdkAndFromSamtools();
        try (FileBackedBinningIndex ours = FileBackedBinningIndex.open(bais[0], true);
                FileBackedBinningIndex theirs = FileBackedBinningIndex.open(bais[1], true)) {
            assertEquals(ours.loadAll(), theirs.loadAll());
        }
    }

    @Test
    public void testBaiAndTheOneSamtoolsBuildsAreTheSameWrittenAsText() throws IOException {
        final Path[] bais = baiFromHtsjdkAndFromSamtools();
        final Path ourText = Files.createTempFile("htsjdk.", ".bai.txt");
        final Path theirText = Files.createTempFile("samtools.", ".bai.txt");
        IOUtil.deleteOnExit(ourText);
        IOUtil.deleteOnExit(theirText);
        BAMIndexer.createAndWriteIndex(bais[0], ourText, true);
        BAMIndexer.createAndWriteIndex(bais[1], theirText, true);
        IOUtil.assertFilesEqual(ourText, theirText);
    }

    @Test
    public void testBaiAndTheOneSamtoolsBuildsAreTheSameBytesOnceRewritten() throws IOException {
        final Path[] bais = baiFromHtsjdkAndFromSamtools();
        final Path theirsRewritten = Files.createTempFile("samtools.rewritten.", ".bai");
        IOUtil.deleteOnExit(theirsRewritten);
        BAMIndexer.createAndWriteIndex(bais[1], theirsRewritten, false);
        IOUtil.assertFilesEqual(bais[0], theirsRewritten);
    }

    @Test(enabled = false, dataProvider = "linearIndexTestData")
    /** Test linear index at specific references and windows */
    public void testLinearIndex(
            String testName,
            String filepath,
            int problemReference,
            int problemWindowStart,
            int problemWindowEnd,
            int expectedCount) {
        final SamReader sfr = SamReaderFactory.makeDefault().open(Paths.get(filepath));
        for (int problemWindow = problemWindowStart; problemWindow <= problemWindowEnd; problemWindow++) {
            int count = countAlignmentsInWindow(problemReference, problemWindow, sfr, expectedCount);
            if (expectedCount != -1) assertEquals(expectedCount, count);
        }
        CloserUtil.close(sfr);
    }

    @DataProvider(name = "linearIndexTestData")
    public Object[][] getLinearIndexTestData() {
        // Add data here for test cases, reference, and windows where linear index needs testing
        return new Object[][] {
            new Object[] {"index_test", BAM_FILE_LOCATION, 1, 29, 66, -1}, // 29-66
            new Object[] {"index_test", BAM_FILE_LOCATION, 1, 68, 118, -1}, // 29-66
        };
    }

    private int countAlignmentsInWindow(int reference, int window, SamReader reader, int expectedCount) {
        final int SIXTEEN_K = 1 << 14; // 1 << LinearIndex.BAM_LIDX_SHIFT
        final int start = window >> 14; // window * SIXTEEN_K;
        final int stop = ((window + 1) >> 14) - 1; // (window + 1 * SIXTEEN_K) - 1;

        final String chr = reader.getFileHeader().getSequence(reference).getSequenceName();

        // get records for the entire linear index window
        SAMRecordIterator iter = reader.queryOverlapping(chr, start, stop);
        SAMRecord rec;
        int count = 0;
        while (iter.hasNext()) {
            rec = iter.next();
            count++;
            if (expectedCount == -1) System.err.println(rec.getReadName());
        }
        iter.close();
        return count;
    }

    @Test(enabled = false, dataProvider = "indexComparisonData")
    /** Test linear index at all references and windows, comparing with existing index */
    public void compareLinearIndex(String testName, String bamFile, String bamIndexFile) throws IOException {
        // compare index generated from bamFile with existing bamIndex file
        // by testing all the references' windows and comparing the counts

        // 1. generate bai file
        // 2. count its references
        // 3. count bamIndex references comparing counts

        // 1. generate bai file
        Path bam = Paths.get(bamFile);
        assertTrue(Files.exists(bam), testName + " input bam file doesn't exist: " + bamFile);

        Path indexFile1 = createIndexFile(bam);
        assertTrue(Files.exists(indexFile1), testName + " generated bam file's index doesn't exist: " + indexFile1);

        // 2. count its references
        Path indexFile2 = Paths.get(bamIndexFile);
        assertTrue(Files.exists(indexFile2), testName + " input index file doesn't exist: " + indexFile2);

        final BinningIndex existingIndex1 = loadIndex(indexFile1);
        final BinningIndex existingIndex2 = loadIndex(indexFile2);
        final int n_ref = existingIndex1.getReferenceCount();
        assertEquals(n_ref, existingIndex2.getReferenceCount());

        final SamReader reader1 = SamReaderFactory.makeDefault()
                .disable(SamReaderFactory.Option.EAGERLY_DECODE)
                .open(bam);

        final SamReader reader2 = SamReaderFactory.makeDefault()
                .disable(SamReaderFactory.Option.EAGERLY_DECODE)
                .open(bam);

        System.out.println("Comparing " + n_ref + " references in " + indexFile1 + " and " + indexFile2);

        for (int i = 0; i < n_ref; i++) {
            final ReferenceBins content1 = existingIndex1.getReference(i);
            final ReferenceBins content2 = existingIndex2.getReference(i);
            if (content1.getBinCount() == 0) {
                assertEquals(
                        content2.getBinCount(),
                        0,
                        "No content for 1st bam index, but content for second at reference" + i);
                continue;
            }
            int[] counts1 = new int[LinearIndex.MAX_LINEAR_INDEX_SIZE];
            int[] counts2 = new int[LinearIndex.MAX_LINEAR_INDEX_SIZE];
            // todo not li1 and li2 sizes may differ. Implies 0's in the smaller index windows
            // 3. count bamIndex references comparing counts
            int baiSize = Math.max(content1.getLinearIndex().length, content2.getLinearIndex().length);
            for (int win = 0; win < baiSize; win++) {
                counts1[win] = countAlignmentsInWindow(i, win, reader1, 0);
                counts2[win] = countAlignmentsInWindow(i, win, reader2, counts1[win]);
                assertEquals(counts2[win], counts1[win], "Counts don't match for reference " + i + " window " + win);
            }
        }

        indexFile1.toFile().deleteOnExit();
    }

    @DataProvider(name = "indexComparisonData")
    public Object[][] getIndexComparisonData() {
        // enter bam file and alternate index file to be tested against generated bam index
        return new Object[][] {
            new Object[] {"index_test", BAM_FILE_LOCATION, BAI_FILE_LOCATION},
        };
    }

    @Test(expectedExceptions = SAMException.class)
    public void testRequireCoordinateSortOrder() {
        SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.queryname);

        new BAMIndexer(new ByteArrayOutputStream(), header);
    }

    private static BinningIndex loadIndex(final Path indexFile) {
        try (FileBackedBinningIndex index = FileBackedBinningIndex.open(indexFile, true)) {
            return index.loadAll();
        }
    }

    /** generates the index file using the latest java index generating code */
    private Path createIndexFile(Path bamPath) throws IOException {
        final Path bamIndexFile = Files.createTempFile("Bai.", ".bai");
        final SamReader bam = SamReaderFactory.makeDefault().open(bamPath);
        BAMIndexer.createIndex(bam, bamIndexFile);
        verbose("Wrote BAM Index file " + bamIndexFile);
        bam.close();
        return bamIndexFile;
    }

    private void verbose(final String text) {
        if (mVerbose) {
            System.out.println("#BAMIndexWriterTest " + text);
        }
    }

    /**
     * samtools files a placed but unmapped read under the 16 kb window its position falls in, including when that
     * position is the first of a window.
     */
    @Test
    public void testPlacedUnmappedReadOnAWindowBoundaryIsFiledUnderItsOwnWindow() throws Exception {
        final int firstBaseOfSecondWindow = 16_385;
        final SAMRecordSetBuilder records =
                new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate, true, 1_000_000);
        records.addFrag("before", 0, 100, false);
        records.addFrag("placedUnmapped", 0, firstBaseOfSecondWindow, false, true, null, null, -1);
        records.addFrag("after", 0, 30_000, false);
        final Path bam = Files.createTempFile("windowBoundary.", ".bam");
        bam.toFile().deleteOnExit();
        try (SAMFileWriter writer = new SAMFileWriterFactory()
                .setCreateIndex(false)
                .setCreateMd5File(false)
                .makeBAMWriter(records.getHeader(), true, bam)) {
            records.getRecords().forEach(writer::addAlignment);
        }
        final Path bai = Files.createTempFile("windowBoundary.", ".bai");
        bai.toFile().deleteOnExit();
        long offsetOfPlacedUnmapped = -1;
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(bam)) {
            final BAMIndexer indexer = new BAMIndexer(bai, reader.getFileHeader());
            for (final SAMRecord record : reader) {
                if (record.getReadName().equals("placedUnmapped")) {
                    offsetOfPlacedUnmapped = ((BAMFileSpan)
                                    record.getFileSource().getFilePointer())
                            .getSingleChunk()
                            .getChunkStart();
                }
                indexer.processAlignment(record);
            }
            indexer.finish();
        }

        try (InputStream in = Files.newInputStream(bai)) {
            final BinaryCodec codec = new BinaryCodec(in);
            codec.readBytes(new byte[4]);
            final BinningIndex index = BinningIndex.readBaiLayout(
                    codec, codec.readInt(), BinningIndex.BAI_MIN_SHIFT, BinningIndex.BAI_DEPTH);
            assertEquals(index.getReference(0).getLinearIndex()[1], offsetOfPlacedUnmapped);
        }
    }

    @Test
    public void testFinishWithEndMovesTheLastChunksEndWhenLastRecordIsPlaced() throws IOException {
        final SAMRecordSetBuilder records =
                new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate, true, 1_000_000);
        records.addFrag("placed", 0, 100, false);
        final Path bam = Files.createTempFile("finishEnd.", ".bam");
        bam.toFile().deleteOnExit();
        try (SAMFileWriter writer =
                new SAMFileWriterFactory().setCreateIndex(false).makeBAMWriter(records.getHeader(), true, bam)) {
            records.getRecords().forEach(writer::addAlignment);
        }
        final Path bai = Files.createTempFile("finishEnd.", ".bai");
        bai.toFile().deleteOnExit();
        final long movedEnd;
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(bam)) {
            final BAMIndexer indexer = new BAMIndexer(bai, reader.getFileHeader());
            for (final SAMRecord record : reader) {
                indexer.processAlignment(record);
            }
            movedEnd = 0xDEAD_0000_0000L; // an arbitrary later pointer
            indexer.finish(movedEnd);
        }

        try (FileBackedBinningIndex idx = FileBackedBinningIndex.open(bai, true)) {
            final BinningIndex index = idx.loadAll();
            final List<Chunk> chunks = index.getReference(0).getChunks(0);
            assertEquals(chunks.get(chunks.size() - 1).getChunkEnd(), movedEnd);
        }
    }

    @Test
    public void testFinishWithEndLeavesChunkEndWhenLastRecordIsUnplaced() throws IOException {
        final SAMRecordSetBuilder records =
                new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate, true, 1_000_000);
        records.addFrag("placed", 0, 100, false);
        records.addUnmappedFragment("unplaced");
        final Path bam = Files.createTempFile("finishEnd.", ".bam");
        bam.toFile().deleteOnExit();
        try (SAMFileWriter writer =
                new SAMFileWriterFactory().setCreateIndex(false).makeBAMWriter(records.getHeader(), true, bam)) {
            records.getRecords().forEach(writer::addAlignment);
        }
        final Path bai = Files.createTempFile("finishEnd.", ".bai");
        bai.toFile().deleteOnExit();
        // Capture the chunk end before calling finish(end)
        long originalEnd;
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(bam)) {
            final BAMIndexer indexer = new BAMIndexer(bai, reader.getFileHeader());
            originalEnd = 0;
            for (final SAMRecord record : reader) {
                if (!record.getReadUnmappedFlag() || record.getAlignmentStart() != SAMRecord.NO_ALIGNMENT_START) {
                    final Chunk chunk = ((BAMFileSpan) record.getFileSource().getFilePointer()).getSingleChunk();
                    originalEnd = chunk.getChunkEnd();
                }
                indexer.processAlignment(record);
            }
            indexer.finish(0xDEAD_0000_0000L);
        }

        try (FileBackedBinningIndex idx = FileBackedBinningIndex.open(bai, true)) {
            final BinningIndex index = idx.loadAll();
            final List<Chunk> chunks = index.getReference(0).getChunks(0);
            // The end should NOT have been moved, because the last record was unplaced
            assertEquals(chunks.get(chunks.size() - 1).getChunkEnd(), originalEnd);
        }
    }

    @Test
    public void testFinishWithEndOnNoRecordsDoesNotThrow() {
        final SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        header.addSequence(new SAMSequenceRecord("chr1", 1000));
        final BAMIndexer indexer = new BAMIndexer(new ByteArrayOutputStream(), header);
        indexer.finish(0xDEAD_0000_0000L); // should not throw
    }

    @Test
    public void testArgumentsAreCheckedBeforeTheIndexPathIsOpened() throws IOException {
        final Path existing = Files.createTempFile("keepMe.", ".bai");
        existing.toFile().deleteOnExit();
        Files.writeString(existing, "an index somebody still wants");
        final SAMFileHeader unindexable = new SAMFileHeader();
        unindexable.setSortOrder(SAMFileHeader.SortOrder.queryname);
        try {
            new BAMIndexer(existing, unindexable);
            throw new AssertionError("a queryname-sorted header was accepted");
        } catch (final SAMException expected) {
            assertEquals(Files.readString(existing), "an index somebody still wants");
        }
    }
}
