package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.index.FileBackedBinningIndex;
import htsjdk.index.ReferenceBins;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.IOUtil;
import htsjdk.utils.SamtoolsTestUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/** Where in a block-compressed SAM file each record lies, and reading the file from such a place. */
public class BlockCompressedSamFilePointerTest extends HtsjdkTest {
    private static final int RECORDS = 6_000; // enough text for a good many BGZF blocks

    /** Coordinate-sorted records on two references, followed by some without a position. */
    private static SAMRecordSetBuilder records() {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        for (int i = 0; i < RECORDS; i++) {
            builder.addFrag("read" + i, i < RECORDS / 2 ? 0 : 1, 1 + 37 * (i % (RECORDS / 2)), i % 2 == 0);
        }
        for (int i = 0; i < 50; i++) {
            builder.addUnmappedFragment("unplaced" + i);
        }
        return builder;
    }

    private static Path writeSamGz(final SAMRecordSetBuilder records) throws IOException {
        final Path samGz = Files.createTempFile("filePointers.", ".sam.gz");
        IOUtil.deleteOnExit(samGz);
        try (SAMFileWriter writer = new SAMFileWriterFactory().makeWriter(records.getHeader(), true, samGz, null)) {
            records.getRecords().forEach(writer::addAlignment);
        }
        return samGz;
    }

    private static SamReader openWithFileSources(final Path samGz) {
        return SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(samGz);
    }

    private static Chunk chunkOf(final SAMRecord record) {
        return ((BAMFileSpan) record.getFileSource().getFilePointer()).getSingleChunk();
    }

    @Test
    public void testEveryRecordCanBeReadBackFromItsFilePointer() throws IOException {
        final Path samGz = writeSamGz(records());
        final List<SAMRecord> all = new ArrayList<>();
        try (SamReader reader = openWithFileSources(samGz)) {
            reader.forEach(all::add);
        }
        Assert.assertEquals(all.size(), RECORDS + 50);

        final Set<Long> blocks = new HashSet<>();
        try (SamReader reader = SamReaderFactory.makeDefault().open(samGz)) {
            for (final SAMRecord expected : all) {
                blocks.add(BlockCompressedFilePointerUtil.getBlockAddress(
                        chunkOf(expected).getChunkStart()));
                try (SAMRecordIterator one =
                        reader.indexing().iterator(expected.getFileSource().getFilePointer())) {
                    Assert.assertEquals(one.next(), expected);
                    Assert.assertFalse(one.hasNext(), "a record's span should hold that record alone");
                }
            }
        }
        Assert.assertTrue(blocks.size() > 5, "the records should be spread over many blocks, not " + blocks.size());
    }

    @Test
    public void testARecordStartsWhereTheOneBeforeItEnds() throws IOException {
        try (SamReader reader = openWithFileSources(writeSamGz(records()))) {
            long previousEnd = ((BAMFileSpan) reader.indexing().getFilePointerSpanningReads())
                    .getSingleChunk()
                    .getChunkStart();
            for (final SAMRecord record : reader) {
                Assert.assertEquals(chunkOf(record).getChunkStart(), previousEnd, record.getReadName());
                previousEnd = chunkOf(record).getChunkEnd();
            }
        }
    }

    @Test
    public void testSpanOfSeveralChunksYieldsTheRecordsOfEachInTurn() throws IOException {
        final Path samGz = writeSamGz(records());
        final List<SAMRecord> all = new ArrayList<>();
        try (SamReader reader = openWithFileSources(samGz)) {
            reader.forEach(all::add);
        }
        // Records 10-19, then 4000-4009: two chunks far apart in the file
        final BAMFileSpan span = new BAMFileSpan(List.of(
                new Chunk(
                        chunkOf(all.get(10)).getChunkStart(),
                        chunkOf(all.get(19)).getChunkEnd()),
                new Chunk(
                        chunkOf(all.get(4_000)).getChunkStart(),
                        chunkOf(all.get(4_009)).getChunkEnd())));
        final List<SAMRecord> expected = new ArrayList<>(all.subList(10, 20));
        expected.addAll(all.subList(4_000, 4_010));

        try (SamReader reader = SamReaderFactory.makeDefault().open(samGz);
                SAMRecordIterator records = reader.indexing().iterator(span)) {
            Assert.assertEquals(records.toList(), expected);
        }
    }

