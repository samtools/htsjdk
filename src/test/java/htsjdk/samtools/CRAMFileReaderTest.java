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
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Additional tests for CRAMFileReader are in CRAMFileIndexTest
 */
public class CRAMFileReaderTest extends HtsjdkTest {

    private static final Path TEST_DATA_DIR = Path.of("src/test/resources/htsjdk/samtools");
    private static final Path CRAM_WITH_CRAI = TEST_DATA_DIR.resolve("cram_with_crai_index.cram");
    private static final Path CRAM_WITHOUT_CRAI = TEST_DATA_DIR.resolve("cram_query_sorted.cram");
    private static final ReferenceSource REFERENCE = createReferenceSource();
    private static final Path INDEX_FILE = TEST_DATA_DIR.resolve("cram_with_crai_index.cram.crai");

    private static ReferenceSource createReferenceSource() {
        final byte[] refBases = new byte[10 * 10];
        Arrays.fill(refBases, (byte) 'A');
        InMemoryReferenceSequenceFile rsf = new InMemoryReferenceSequenceFile();
        rsf.add("chr1", refBases);
        return new ReferenceSource(rsf);
    }

    // constructor 1: CRAMFileReader(final Path cramFile, final InputStream inputStream)

    @Test(description = "Test CRAMReader 1 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader1_ReferenceRequired() {
        final InputStream bis = null;
        // assumes that reference_fasta property is not set and the download service is not enabled
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, bis);
        reader.getIterator().hasNext();
    }

    // constructor 2: CRAMFileReader(final Path cramFile, final InputStream inputStream, final ReferenceSource
    // referenceSource)

    @Test(description = "Test CRAMReader 2 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader2ReferenceRequired() {
        final InputStream bis = null;
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, bis, null);
        reader.getIterator().hasNext();
    }

    @Test(description = "Test CRAMReader 2 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader2_InputRequired() {
        final Path path = null;
        final InputStream bis = null;
        final CRAMFileReader reader = new CRAMFileReader(path, bis, createReferenceSource());
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

    // constructor 3: CRAMFileReader(final Path cramFile, final Path indexFile, final ReferenceSource referenceSource)

    @Test(description = "Test CRAMReader 3 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader3_RequiredReference() {
        final Path indexPath = null;
        final ReferenceSource refSource = null;
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, indexPath, refSource);
        reader.getIterator().hasNext();
    }

    @Test(description = "Test CRAMReader 3 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader3_InputRequired() {
        final Path inputPath = null;
        final Path indexPath = null;
        ReferenceSource refSource = null;
        new CRAMFileReader(inputPath, indexPath, refSource);
    }

    @Test
    public void testCRAMReader3_ShouldAutomaticallyFindCRAMIndex() {
        final Path indexPath = null;
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, indexPath, REFERENCE);
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
        final Path indexPath = null;
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITHOUT_CRAI, indexPath, REFERENCE);
        reader.getIndex();
    }

    // constructor 4: CRAMFileReader(final Path cramFile, final ReferenceSource referenceSource)

    @Test(description = "Test CRAMReader 4 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader4_ReferenceRequired() {
        final ReferenceSource refSource = null;
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, refSource);
        reader.getIterator().hasNext();
    }

    @Test(description = "Test CRAMReader 4 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader4_InputRequired() {
        final Path inputPath = null;
        new CRAMFileReader(inputPath, createReferenceSource());
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
        try (final InputStream fis = Files.newInputStream(CRAM_WITH_CRAI)) {
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

    // constructor 6: CRAMFileReader(final InputStream stream, final Path indexFile, final ReferenceSource
    // referenceSource,
    //                final ValidationStringency validationStringency)
    @Test(description = "Test CRAMReader 6 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader6_ReferenceRequired() throws IOException {
        try (final InputStream fis = Files.newInputStream(CRAM_WITH_CRAI)) {
            final Path indexPath = null;
            final ReferenceSource refSource = null;
            final CRAMFileReader reader = new CRAMFileReader(fis, indexPath, refSource, ValidationStringency.STRICT);
            reader.getIterator().hasNext();
        }
    }

    @Test(description = "Test CRAMReader 6 input required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader6_InputRequired() throws IOException {
        InputStream bis = null;
        Path indexPath = null;
        new CRAMFileReader(bis, indexPath, createReferenceSource(), ValidationStringency.STRICT);
    }

    // constructor 7: CRAMFileReader(final Path cramFile, final Path indexFile, final ReferenceSource referenceSource,
    //                final ValidationStringency validationStringency)
    @Test(description = "Test CRAMReader 7 reference required", expectedExceptions = IllegalArgumentException.class)
    public void testCRAMReader7_ReferenceRequired() throws IOException {
        ReferenceSource refSource = null;
        final CRAMFileReader reader =
                new CRAMFileReader(CRAM_WITH_CRAI, CRAM_WITH_CRAI, refSource, ValidationStringency.STRICT);
        reader.getIterator().hasNext();
    }

    @Test
    public void testCRAMReader7_ShouldAutomaticallyFindCRAMIndex() throws IOException {
        Path indexPath = null;
        CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, indexPath, REFERENCE, ValidationStringency.STRICT);
        Assert.assertTrue(reader.hasIndex(), "Can't find existing CRAM index.");
    }

    @Test
    public void testCRAMReader7_ShouldUseCRAMIndex() throws IOException {
        CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, INDEX_FILE, REFERENCE, ValidationStringency.STRICT);
        Assert.assertTrue(reader.hasIndex(), "Can't find existing CRAM index.");
    }

    @Test(expectedExceptions = SAMException.class)
    public void testCRAMReader7_WithoutCRAMIndex() throws IOException {
        Path indexPath = null;
        CRAMFileReader reader =
                new CRAMFileReader(CRAM_WITHOUT_CRAI, indexPath, REFERENCE, ValidationStringency.STRICT);
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

    private static final int MAPPED_READS = 1000;
    private static final int UNMAPPED_READS = 50;
    private static final int READS_PER_CONTAINER = 100;
    private static final int CHR1_LENGTH = 100_000;
    private static final ReferenceSource CHR1_REFERENCE = createChr1Reference();

    private static ReferenceSource createChr1Reference() {
        final byte[] bases = new byte[CHR1_LENGTH];
        Arrays.fill(bases, (byte) 'A');
        final InMemoryReferenceSequenceFile referenceFile = new InMemoryReferenceSequenceFile();
        referenceFile.add("chr1", bases);
        return new ReferenceSource(referenceFile);
    }

    /**
     * Writes a coordinate-sorted CRAM and its CRAI to temporary files: {@link #MAPPED_READS} reads ten bases apart on
     * chr1, then {@link #UNMAPPED_READS} unmapped reads, {@link #READS_PER_CONTAINER} reads to a container.
     *
     * @return the path of the CRAM; its CRAI is beside it
     */
    private static Path writeMultiContainerCramWithCrai() throws IOException {
        final SAMRecordSetBuilder records =
                new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate, true, CHR1_LENGTH);
        for (int i = 0; i < MAPPED_READS; i++) {
            records.addFrag("mapped" + i, 0, 1 + i * 10, false);
        }
        for (int i = 0; i < UNMAPPED_READS; i++) {
            records.addUnmappedFragment("unmapped" + i);
        }
        final CRAMEncodingStrategy smallContainers = new CRAMEncodingStrategy()
                .setMinimumSingleReferenceSliceSize(READS_PER_CONTAINER)
                .setReadsPerSlice(READS_PER_CONTAINER);

        final Path cram = Files.createTempFile("multiContainer.", FileExtensions.CRAM);
        final Path crai = cram.resolveSibling(cram.getFileName() + FileExtensions.CRAM_INDEX);
        IOUtil.deleteOnExit(cram);
        IOUtil.deleteOnExit(crai);
        try (OutputStream cramOut = Files.newOutputStream(cram);
                OutputStream craiOut = Files.newOutputStream(crai);
                CRAMFileWriter writer = new CRAMFileWriter(
                        smallContainers,
                        cramOut,
                        craiOut,
                        true,
                        CHR1_REFERENCE,
                        records.getHeader(),
                        cram.toString())) {
            records.forEach(writer::addAlignment);
        }
        return cram;
    }

    private static SamReader openByPath(final Path cram) {
        return SamReaderFactory.makeDefault().referenceSource(CHR1_REFERENCE).open(cram);
    }

    /** Opens the CRAM and its CRAI as seekable streams, as a reader of a URL does. */
    private static SamReader openBySeekableStreams(final Path cram) throws IOException {
        final Path crai = cram.resolveSibling(cram.getFileName() + FileExtensions.CRAM_INDEX);
        return SamReaderFactory.makeDefault()
                .referenceSource(CHR1_REFERENCE)
                .open(SamInputResource.of(new SeekablePathStream(cram)).index(new SeekablePathStream(crai)));
    }

    private static List<String> drain(final Iterator<SAMRecord> iterator) {
        final List<String> records = new ArrayList<>();
        iterator.forEachRemaining(record -> records.add(record.getSAMString()));
        return records;
    }

    /** Reads one record at a time from each iterator in turn until both are exhausted. */
    private static void readInTurn(
            final Iterator<SAMRecord> first,
            final List<String> firstRecords,
            final Iterator<SAMRecord> second,
            final List<String> secondRecords) {
        while (first.hasNext() || second.hasNext()) {
            if (first.hasNext()) {
                firstRecords.add(first.next().getSAMString());
            }
            if (second.hasNext()) {
                secondRecords.add(second.next().getSAMString());
            }
        }
    }

    @Test
    public void anIteratorAndAQueryReadInTurnFromSeekableStreamsEachReturnAllTheirRecords() throws IOException {
        final Path cram = writeMultiContainerCramWithCrai();
        final List<String> expectedAll;
        final List<String> expectedQuery;
        try (SamReader byPath = openByPath(cram)) {
            expectedAll = drain(byPath.iterator());
            expectedQuery = drain(byPath.queryOverlapping("chr1", 4000, 6000));
        }
        Assert.assertEquals(expectedAll.size(), MAPPED_READS + UNMAPPED_READS);

        final List<String> all = new ArrayList<>();
        final List<String> query = new ArrayList<>();
        try (SamReader byStreams = openBySeekableStreams(cram);
                SAMRecordIterator iterator = byStreams.iterator()) {
            all.add(iterator.next().getSAMString());
            try (SAMRecordIterator queryIterator = byStreams.queryOverlapping("chr1", 4000, 6000)) {
                readInTurn(iterator, all, queryIterator, query);
            }
        }
        Assert.assertEquals(all, expectedAll);
        Assert.assertEquals(query, expectedQuery);
    }

    @Test
    public void overlappingQueriesReadInTurnFromSeekableStreamsEachReturnAllTheirRecords() throws IOException {
        final Path cram = writeMultiContainerCramWithCrai();
        final List<String> expectedWide;
        final List<String> expectedNarrow;
        try (SamReader byPath = openByPath(cram)) {
            expectedWide = drain(byPath.queryOverlapping("chr1", 1000, 8000));
            expectedNarrow = drain(byPath.queryOverlapping("chr1", 3000, 5000));
        }

        final List<String> wide = new ArrayList<>();
        final List<String> narrow = new ArrayList<>();
        try (SamReader byStreams = openBySeekableStreams(cram);
                SAMRecordIterator wideIterator = byStreams.queryOverlapping("chr1", 1000, 8000)) {
            wide.add(wideIterator.next().getSAMString());
            try (SAMRecordIterator narrowIterator = byStreams.queryOverlapping("chr1", 3000, 5000)) {
                readInTurn(wideIterator, wide, narrowIterator, narrow);
            }
        }
        Assert.assertEquals(wide, expectedWide);
        Assert.assertEquals(narrow, expectedNarrow);
    }

    @Test
    public void anIteratorOpenedAfterClosingAnotherOverSeekableStreamsStartsAtTheFirstRecord() throws IOException {
        final Path cram = writeMultiContainerCramWithCrai();
        final List<String> expected;
        try (SamReader byPath = openByPath(cram)) {
            expected = drain(byPath.iterator());
        }

        try (SamReader byStreams = openBySeekableStreams(cram)) {
            try (SAMRecordIterator first = byStreams.iterator()) {
                for (int i = 0; i < 5; i++) {
                    first.next();
                }
            }
            try (SAMRecordIterator second = byStreams.iterator()) {
                Assert.assertEquals(drain(second), expected);
            }
        }
    }

    @Test
    public void anIteratorOpenedAfterQueryUnmappedOverSeekableStreamsStartsAtTheFirstRecord() throws IOException {
        final Path cram = writeMultiContainerCramWithCrai();
        final List<String> expectedAll;
        final List<String> expectedUnmapped;
        try (SamReader byPath = openByPath(cram)) {
            expectedAll = drain(byPath.iterator());
            expectedUnmapped = drain(byPath.queryUnmapped());
        }
        Assert.assertEquals(expectedUnmapped.size(), UNMAPPED_READS);

        try (SamReader byStreams = openBySeekableStreams(cram)) {
            try (SAMRecordIterator unmapped = byStreams.queryUnmapped()) {
                Assert.assertEquals(drain(unmapped), expectedUnmapped);
            }
            try (SAMRecordIterator all = byStreams.iterator()) {
                Assert.assertEquals(drain(all), expectedAll);
            }
        }
    }

    @Test
    public void twoIteratorsReadInTurnFromSeekableStreamsEachReturnEveryRecord() throws IOException {
        final Path cram = writeMultiContainerCramWithCrai();
        final List<String> expected;
        try (SamReader byPath = openByPath(cram)) {
            expected = drain(byPath.iterator());
        }

        final List<String> first = new ArrayList<>();
        final List<String> second = new ArrayList<>();
        try (SamReader byStreams = openBySeekableStreams(cram);
                SAMRecordIterator firstIterator = byStreams.iterator()) {
            for (int i = 0; i < 2 * READS_PER_CONTAINER + 5; i++) {
                first.add(firstIterator.next().getSAMString());
            }
            try (SAMRecordIterator secondIterator = byStreams.iterator()) {
                readInTurn(firstIterator, first, secondIterator, second);
            }
        }
        Assert.assertEquals(first, expected);
        Assert.assertEquals(second, expected);
    }

    @Test
    public void aQueryAfterClosingAnIteratorOverSeekableStreamsStillReadsTheStream() throws IOException {
        final Path cram = writeMultiContainerCramWithCrai();
        final List<String> expected;
        try (SamReader byPath = openByPath(cram)) {
            expected = drain(byPath.queryOverlapping("chr1", 4000, 6000));
        }

        // Unbuffered streams, so that reads after the close reach the stream itself rather than a buffer of it.
        final Path crai = cram.resolveSibling(cram.getFileName() + FileExtensions.CRAM_INDEX);
        try (CRAMFileReader reader = new CRAMFileReader(
                new SeekablePathStream(cram),
                new SeekablePathStream(crai),
                CHR1_REFERENCE,
                ValidationStringency.SILENT)) {
            try (SAMRecordIterator iterator = reader.getIterator()) {
                for (int i = 0; i < 5; i++) {
                    iterator.next();
                }
            }
            try (CloseableIterator<SAMRecord> query =
                    reader.query(new QueryInterval[] {new QueryInterval(0, 4000, 6000)}, false)) {
                Assert.assertEquals(drain(query), expected);
            }
        }
    }

    @Test
    public void aCramStartingPartwayIntoASeekableStreamIteratesAllItsRecordsEachTime() throws IOException {
        final Path cram = writeMultiContainerCramWithCrai();
        final List<String> expected;
        try (SamReader byPath = openByPath(cram)) {
            expected = drain(byPath.iterator());
        }
        final byte[] cramBytes = Files.readAllBytes(cram);
        final int prefixLength = 1000;
        final byte[] prefixedCram = new byte[prefixLength + cramBytes.length];
        Arrays.fill(prefixedCram, 0, prefixLength, (byte) 'x');
        System.arraycopy(cramBytes, 0, prefixedCram, prefixLength, cramBytes.length);

        final SeekableMemoryStream stream = new SeekableMemoryStream(prefixedCram, "prefixed.cram");
        stream.seek(prefixLength);
        try (CRAMFileReader reader =
                new CRAMFileReader(stream, (SeekableStream) null, CHR1_REFERENCE, ValidationStringency.SILENT)) {
            try (SAMRecordIterator first = reader.getIterator()) {
                Assert.assertEquals(drain(first), expected);
            }
            try (SAMRecordIterator second = reader.getIterator()) {
                Assert.assertEquals(drain(second), expected);
            }
        }
    }

    @Test
    public void aReaderMadeWithoutAStringencyFromAPathIsSilent() {
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, REFERENCE);
        Assert.assertEquals(reader.getValidationStringency(), ValidationStringency.SILENT);
        reader.close();
    }

    @Test
    public void aReaderMadeWithoutAStringencyFromAPathAndIndexIsSilent() {
        final CRAMFileReader reader = new CRAMFileReader(CRAM_WITH_CRAI, INDEX_FILE, REFERENCE);
        Assert.assertEquals(reader.getValidationStringency(), ValidationStringency.SILENT);
        reader.close();
    }

    @Test
    public void aReaderMadeWithoutAStringencyFromAStreamIsSilent() throws IOException {
        final CRAMFileReader reader = new CRAMFileReader((Path) null, Files.newInputStream(CRAM_WITH_CRAI), REFERENCE);
        Assert.assertEquals(reader.getValidationStringency(), ValidationStringency.SILENT);
        reader.close();
    }
}
