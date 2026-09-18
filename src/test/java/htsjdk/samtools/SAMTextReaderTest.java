/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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
import htsjdk.index.BinningIndex;
import htsjdk.index.FileBackedBinningIndex;
import htsjdk.index.ReferenceBins;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.tribble.index.tabix.TabixIndexType;
import htsjdk.tribble.util.LittleEndianOutputStream;
import htsjdk.utils.SamtoolsTestUtils;
import htsjdk.utils.TabixTestUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SAMTextReaderTest extends HtsjdkTest {
    private static final String ARRAY_TAG = "xa";

    // Simple input, spot check that parsed correctly, and make sure nothing blows up.
    @Test
    public void testBasic() throws Exception {
        final String seq1 = "AGCTTAGCTAGCTACCTATATCTTGGTCTTGGCCG";
        final String seq2 = "ACCTATATCTTGGCCTTGGCCGATGCGGCCTTGCA";
        final String qual1 = "<<<<<<<<<<<<<<<<<<<<<:<9/,&,22;;<<<";
        final String qual2 = "<<<<<;<<<<7;:<<<6;<<<<<<<<<<<<7<<<<";
        final String fileFormatVersion = "1.0";
        final String sequence = "chr20";
        final int sequenceLength = 62435964;
        final String charTag = "XC";
        final char charValue = 'q';
        final String intTag = "XI";
        final int intValue = 12345;
        final String floatTag = "XF";
        final float floatValue = 1.2345f;
        final String stringTag = "XS";
        final String stringValue = "Hi,Mom!";
        final String samExample = "@HD\tVN:" + fileFormatVersion + "\t" + charTag + ":" + charValue + "\n" + "@SQ\tSN:"
                + sequence + "\tAS:HG18\tLN:" + sequenceLength + "\t" + intTag + ":" + intValue + "\n"
                + "@RG\tID:L1\tPU:SC_1_10\tLB:SC_1\tSM:NA12891"
                + "\t" + floatTag + ":" + floatValue + "\n" + "@RG\tID:L2\tPU:SC_2_12\tLB:SC_2\tSM:NA12891\n"
                + "@PG\tID:0\tVN:1.0\tCL:yo baby\t"
                + stringTag + ":" + stringValue + "\n" + "@PG\tID:2\tVN:1.1\tCL:whassup? ? ? ?\n"
                + "read_28833_29006_6945\t99\tchr20\t28833\t20\t10M1D25M\t=\t28993\t195\t"
                + seq1.toLowerCase()
                + "\t" + qual1 + "\t" + "MF:i:130\tNm:i:1\tH0:i:0\tH1:i:0\tRG:Z:L1\n"
                + "read_28701_28881_323b\t147\tchr20\t28834\t30\t35M\t=\t28701\t-168\t"
                + seq2
                + "\t" + qual2 + "\t" + "MF:i:18\tNm:i:0\tH0:i:1\tH1:i:0\tRG:Z:L2\n";

        final String[] samResults = {
            "read_28833_29006_6945\t99\tchr20\t28833\t20\t10M1D25M\t=\t28993\t195\t" + seq1 + "\t" + qual1
                    + "\tH0:i:0\tH1:i:0\tMF:i:130\tRG:Z:L1\tNm:i:1",
            "read_28701_28881_323b\t147\tchr20\t28834\t30\t35M\t=\t28701\t-168\t" + seq2 + "\t" + qual2
                    + "\tH0:i:1\tH1:i:0\tMF:i:18\tRG:Z:L2\tNm:i:0"
        };

        final SamReader samReader = createSamFileReader(samExample);
        final SAMFileHeader fileHeader = samReader.getFileHeader();

        Assert.assertEquals(fileHeader.getVersion(), fileFormatVersion);
        Assert.assertEquals(fileHeader.getAttribute(charTag), Character.toString(charValue));
        final SAMSequenceRecord sequenceRecord = fileHeader.getSequence(sequence);
        Assert.assertNotNull(sequenceRecord);
        Assert.assertEquals(sequenceRecord.getSequenceLength(), sequenceLength);
        Assert.assertEquals(sequenceRecord.getAttribute(intTag), Integer.toString(intValue));
        Assert.assertEquals(fileHeader.getReadGroup("L1").getAttribute(floatTag), Float.toString(floatValue));
        Assert.assertEquals(fileHeader.getProgramRecord("0").getAttribute(stringTag), stringValue);

        final CloseableIterator<SAMRecord> iterator = samReader.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            final SAMRecord rec = iterator.next();
            Assert.assertEquals(rec.getSAMString(), samResults[i++]);
        }
        iterator.close();
        iterator.close();
        samReader.close();
    }

    private SamReader createSamFileReader(final String samExample) {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(samExample.getBytes());
        return SamReaderFactory.makeDefault().open(SamInputResource.of(inputStream));
    }

    @Test
    public void testUnmapped() {
        final String alignmentFromKris =
                "0\t4\t*\t0\t0\t*\t*\t0\t0\tGCCTCGTAGTGCGCCATCAGTCTATCGATGTCGTTG\t44\"44===;;;;;;;;;::::88844\"4\"\"\"\"\"\"\"\"\n";
        final SamReader samReader = createSamFileReader(alignmentFromKris);
        final CloseableIterator<SAMRecord> iterator = samReader.iterator();
        while (iterator.hasNext()) {
            iterator.next();
        }
        iterator.close();
        CloserUtil.close(samReader);
    }

    /**
     * Colon separates fields of a text tag, but colon is also valid in a tag value, so assert that works properly.
     */
    @Test
    public void testTagWithColon() {
        // Create a SAMRecord with a String tag containing a colon
        final SAMRecordSetBuilder samBuilder = new SAMRecordSetBuilder();
        samBuilder.addUnmappedFragment("Hi,Mom!");
        final SAMRecord rec = samBuilder.iterator().next();
        final String valueWithColons = "A:B::C:::";
        rec.setAttribute(SAMTag.CQ, valueWithColons);
        // Write the record as SAM Text
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final SAMFileWriter textWriter = new SAMFileWriterFactory().makeSAMWriter(samBuilder.getHeader(), true, os);
        textWriter.addAlignment(rec);
        textWriter.close();

        final SamReader reader =
                SamReaderFactory.makeDefault().open(SamInputResource.of(new ByteArrayInputStream(os.toByteArray())));
        final SAMRecord recFromText = reader.iterator().next();
        Assert.assertEquals(recFromText.getAttribute(SAMTag.CQ), valueWithColons);
        CloserUtil.close(reader);
    }

    @DataProvider
    public Object[][] getRecordsWithArrays() {
        final String recordBase = "Read\t4\tchr1\t1\t0\t*\t*\t0\t0\tG\t%\t";
        return new Object[][] {
            {recordBase + ARRAY_TAG + ":B:i", new int[0]},
            {recordBase + ARRAY_TAG + ":B:i,", new int[0]},
            {recordBase + ARRAY_TAG + ":B:i,1,2,3,", new int[] {1, 2, 3}},
        };
    }

    @Test(dataProvider = "getRecordsWithArrays")
    public void testSamRecordCanHandleArrays(String samRecord, Object array) {
        final SAMLineParser samLineParser = new SAMLineParser(new SAMFileHeader());
        final SAMRecord record = samLineParser.parseLine(samRecord);
        Assert.assertEquals(record.getAttribute(ARRAY_TAG), array);
    }

    @Test
    public void testFlagAboveSignedIntRangeIsRejected() {
        // The fast FLAG parser must not silently sign-extend values > Integer.MAX_VALUE.
        // The spec restricts FLAG to 16 bits; the legacy path threw, and we want to preserve that.
        final SAMLineParser parser = new SAMLineParser(new SAMFileHeader());
        final String line = "Read\t4294967295\tchr1\t1\t0\t*\t*\t0\t0\t*\t*";
        try {
            parser.parseLine(line);
            Assert.fail("Expected SAMFormatException for FLAG above signed-int range");
        } catch (final SAMFormatException expected) {
            // expected
        }
    }

    @Test
    public void testMrnmEqualsWithRnameNotInDictionaryDoesNotNpe() {
        // Regression: when RNAME is specified but the name is not present in the sequence
        // dictionary, parsing a record with MRNM='=' must not pass a null mate reference name
        // through to setMateReferenceNameAndIndex (which would NPE callers downstream).
        // The mate reference name should mirror the record's reference name.
        final SAMFileHeader header = new SAMFileHeader();
        // Header has no @SQ records, so any RNAME lookup will miss.
        final SAMLineParser parser =
                new SAMLineParser(new DefaultSAMRecordFactory(), ValidationStringency.SILENT, header, null, null);
        // FLAG 99 = paired + proper-pair + mate-reverse + first-of-pair; mate must be mapped.
        final String line = "Read\t99\tchrUnknown\t100\t30\t50M\t=\t150\t100\t"
                + "ACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTAC\t"
                + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
        final SAMRecord record = parser.parseLine(line);
        Assert.assertEquals(record.getReferenceName(), "chrUnknown");
        Assert.assertEquals(record.getMateReferenceName(), "chrUnknown");
    }

    @Test
    public void testErrorMessageReportsCurrentLineAfterMixedParseModes() {
        // Regression test: after a successful parseLine(String) call, parseLineFromBytes on the
        // same parser must produce error messages referencing the BYTE line just handed to it,
        // not a stale line from the earlier call (the error message is reconstructed from the byte
        // range that parseLineFromBytes records on every call).
        final SAMLineParser strict = new SAMLineParser(
                new DefaultSAMRecordFactory(), ValidationStringency.STRICT, new SAMFileHeader(), null, null);
        // Warm up the same parser with a well-formed line through the String API.
        final String good = "Read\t4\tchr1\t1\t0\t*\t*\t0\t0\tG\t%";
        strict.parseLine(good);
        // Now feed an obviously malformed byte line (too few tab-separated fields); STRICT surfaces it.
        final byte[] bad = "Read\tNOT-AN-INT\t".getBytes();
        try {
            strict.parseLineFromBytes(bad, 0, bad.length, 7);
            Assert.fail("Expected an exception");
        } catch (final SAMFormatException e) {
            // The error message must reference the byte line "Read\tNOT-AN-INT\t", not the
            // earlier "good" line.
            final String message = e.getMessage();
            Assert.assertTrue(
                    message.contains("NOT-AN-INT") || message.contains("Read\tNOT-AN-INT"),
                    "Expected error message to reference the byte line; got: " + message);
            Assert.assertFalse(
                    message.contains("chr1"), "Error message must not reference the stale prior line; got: " + message);
        }
    }

    // Block-compressed SAM: where in the file each record lies, and reading the file from such a place.

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

    // Queries of block-compressed SAM through a BAI or CSI index. What a query should return is settled by asking
    // the same of a BAM that holds the same records.

    private static final int PLACED = 8_000;

    private SAMFileHeader header;
    private Path bam;
    private Path samGzWithBai;
    private Path samGzWithCsi;
    private Path samGzWithoutIndex;

    @BeforeClass
    public void writeFilesToQuery() throws IOException {
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        final Random random = new Random(11);
        for (int i = 0; i < PLACED; i++) {
            // Reference 1 is left without reads, and one read in fifty is long enough to reach into other bins.
            final int reference = i < PLACED / 2 ? 0 : 2;
            final int start = 1 + 50 * (i % (PLACED / 2)) + random.nextInt(40);
            if (i % 50 == 0) {
                records.addFrag("long" + i, reference, start, false, false, "20000M", null, 30);
            } else if (i % 7 == 0) {
                records.addPair("pair" + i, reference, start, start + 300);
            } else {
                records.addFrag("read" + i, reference, start, i % 2 == 0);
            }
        }
        for (int i = 0; i < 40; i++) {
            records.addUnmappedFragment("unplaced" + i);
        }
        header = records.getHeader();

        final Path directory = Files.createTempDirectory("samGzQuery");
        IOUtil.deleteOnExit(directory);
        bam = directory.resolve("records.bam");
        samGzWithBai = directory.resolve("bai.sam.gz");
        samGzWithCsi = directory.resolve("csi.sam.gz");
        samGzWithoutIndex = directory.resolve("bare.sam.gz");
        final List<SAMRecord> sorted = new ArrayList<>();
        records.iterator().forEachRemaining(sorted::add);
        try (SAMFileWriter writer =
                new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(header, true, bam)) {
            sorted.forEach(writer::addAlignment);
        }
        for (final Path samGz : List.of(samGzWithBai, samGzWithCsi, samGzWithoutIndex)) {
            try (SAMFileWriter writer = new SAMFileWriterFactory().makeWriter(header, true, samGz, null)) {
                sorted.forEach(writer::addAlignment);
            }
        }
        index(samGzWithBai, samGzWithBai.resolveSibling("bai.sam.gz.bai"), BamIndexType.BAI);
        index(samGzWithCsi, samGzWithCsi.resolveSibling("csi.sam.gz.csi"), BamIndexType.CSI);
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            files.forEach(IOUtil::deleteOnExit);
        }
    }

    private static void index(final Path samGz, final Path index, final BamIndexType type) throws IOException {
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(samGz)) {
            BAMIndexer.createIndex(reader, index, null, type);
        }
    }

    private static List<String> drain(final SAMRecordIterator records) {
        try (records) {
            return records.stream().map(SAMRecord::getSAMString).collect(Collectors.toList());
        }
    }

    /** Runs the same randomly placed queries, small and large, against the SAM and the BAM. */
    private void assertQueriesAgreeWithBam(final Path samGz, final boolean contained) throws IOException {
        final Random random = new Random(5);
        int recordsSeen = 0;
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz);
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            for (int i = 0; i < 150; i++) {
                final String sequence = header.getSequence(random.nextInt(3)).getSequenceName();
                final int start = 1 + random.nextInt(220_000);
                final int end = start + random.nextInt(i % 5 == 0 ? 60_000 : 700);
                final List<String> found = drain(sam.query(sequence, start, end, contained));
                Assert.assertEquals(
                        found,
                        drain(expected.query(sequence, start, end, contained)),
                        sequence + ":" + start + "-" + end);
                recordsSeen += found.size();
            }
        }
        Assert.assertTrue(recordsSeen > 1_000, "the queries should have found plenty of records, not " + recordsSeen);
    }

    @Test
    public void testOverlapQueriesThroughABai() throws IOException {
        assertQueriesAgreeWithBam(samGzWithBai, false);
    }

    @Test
    public void testContainedQueriesThroughABai() throws IOException {
        assertQueriesAgreeWithBam(samGzWithBai, true);
    }

    @Test
    public void testOverlapQueriesThroughACsi() throws IOException {
        assertQueriesAgreeWithBam(samGzWithCsi, false);
    }

    @Test
    public void testContainedQueriesThroughACsi() throws IOException {
        assertQueriesAgreeWithBam(samGzWithCsi, true);
    }

    @Test
    public void testQueryOfSeveralIntervalsAtOnce() throws IOException {
        final QueryInterval[] intervals = QueryInterval.optimizeIntervals(new QueryInterval[] {
            new QueryInterval(0, 1_000, 3_000),
            new QueryInterval(0, 150_000, 151_000),
            new QueryInterval(1, 1, 100_000),
            new QueryInterval(2, 40_000, 40_500),
        });
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithBai);
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            final List<String> found = drain(sam.query(intervals, false));
            Assert.assertEquals(found, drain(expected.query(intervals, false)));
            Assert.assertTrue(found.size() > 50);
        }
    }

    @Test
    public void testQueryOfAReferenceWithoutReadsFindsNothing() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithBai)) {
            Assert.assertEquals(drain(sam.queryOverlapping("chr2", 1, 0)), List.of());
        }
    }

    @Test
    public void testQueryAlignmentStart() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithCsi);
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            int found = 0;
            for (final SAMRecord record : expected) {
                if (found == 25 || record.getReadUnmappedFlag()) break;
                if (record.getAlignmentStart() % 3 != 0) continue;
                found++;
                final String sequence = record.getReferenceName();
                Assert.assertEquals(
                        drain(sam.queryAlignmentStart(sequence, record.getAlignmentStart())),
                        queryAlignmentStartInBam(sequence, record.getAlignmentStart()));
            }
            Assert.assertEquals(found, 25);
        }
    }

    private List<String> queryAlignmentStartInBam(final String sequence, final int start) throws IOException {
        try (SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
            return drain(reader.queryAlignmentStart(sequence, start));
        }
    }

    @Test
    public void testQueryUnmappedThroughABai() throws IOException {
        assertUnmappedAgreeWithBam(samGzWithBai);
    }

    @Test
    public void testQueryUnmappedThroughACsi() throws IOException {
        assertUnmappedAgreeWithBam(samGzWithCsi);
    }

    private void assertUnmappedAgreeWithBam(final Path samGz) throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz);
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            final List<String> found = drain(sam.queryUnmapped());
            Assert.assertEquals(found, drain(expected.queryUnmapped()));
            Assert.assertEquals(found.size(), 40);
        }
    }

    @Test
    public void testQueryMate() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithBai);
                SamReader other = SamReaderFactory.makeDefault().open(samGzWithBai)) {
            int pairs = 0;
            for (final SAMRecord record : other) {
                if (pairs == 10) break;
                if (!record.getReadPairedFlag() || !record.getFirstOfPairFlag()) continue;
                pairs++;
                final SAMRecord mate = sam.queryMate(record);
                Assert.assertEquals(mate.getReadName(), record.getReadName());
                Assert.assertTrue(mate.getSecondOfPairFlag());
            }
            Assert.assertEquals(pairs, 10);
        }
    }

    @Test
    public void testOneReaderAnswersQueryAfterQuery() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithBai)) {
            final List<String> first = drain(sam.queryOverlapping("chr1", 5_000, 6_000));
            drain(sam.queryOverlapping("chr3", 90_000, 95_000));
            Assert.assertEquals(drain(sam.queryOverlapping("chr1", 5_000, 6_000)), first);
            Assert.assertFalse(first.isEmpty());
            Assert.assertEquals(drain(sam.iterator()).size(), drainBam().size());
        }
    }

    private List<String> drainBam() throws IOException {
        try (SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
            return drain(reader.iterator());
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testASecondQueryWhileOneIsOpenIsRefused() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithBai);
                SAMRecordIterator open = sam.queryOverlapping("chr1", 5_000, 6_000)) {
            sam.queryOverlapping("chr1", 7_000, 8_000);
        }
    }

    @Test
    public void testIndexBesideTheFileIsFound() throws IOException {
        try (SamReader withBai = SamReaderFactory.makeDefault().open(samGzWithBai);
                SamReader withCsi = SamReaderFactory.makeDefault().open(samGzWithCsi);
                SamReader without = SamReaderFactory.makeDefault().open(samGzWithoutIndex)) {
            Assert.assertTrue(withBai.hasIndex());
            Assert.assertTrue(withCsi.hasIndex());
            Assert.assertFalse(without.hasIndex());
            Assert.assertTrue(withBai.indexing().hasBrowseableIndex());
        }
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testQueryWithoutAnIndexIsUnsupported() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithoutIndex)) {
            sam.queryOverlapping("chr1", 5_000, 6_000);
        }
    }

    @Test
    public void testIndexNamedExplicitly() throws IOException {
        final Path elsewhere = Files.createTempFile("elsewhere.", ".csi");
        IOUtil.deleteOnExit(elsewhere);
        Files.copy(
                samGzWithCsi.resolveSibling("csi.sam.gz.csi"),
                elsewhere,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try (SamReader sam = SamReaderFactory.makeDefault()
                        .open(SamInputResource.of(samGzWithoutIndex).index(elsewhere));
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            Assert.assertEquals(
                    drain(sam.queryOverlapping("chr3", 10_000, 12_000)),
                    drain(expected.queryOverlapping("chr3", 10_000, 12_000)));
        }
    }

    @Test
    public void testIndexGivenAsAStream() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault()
                        .open(SamInputResource.of(samGzWithoutIndex)
                                .index(new SeekablePathStream(samGzWithBai.resolveSibling("bai.sam.gz.bai"))));
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            Assert.assertEquals(
                    drain(sam.queryOverlapping("chr1", 10_000, 12_000)),
                    drain(expected.queryOverlapping("chr1", 10_000, 12_000)));
        }
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testIndexCoveringADifferentNumberOfReferencesIsRefused() throws IOException {
        final SAMRecordSetBuilder other = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        other.getHeader()
                .setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("only", 1_000_000))));
        other.addFrag("read", 0, 100, false);
        final Path otherBam = Files.createTempFile("other.", ".bam");
        IOUtil.deleteOnExit(otherBam);
        IOUtil.deleteOnExit(
                otherBam.resolveSibling(otherBam.getFileName().toString().replaceAll("\\.bam$", ".bai")));
        try (SAMFileWriter writer =
                new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(other.getHeader(), true, otherBam)) {
            other.getRecords().forEach(writer::addAlignment);
        }
        final Path otherBai =
                otherBam.resolveSibling(otherBam.getFileName().toString().replaceAll("\\.bam$", ".bai"));

        try (SamReader sam = SamReaderFactory.makeDefault()
                .open(SamInputResource.of(samGzWithoutIndex).index(otherBai))) {
            sam.queryOverlapping("chr1", 5_000, 6_000);
        }
    }

    @Test
    public void testIndexStreamThatIsRefusedIsClosedOnce() throws IOException {
        final int[] closes = {0};
        final SeekablePathStream baiOfAnotherFile = new SeekablePathStream(bam.resolveSibling("records.bai")) {
            @Override
            public void close() throws IOException {
                closes[0]++;
                super.close();
            }
        };
        // The oracle BAM's index fits the file, so cut the reader's header down to make it not fit
        final Path oneSequence = Files.createTempFile("oneSequence.", ".sam.gz");
        IOUtil.deleteOnExit(oneSequence);
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        records.getHeader()
                .setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("only", 1_000_000))));
        records.addFrag("read", 0, 100, false);
        try (SAMFileWriter writer =
                new SAMFileWriterFactory().makeWriter(records.getHeader(), true, oneSequence, null)) {
            records.getRecords().forEach(writer::addAlignment);
        }

        try (SamReader sam = SamReaderFactory.makeDefault()
                .open(SamInputResource.of(oneSequence).index(baiOfAnotherFile))) {
            Assert.assertThrows(SAMFormatException.class, () -> sam.queryOverlapping("only", 1, 1_000));
        }
        Assert.assertEquals(closes[0], 1);
    }

    @Test
    public void testIndexesBuiltBySamtools() throws IOException {
        if (!SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("samtools is not available");
        }
        final Path bai = Files.createTempFile("samtools.", ".bai");
        final Path csi = Files.createTempFile("samtools.", ".csi");
        IOUtil.deleteOnExit(bai);
        IOUtil.deleteOnExit(csi);
        SamtoolsTestUtils.executeSamToolsCommand("index -b -o " + bai + " " + samGzWithoutIndex);
        SamtoolsTestUtils.executeSamToolsCommand("index -c -o " + csi + " " + samGzWithoutIndex);

        for (final Path index : List.of(bai, csi)) {
            try (SamReader sam = SamReaderFactory.makeDefault()
                            .open(SamInputResource.of(samGzWithoutIndex).index(index));
                    SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
                for (final int start : new int[] {1, 16_380, 100_000, 199_000}) {
                    Assert.assertEquals(
                            drain(sam.queryOverlapping("chr3", start, start + 5_000)),
                            drain(expected.queryOverlapping("chr3", start, start + 5_000)),
                            index + " at " + start);
                }
                Assert.assertEquals(drain(sam.queryUnmapped()), drain(expected.queryUnmapped()), index.toString());
            }
        }
    }

    @Test
    public void testIndexStreamIsClosedWhenTheHeaderCannotBeRead() throws IOException {
        final Path badHeader = Files.createTempFile("badHeader.", ".sam.gz");
        IOUtil.deleteOnExit(badHeader);
        try (java.io.OutputStream out = new htsjdk.samtools.util.BlockCompressedOutputStream(badHeader, 5)) {
            out.write("@HD\tVN:1.6\tSO:coordinate\n@SQ\tSN:chr1\tLN:notANumber\n"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        final int[] closes = {0};
        final SeekablePathStream index = new SeekablePathStream(bam.resolveSibling("records.bai")) {
            @Override
            public void close() throws IOException {
                closes[0]++;
                super.close();
            }
        };

        Assert.assertThrows(RuntimeException.class, () -> SamReaderFactory.makeDefault()
                .open(SamInputResource.of(badHeader).index(index)));
        Assert.assertEquals(closes[0], 1);
    }

    // Block-compressed SAM indexed the way tabix indexes it: references numbered as the file meets them, and named.
    // The header's first sequence has no reads in these files, so every reference has one number in the header and
    // another in the index, and an index asked by header ordinal would answer with some other reference's reads.

    private List<SAMRecord> sparseRecords;
    private SAMFileHeader sparseHeader;
    private Path sparseBam;

    @BeforeClass
    public void writeSparseBam() throws IOException {
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        for (int i = 0; i < 3_000; i++) {
            records.addFrag("read" + i, i < 1_500 ? 1 : 3, 1 + 60 * (i % 1_500), i % 2 == 0);
        }
        for (int i = 0; i < 25; i++) {
            records.addUnmappedFragment("unplaced" + i);
        }
        sparseHeader = records.getHeader();
        sparseRecords = new ArrayList<>();
        records.iterator().forEachRemaining(sparseRecords::add);

        sparseBam = Files.createTempFile("tabixOracle.", ".bam");
        IOUtil.deleteOnExit(sparseBam);
        IOUtil.deleteOnExit(
                sparseBam.resolveSibling(sparseBam.getFileName().toString().replaceAll("\\.bam$", ".bai")));
        try (SAMFileWriter writer =
                new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(sparseHeader, true, sparseBam)) {
            sparseRecords.forEach(writer::addAlignment);
        }
    }

    /** A fresh copy of the records as block-compressed SAM, in a directory of its own so that its index is unambiguous. */
    private Path writeSparseSamGz() throws IOException {
        final Path directory = Files.createTempDirectory("samGzTabix");
        IOUtil.deleteOnExit(directory);
        final Path samGz = directory.resolve("records.sam.gz");
        IOUtil.deleteOnExit(samGz);
        try (SAMFileWriter writer = new SAMFileWriterFactory().makeWriter(sparseHeader, true, samGz, null)) {
            sparseRecords.forEach(writer::addAlignment);
        }
        return samGz;
    }

    /**
     * Indexes a file as tabix would: a reference per sequence that has reads, in the order the file meets them.
     *
     * @param format what the index is to say the file's format is
     * @param naming what the index is to call each sequence, given its name in the file
     * @param listUnplaced whether to list {@code *} as a sequence, as tabix does for a file with unplaced reads
     */
    private Path indexAsTabixWould(
            final Path samGz,
            final TabixIndexType type,
            final TabixFormat format,
            final UnaryOperator<String> naming,
            final boolean listUnplaced)
            throws IOException {
        final List<String> names = new ArrayList<>();
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 5, type == TabixIndexType.CSI);
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(samGz)) {
            for (final SAMRecord record : reader) {
                if (record.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                    break;
                }
                if (!names.contains(record.getReferenceName())) {
                    names.add(record.getReferenceName());
                }
                final Chunk chunk = ((BAMFileSpan) record.getFileSource().getFilePointer()).getSingleChunk();
                builder.add(
                        names.indexOf(record.getReferenceName()),
                        record.getAlignmentStart(),
                        record.getAlignmentEnd(),
                        chunk.getChunkStart(),
                        chunk.getChunkEnd());
            }
        }
        final List<String> listed = names.stream().map(naming).collect(Collectors.toCollection(ArrayList::new));
        if (listUnplaced) {
            listed.add(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME);
        }
        final Path index = samGz.resolveSibling(samGz.getFileName() + type.getExtension());
        IOUtil.deleteOnExit(index);
        new TabixIndex(format, listed, builder.build(listed.size()), type).write(index);
        return index;
    }

    /** Queries on each of the header's first four sequences, two of which have reads, and for the unplaced reads. */
    private void assertAnswersAsTheBamDoes(final Path samGz) throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz);
                SamReader expected = SamReaderFactory.makeDefault().open(sparseBam)) {
            Assert.assertTrue(sam.hasIndex(), "the index beside the file should have been found");
            int found = 0;
            for (int reference = 0; reference < 4; reference++) {
                final String sequence = sparseHeader.getSequence(reference).getSequenceName();
                for (final int start : new int[] {1, 16_000, 45_000, 89_000}) {
                    final List<String> records = drain(sam.queryOverlapping(sequence, start, start + 3_000));
                    Assert.assertEquals(
                            records,
                            drain(expected.queryOverlapping(sequence, start, start + 3_000)),
                            sequence + ":" + start);
                    found += records.size();
                }
            }
            Assert.assertTrue(found > 300, "the queries should have found plenty of records, not " + found);
            Assert.assertEquals(drain(sam.queryUnmapped()), drain(expected.queryUnmapped()));
        }
    }

    @Test
    public void testTbiIsAskedByName() throws IOException {
        final Path samGz = writeSparseSamGz();
        indexAsTabixWould(samGz, TabixIndexType.TBI, TabixFormat.SAM, UnaryOperator.identity(), true);
        assertAnswersAsTheBamDoes(samGz);
    }

    @Test
    public void testCsiWithATabixHeaderIsAskedByName() throws IOException {
        final Path samGz = writeSparseSamGz();
        indexAsTabixWould(samGz, TabixIndexType.CSI, TabixFormat.SAM, UnaryOperator.identity(), true);
        assertAnswersAsTheBamDoes(samGz);
    }

    @Test
    public void testCsiWithATabixHeaderGivenAsAStream() throws IOException {
        final Path samGz = writeSparseSamGz();
        final Path csi = indexAsTabixWould(samGz, TabixIndexType.CSI, TabixFormat.SAM, UnaryOperator.identity(), false);
        final Path elsewhere = Files.createTempFile("elsewhere.", ".index");
        IOUtil.deleteOnExit(elsewhere);
        Files.move(csi, elsewhere, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        try (SamReader sam = SamReaderFactory.makeDefault()
                        .open(SamInputResource.of(samGz)
                                .index(new htsjdk.samtools.seekablestream.SeekablePathStream(elsewhere)));
                SamReader expected = SamReaderFactory.makeDefault().open(sparseBam)) {
            Assert.assertEquals(
                    drain(sam.queryOverlapping("chr4", 10_000, 14_000)),
                    drain(expected.queryOverlapping("chr4", 10_000, 14_000)));
        }
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testTabixIndexMadeForAnotherFormatIsRefused() throws IOException {
        final Path samGz = writeSparseSamGz();
        indexAsTabixWould(samGz, TabixIndexType.TBI, TabixFormat.VCF, UnaryOperator.identity(), false);
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz)) {
            sam.queryOverlapping("chr2", 1, 1_000);
        }
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testTabixIndexNamingASequenceTheHeaderLacksIsRefused() throws IOException {
        final Path samGz = writeSparseSamGz();
        indexAsTabixWould(samGz, TabixIndexType.CSI, TabixFormat.SAM, name -> "other_" + name, false);
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz)) {
            sam.queryOverlapping("chr2", 1, 1_000);
        }
    }

    @Test
    public void testTbiBuiltByTabix() throws IOException {
        assertIndexBuiltByTabixAnswersAsTheBamDoes();
    }

    @Test
    public void testCsiBuiltByTabix() throws IOException {
        assertIndexBuiltByTabixAnswersAsTheBamDoes("-C");
    }

    private void assertIndexBuiltByTabixAnswersAsTheBamDoes(final String... options) throws IOException {
        if (!TabixTestUtils.isTabixAvailable()) {
            throw new SkipException("tabix is not available");
        }
        final Path samGz = writeSparseSamGz();
        final List<String> arguments = new ArrayList<>(List.of(options));
        arguments.addAll(List.of("-f", "-p", "sam", samGz.toString()));
        TabixTestUtils.executeTabix(arguments.toArray(new String[0]));
        try (java.util.stream.Stream<Path> files = Files.list(samGz.getParent())) {
            files.forEach(IOUtil::deleteOnExit);
        }
        assertAnswersAsTheBamDoes(samGz);
    }

    /** Indexes block-compressed SAM with a CSI of htsjdk's own making, which names the header's sequences, placed beside it. */
    private static Path indexWithCsi(final Path samGz) throws IOException {
        final Path csi = samGz.resolveSibling(samGz.getFileName() + ".csi");
        IOUtil.deleteOnExit(csi);
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(samGz)) {
            BAMIndexer.createIndex(reader, csi, null, BamIndexType.CSI);
        }
        return csi;
    }

    @Test
    public void testCsiThatNamesEverySequenceOfTheHeaderInOrderIsAnsweredRightly() throws IOException {
        final Path samGz = writeSparseSamGz();
        indexWithCsi(samGz);
        assertAnswersAsTheBamDoes(samGz);
    }

    /** A CSI of the file, references numbered as the header numbers them, with the given bytes as its aux block. */
    private Path indexWithCsiCarrying(final Path samGz, final byte[] aux) throws IOException {
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 5, true);
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(samGz)) {
            for (final SAMRecord record : reader) {
                if (record.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                    break;
                }
                final Chunk chunk = ((BAMFileSpan) record.getFileSource().getFilePointer()).getSingleChunk();
                builder.add(
                        record.getReferenceIndex(),
                        record.getAlignmentStart(),
                        record.getAlignmentEnd(),
                        chunk.getChunkStart(),
                        chunk.getChunkEnd());
            }
        }
        final Path csi = samGz.resolveSibling(samGz.getFileName() + ".csi");
        IOUtil.deleteOnExit(csi);
        try (BinaryCodec codec =
                new BinaryCodec(new BlockCompressedOutputStream(Files.newOutputStream(csi), (Path) null))) {
            builder.build(sparseHeader.getSequenceDictionary().size()).writeCsi(codec, aux);
        }
        return csi;
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testCsiWhoseAuxBlockIsNotATabixHeaderIsRefused() throws IOException {
        final Path samGz = writeSparseSamGz();
        indexWithCsiCarrying(samGz, new byte[] {1, 2, 3});
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz)) {
            sam.queryOverlapping("chr2", 1, 1_000);
        }
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testFileNamedAsATabixIndexThatIsNotOneIsRefused() throws IOException {
        final Path samGz = writeSparseSamGz();
        final Path bareCsi = indexWithCsiCarrying(samGz, new byte[0]);
        Files.move(bareCsi, samGz.resolveSibling(samGz.getFileName() + ".tbi"));
        IOUtil.deleteOnExit(samGz.resolveSibling(samGz.getFileName() + ".tbi"));
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz)) {
            sam.queryOverlapping("chr2", 1, 1_000);
        }
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testTabixIndexListingASequenceTwiceIsRefused() throws IOException {
        final Path samGz = writeSparseSamGz();
        indexAsTabixWould(samGz, TabixIndexType.CSI, TabixFormat.SAM, name -> "chr2", false);
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz)) {
            sam.queryOverlapping("chr2", 1, 1_000);
        }
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testCsiWhoseTabixHeaderNamesFewerSequencesThanItIndexesIsRefused() throws IOException {
        final ByteArrayOutputStream csiOfOneSequence = new ByteArrayOutputStream();
        new TabixIndex(
                        TabixFormat.SAM,
                        List.of("chr2"),
                        new BinningIndex.Builder(14, 5, true).build(1),
                        TabixIndexType.CSI)
                .write(new LittleEndianOutputStream(csiOfOneSequence));
        final byte[] auxNamingOneSequence = BinningIndex.readCsi(
                        new BinaryCodec(new ByteArrayInputStream(csiOfOneSequence.toByteArray())))
                .aux();

        final Path samGz = writeSparseSamGz();
        indexWithCsiCarrying(samGz, auxNamingOneSequence);
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz)) {
            sam.queryOverlapping("chr2", 1, 1_000);
        }
    }

    @Test
    public void testIndexIsClosedEvenIfClosingTheFileFails() throws IOException {
        final BlockCompressedInputStream failsToClose = new BlockCompressedInputStream(samGzWithoutIndex) {
            @Override
            public void close() throws IOException {
                super.close();
                throw new IOException("injected failure");
            }
        };
        final int[] closes = {0};
        final SeekablePathStream index = new SeekablePathStream(samGzWithBai.resolveSibling("bai.sam.gz.bai")) {
            @Override
            public void close() throws IOException {
                closes[0]++;
                super.close();
            }
        };
        final SAMTextReader reader = new SAMTextReader(
                failsToClose,
                true,
                samGzWithoutIndex,
                null,
                index,
                ValidationStringency.DEFAULT_STRINGENCY,
                new DefaultSAMRecordFactory());

        Assert.assertThrows(RuntimeIOException.class, reader::close);
        Assert.assertEquals(closes[0], 1);
    }

    @Test
    public void testIteratorClosedASecondTimeDoesNotFreeTheReaderForAnother() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithBai)) {
            final SAMRecordIterator first = sam.iterator();
            first.close();
            try (SAMRecordIterator second = sam.iterator()) {
                first.close();
                Assert.assertThrows(IllegalStateException.class, sam::iterator);
                Assert.assertTrue(second.hasNext());
            }
        }
    }
}