    @Test
    public void testAFileCanBeReadThroughMoreThanOnce() throws IOException {
        try (SamReader reader = SamReaderFactory.makeDefault().open(writeSamGz(records()))) {
            final List<SAMRecord> first;
            try (SAMRecordIterator records = reader.iterator()) {
                first = records.toList();
            }
            try (SAMRecordIterator records = reader.iterator()) {
                Assert.assertEquals(records.toList(), first);
            }
            Assert.assertEquals(first.size(), RECORDS + 50);
        }
    }

    @Test
    public void testAFileWithNoRecordsCanBeReadThroughMoreThanOnce() throws IOException {
        final SAMRecordSetBuilder none = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        try (SamReader reader = SamReaderFactory.makeDefault().open(writeSamGz(none))) {
            for (int pass = 0; pass < 2; pass++) {
                try (SAMRecordIterator records = reader.iterator()) {
                    Assert.assertFalse(records.hasNext());
                }
            }
            Assert.assertTrue(reader.getFileHeader().getSequenceDictionary().size() > 0);
        }
    }

    /**
     * Two block-compressed files joined end to end, as {@code cat} joins them, have the first one's end-of-file marker
     * in the middle. Reading a block at a time, every read starts at a block boundary, that one included.
     */
    @Test
    public void testFileJoinedFromTwoIsReadToItsEndAndFromAnyRecord() throws IOException {
        final SAMRecordSetBuilder records = records();
        final Path whole = writeSamGz(records);
        final List<SAMRecord> expected = new ArrayList<>();
        try (SamReader reader = SamReaderFactory.makeDefault().open(whole)) {
            reader.forEach(expected::add);
        }

        // The second part is records alone: block-compressed text with no header of its own
        final StringBuilder tail = new StringBuilder();
        final int headed = expected.size() / 2;
        expected.subList(headed, expected.size())
                .forEach(record ->
                        tail.append(record.getSAMString().stripTrailing()).append('\n'));
        final Path head = Files.createTempFile("head.", ".sam.gz");
        final Path joined = Files.createTempFile("joined.", ".sam.gz");
        IOUtil.deleteOnExit(head);
        IOUtil.deleteOnExit(joined);
        try (SAMFileWriter writer = new SAMFileWriterFactory().makeWriter(records.getHeader(), true, head, null)) {
            expected.subList(0, headed).forEach(writer::addAlignment);
        }
        try (java.io.OutputStream out = Files.newOutputStream(joined)) {
            Files.copy(head, out);
            try (java.io.OutputStream block = new htsjdk.samtools.util.BlockCompressedOutputStream(out, (Path) null)) {
                block.write(tail.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }

        final List<SAMRecord> found = new ArrayList<>();
        try (SamReader reader = openWithFileSources(joined)) {
            reader.forEach(found::add);
        }
        Assert.assertEquals(found, expected);
        try (SamReader reader = SamReaderFactory.makeDefault().open(joined)) {
            for (final int i : new int[] {0, headed - 1, headed, headed + 1, expected.size() - 1}) {
                try (SAMRecordIterator one =
                        reader.indexing().iterator(found.get(i).getFileSource().getFilePointer())) {
                    Assert.assertEquals(one.next(), expected.get(i), "record " + i);
                }
            }
        }
    }

    @Test
    public void testRecordsReadFromAStreamSayWhereTheyLieToo() throws IOException {
        final Path samGz = writeSamGz(records());
        final List<Chunk> fromFile = new ArrayList<>();
        try (SamReader reader = openWithFileSources(samGz)) {
            reader.forEach(record -> fromFile.add(chunkOf(record)));
        }
        final List<Chunk> fromStream = new ArrayList<>();
        try (InputStream stream = Files.newInputStream(samGz);
                SamReader reader = SamReaderFactory.makeDefault()
                        .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                        .open(SamInputResource.of(stream))) {
            reader.forEach(record -> fromStream.add(chunkOf(record)));
        }
        Assert.assertEquals(fromStream, fromFile);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testAStreamCannotBeReadFromAGivenPlace() throws IOException {
        final Path samGz = writeSamGz(records());
        try (InputStream stream = Files.newInputStream(samGz);
                SamReader reader = SamReaderFactory.makeDefault().open(SamInputResource.of(stream))) {
            reader.indexing().iterator(reader.indexing().getFilePointerSpanningReads());
        }
    }

    @Test
    public void testAMalformedRecordIsReportedWithItsLineNumber() throws IOException {
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        records.addFrag("good", 0, 100, false);
        final Path samGz = Files.createTempFile("malformed.", ".sam.gz");
        IOUtil.deleteOnExit(samGz);
        final StringBuilder text = new StringBuilder();
        try (SAMFileWriter writer = new SAMFileWriterFactory().makeWriter(records.getHeader(), true, samGz, null)) {
            records.getRecords().forEach(writer::addAlignment);
        }
        final int headerLines;
        try (SamReader reader = SamReaderFactory.makeDefault().open(samGz)) {
            headerLines = reader.getFileHeader().getSAMString().split("\n").length;
            text.append(reader.getFileHeader().getSAMString());
            reader.forEach(
                    record -> text.append(record.getSAMString().stripTrailing()).append('\n'));
        }
        text.append("too\tfew\tfields\n");
        try (java.io.OutputStream out = new htsjdk.samtools.util.BlockCompressedOutputStream(samGz, 5)) {
            out.write(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        try (SamReader reader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.STRICT)
                .open(samGz)) {
            reader.forEach(record -> {});
            Assert.fail("the malformed record was accepted");
        } catch (final SAMFormatException e) {
            Assert.assertTrue(e.getMessage().contains("Line " + (headerLines + 2)), e.getMessage());
        }
    }

    /**
     * samtools folds sparsely used bins into the bins above them, which htsjdk does not do, so the two indexes are
     * compared in everything but how their chunks are binned: the file offsets are what reading gets right or wrong.
     */
    @Test
    public void testIndexOfAnExistingFileHasTheOffsetsOfTheOneSamtoolsBuilds() throws IOException {
        if (!SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("samtools is not available");
        }
        final Path samGz = writeSamGz(records());
        final Path ours = Files.createTempFile("ours.", ".bai");
        final Path theirs = Files.createTempFile("theirs.", ".bai");
        IOUtil.deleteOnExit(ours);
        IOUtil.deleteOnExit(theirs);

        try (SamReader reader = openWithFileSources(samGz)) {
            BAMIndexer.createIndex(reader, ours);
        }
        SamtoolsTestUtils.executeSamToolsCommand("index -b -o " + theirs + " " + samGz);

        try (FileBackedBinningIndex ourIndex = FileBackedBinningIndex.open(ours, true);
                FileBackedBinningIndex theirIndex = FileBackedBinningIndex.open(theirs, true)) {
            Assert.assertEquals(ourIndex.getReferenceCount(), theirIndex.getReferenceCount());
            Assert.assertEquals(ourIndex.getNoCoordinateCount(), theirIndex.getNoCoordinateCount());
            for (int i = 0; i < ourIndex.getReferenceCount(); i++) {
                final ReferenceBins ourBins = ourIndex.getReference(i);
                final ReferenceBins theirBins = theirIndex.getReference(i);
                Assert.assertEquals(ourBins.getLinearIndex(), theirBins.getLinearIndex(), "linear index of " + i);
                Assert.assertEquals(ourBins.getMetadata(), theirBins.getMetadata(), "metadata of " + i);
                Assert.assertEquals(allChunksCoalesced(ourBins), allChunksCoalesced(theirBins), "chunks of " + i);
            }
        }
    }

    /** What is left of a reference's chunks once the bins they are filed under are forgotten. */
    private static List<Chunk> allChunksCoalesced(final ReferenceBins bins) {
        final List<Chunk> chunks = new ArrayList<>();
        for (int bin = 0; bin < bins.getBinCount(); bin++) {
            chunks.addAll(bins.getChunks(bin));
        }
        // Chunks that touch are one stretch of the file, however many bins it was split among.
        chunks.sort(null);
        final List<Chunk> coalesced = new ArrayList<>();
        for (final Chunk chunk : chunks) {
            final Chunk last = coalesced.isEmpty() ? null : coalesced.get(coalesced.size() - 1);
            if (last != null && last.getChunkEnd() >= chunk.getChunkStart()) {
                last.setChunkEnd(Math.max(last.getChunkEnd(), chunk.getChunkEnd()));
            } else {
                coalesced.add(chunk.clone());
            }
        }
        return coalesced;
    }
}
