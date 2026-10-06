package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.ref.CRAMLazyReferenceSource;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.FastaSequenceIndexCreator;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.SequenceUtil;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CRAMContainerStreamWriterTest extends HtsjdkTest {

    static final int SEQUENCE_LENGTH = 1024 * 1024;

    private List<SAMRecord> createRecords(int count) {
        final List<SAMRecord> list = new ArrayList<>(count);
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
        if (builder.getHeader().getReadGroups().isEmpty()) {
            throw new IllegalStateException("Read group expected in the header");
        }

        int posInRef = 1;
        for (int i = 0; i < count / 2; i++) {
            builder.addPair(Integer.toString(i), i % 2, posInRef += 1, posInRef += 3);
        }
        list.addAll(builder.getRecords());

        // Add NM/MD tags to match what CRAM decode will regenerate
        final ReferenceSource refSource = createReferenceSource();
        for (final SAMRecord rec : list) {
            if (!rec.getReadUnmappedFlag() && rec.getReferenceIndex() >= 0) {
                final byte[] refBases =
                        refSource.getReferenceBases(rec.getHeader().getSequence(rec.getReferenceIndex()), false);
                if (refBases != null) {
                    SequenceUtil.calculateMdAndNmTags(rec, refBases, true, true);
                }
            }
        }

        Collections.sort(list, new SAMRecordCoordinateComparator());

        return list;
    }

    private SAMFileHeader createSAMHeader(SAMFileHeader.SortOrder sortOrder) {
        final SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(sortOrder);
        header.addSequence(new SAMSequenceRecord("chr1", SEQUENCE_LENGTH));
        header.addSequence(new SAMSequenceRecord("chr2", SEQUENCE_LENGTH));
        SAMReadGroupRecord readGroupRecord = new SAMReadGroupRecord("1");
        header.addReadGroup(readGroupRecord);
        return header;
    }

    private ReferenceSource createReferenceSource() {
        final byte[] refBases = new byte[SEQUENCE_LENGTH];
        Arrays.fill(refBases, (byte) 'A');
        InMemoryReferenceSequenceFile rsf = new InMemoryReferenceSequenceFile();
        rsf.add("chr1", refBases);
        rsf.add("chr2", refBases);
        return new ReferenceSource(rsf);
    }

    private void doTest(
            final List<SAMRecord> samRecords, final ByteArrayOutputStream outStream, final OutputStream indexStream) {
        final SAMFileHeader header = createSAMHeader(SAMFileHeader.SortOrder.coordinate);
        final ReferenceSource refSource = createReferenceSource();

        final CRAMContainerStreamWriter containerStream =
                new CRAMContainerStreamWriter(outStream, indexStream, refSource, header, "test");
        containerStream.writeHeader();

        writeThenReadRecords(samRecords, outStream, refSource, containerStream);
    }

    private void doTestWithIndexer(
            final List<SAMRecord> samRecords,
            final ByteArrayOutputStream outStream,
            final SAMFileHeader header,
            final CRAMIndexer indexer) {
        final ReferenceSource refSource = createReferenceSource();

        final CRAMContainerStreamWriter containerStream =
                new CRAMContainerStreamWriter(outStream, refSource, header, "test", indexer);
        containerStream.writeHeader();

        writeThenReadRecords(samRecords, outStream, refSource, containerStream);
    }

    private void writeThenReadRecords(
            List<SAMRecord> samRecords,
            ByteArrayOutputStream outStream,
            ReferenceSource refSource,
            CRAMContainerStreamWriter containerStream) {
        for (SAMRecord record : samRecords) {
            containerStream.writeAlignment(record);
        }
        containerStream.finish(true); // finish and issue EOF

        // read all the records back in
        final CRAMFileReader cReader =
                new CRAMFileReader((Path) null, new ByteArrayInputStream(outStream.toByteArray()), refSource);
        final SAMRecordIterator iterator = cReader.getIterator();
        int count = 0;
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        Assert.assertEquals(count, samRecords.size());
    }

    @Test(description = "Test CRAMContainerStream no index")
    public void testCRAMContainerStreamNoIndex() {
        final List<SAMRecord> samRecords = createRecords(100);
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        doTest(samRecords, outStream, null);
    }

    @Test(description = "Test CRAMContainerStream aggregating multiple partitions")
    public void testCRAMContainerAggregatePartitions() throws IOException {
        final SAMFileHeader header = createSAMHeader(SAMFileHeader.SortOrder.coordinate);
        final ReferenceSource refSource = createReferenceSource();

        // create a bunch of records and write them out to separate streams in groups
        final int nRecs = 100;
        final int recsPerPartition = 20;
        final int nPartitions = nRecs / recsPerPartition;

        final List<SAMRecord> samRecords = createRecords(nRecs);
        final ArrayList<ByteArrayOutputStream> byteStreamArray = new ArrayList<>(nPartitions);

        for (int partition = 0, recNum = 0; partition < nPartitions; partition++) {
            byteStreamArray.add(partition, new ByteArrayOutputStream());
            final CRAMContainerStreamWriter containerStream =
                    new CRAMContainerStreamWriter(byteStreamArray.get(partition), null, refSource, header, "test");

            // don't write a header for the intermediate streams
            for (int i = 0; i < recsPerPartition; i++) {
                containerStream.writeAlignment(samRecords.get(recNum++));
            }
            containerStream.finish(false); // finish but don't issue EOF container
        }

        // now create the final aggregate file by concatenating the individual streams, but this
        // time with a CRAM and SAM header at the front and an EOF container at the end
        final ByteArrayOutputStream aggregateStream = new ByteArrayOutputStream();
        final CRAMContainerStreamWriter aggregateContainerStreamWriter =
                new CRAMContainerStreamWriter(aggregateStream, null, refSource, header, "test");
        aggregateContainerStreamWriter.writeHeader(); // write out one CRAM and SAM header
        for (int j = 0; j < nPartitions; j++) {
            byteStreamArray.get(j).writeTo(aggregateStream);
        }
        aggregateContainerStreamWriter.finish(true); // write out the EOF container

        // now iterate through all the records in the aggregate file
        final CRAMFileReader cReader =
                new CRAMFileReader((Path) null, new ByteArrayInputStream(aggregateStream.toByteArray()), refSource);
        final SAMRecordIterator iterator = cReader.getIterator();
        int count = 0;
        while (iterator.hasNext()) {
            Assert.assertEquals(
                    iterator.next().toString(), samRecords.get(count).toString());
            count++;
        }
        Assert.assertEquals(count, nRecs);
    }

    @Test(description = "Test CRAMContainerStream with bai index")
    public void testCRAMContainerStreamWithBaiIndex() throws IOException {
        final List<SAMRecord> samRecords = createRecords(100);
        try (ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                ByteArrayOutputStream indexStream = new ByteArrayOutputStream()) {
            doTest(samRecords, outStream, indexStream);
            outStream.flush();
            indexStream.flush();
            checkCRAMContainerStream(outStream, indexStream, ".bai");
        }
    }

    @Test(description = "Test CRAMContainerStream with crai index")
    public void testCRAMContainerStreamWithCraiIndex() throws IOException {
        final List<SAMRecord> samRecords = createRecords(100);
        final SAMFileHeader header = createSAMHeader(SAMFileHeader.SortOrder.coordinate);
        try (ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                ByteArrayOutputStream indexStream = new ByteArrayOutputStream()) {
            doTestWithIndexer(samRecords, outStream, header, new CRAMCRAIIndexer(indexStream, header));
            outStream.flush();
            indexStream.flush();
            checkCRAMContainerStream(outStream, indexStream, ".crai");
        }
    }

    private void checkCRAMContainerStream(
            ByteArrayOutputStream outStream, ByteArrayOutputStream indexStream, String indexExtension)
            throws IOException {
        // write the file out
        final Path cramTempFile = Files.createTempFile("cramContainerStreamTest", ".cram");
        cramTempFile.toFile().deleteOnExit();
        Files.write(cramTempFile, outStream.toByteArray());

        // write the index out
        final Path indexTempFile = Files.createTempFile("cramContainerStreamTest", indexExtension);
        indexTempFile.toFile().deleteOnExit();
        Files.write(indexTempFile, indexStream.toByteArray());

        final ReferenceSource refSource = createReferenceSource();
        final CRAMFileReader reader =
                new CRAMFileReader(cramTempFile, indexTempFile, refSource, ValidationStringency.SILENT);
        final CloseableIterator<SAMRecord> iterator =
                reader.query(new QueryInterval[] {new QueryInterval(1, 10, 10)}, false);
        int count = 0;
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        Assert.assertEquals(count, 2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testStringTagWithCharAboveFFIsRejected() {
        final SAMRecord record = createRecords(2).get(0);
        record.setAttribute("XS", "value\u0109");
        final CRAMContainerStreamWriter containerStream = new CRAMContainerStreamWriter(
                new ByteArrayOutputStream(),
                null,
                createReferenceSource(),
                createSAMHeader(SAMFileHeader.SortOrder.coordinate),
                "test");
        containerStream.writeHeader();
        containerStream.writeAlignment(record);
    }

    private static final String CHR1_BASES = "acgtacgtnnacgtRYacgt";
    private static final String CHR2_BASES = "ggggccccaaaatttt";
    private static final String CHR3_BASES = "tttttttt";

    /** Writes a lower-case FASTA of chr1 to chr3 with a .fai, and returns its path. */
    private static Path writeFasta(final Path dir) throws IOException {
        final Path fasta = dir.resolve("ref.fa");
        Files.writeString(fasta, ">chr1\n" + CHR1_BASES + "\n>chr2\n" + CHR2_BASES + "\n>chr3\n" + CHR3_BASES + "\n");
        FastaSequenceIndexCreator.create(fasta, true);
        return fasta;
    }

    private static void writeDictionary(final Path dir, final SAMSequenceRecord... records) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(dir.resolve("ref.dict"))) {
            new SAMSequenceDictionaryCodec(writer).encode(new SAMSequenceDictionary(Arrays.asList(records)));
        }
    }

    private static SAMFileHeader headerWithoutM5s() {
        final SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.unsorted);
        header.addSequence(new SAMSequenceRecord("chr1", CHR1_BASES.length()));
        header.addSequence(new SAMSequenceRecord("chr2", CHR2_BASES.length()));
        header.addSequence(new SAMSequenceRecord("chr3", CHR3_BASES.length()));
        return header;
    }

    private static String md5OfUpperCased(final String bases) {
        return SequenceUtil.calculateMD5String(bases.toUpperCase().getBytes(StandardCharsets.US_ASCII));
    }

    /** Writes one unmapped read with the header and reference source, and returns the header read back from the CRAM. */
    private static SAMFileHeader writeAndReadBackHeader(final SAMFileHeader header, final CRAMReferenceSource source) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final CRAMContainerStreamWriter writer = new CRAMContainerStreamWriter(out, null, source, header, "test");
        writer.writeHeader();
        final SAMRecord read = new SAMRecord(header);
        read.setReadName("unmapped");
        read.setReadUnmappedFlag(true);
        read.setReadString("ACGT");
        read.setBaseQualityString("????");
        writer.writeAlignment(read);
        writer.finish(true);
        try (SamReader reader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .open(SamInputResource.of(new ByteArrayInputStream(out.toByteArray())))) {
            return reader.getFileHeader();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void missingM5sAreComputedFromTheReference() throws IOException {
        final Path dir = Files.createTempDirectory("cramM5Test");
        try {
            final SAMFileHeader written =
                    writeAndReadBackHeader(headerWithoutM5s(), new ReferenceSource(writeFasta(dir)));
            Assert.assertEquals(written.getSequence("chr1").getMd5(), md5OfUpperCased(CHR1_BASES));
            Assert.assertEquals(written.getSequence("chr2").getMd5(), md5OfUpperCased(CHR2_BASES));
            Assert.assertEquals(written.getSequence("chr3").getMd5(), md5OfUpperCased(CHR3_BASES));
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void m5sAreTakenFromTheReferenceDictionary() throws IOException {
        final Path dir = Files.createTempDirectory("cramM5Test");
        try {
            final Path fasta = writeFasta(dir);
            writeDictionary(
                    dir,
                    new SAMSequenceRecord("chr1", CHR1_BASES.length()).setMd5("a".repeat(32)),
                    new SAMSequenceRecord("chr2", CHR2_BASES.length()),
                    new SAMSequenceRecord("chr3", CHR3_BASES.length()));
            final SAMFileHeader written = writeAndReadBackHeader(headerWithoutM5s(), new ReferenceSource(fasta));
            Assert.assertEquals(written.getSequence("chr1").getMd5(), "a".repeat(32));
            Assert.assertEquals(written.getSequence("chr2").getMd5(), md5OfUpperCased(CHR2_BASES));
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void aReferenceDictionaryM5ForADifferentLengthIsNotUsed() throws IOException {
        final Path dir = Files.createTempDirectory("cramM5Test");
        try {
            final Path fasta = writeFasta(dir);
            writeDictionary(
                    dir,
                    new SAMSequenceRecord("chr1", CHR1_BASES.length()).setMd5("a".repeat(32)),
                    new SAMSequenceRecord("chr2", CHR2_BASES.length()),
                    new SAMSequenceRecord("chr3", CHR3_BASES.length()));
            final SAMFileHeader header = new SAMFileHeader();
            header.addSequence(new SAMSequenceRecord("chr1", CHR1_BASES.length() + 1));
            final SAMFileHeader written = writeAndReadBackHeader(header, new ReferenceSource(fasta));
            Assert.assertEquals(written.getSequence("chr1").getMd5(), md5OfUpperCased(CHR1_BASES));
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void existingM5sAreKept() throws IOException {
        final Path dir = Files.createTempDirectory("cramM5Test");
        try {
            final SAMFileHeader header = headerWithoutM5s();
            header.getSequence("chr1").setMd5("b".repeat(32));
            final SAMFileHeader written = writeAndReadBackHeader(header, new ReferenceSource(writeFasta(dir)));
            Assert.assertEquals(written.getSequence("chr1").getMd5(), "b".repeat(32));
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void aContigMissingFromTheReferenceIsLeftWithoutM5() throws IOException {
        final Path dir = Files.createTempDirectory("cramM5Test");
        try {
            final SAMFileHeader header = headerWithoutM5s();
            header.addSequence(new SAMSequenceRecord("chrNotInReference", 100));
            final SAMFileHeader written = writeAndReadBackHeader(header, new ReferenceSource(writeFasta(dir)));
            Assert.assertNull(written.getSequence("chrNotInReference").getMd5());
            Assert.assertEquals(written.getSequence("chr1").getMd5(), md5OfUpperCased(CHR1_BASES));
            Assert.assertEquals(written.getSequence("chr3").getMd5(), md5OfUpperCased(CHR3_BASES));
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void theCallersHeaderIsNotModified() throws IOException {
        final Path dir = Files.createTempDirectory("cramM5Test");
        try {
            final SAMFileHeader header = headerWithoutM5s();
            writeAndReadBackHeader(header, new ReferenceSource(writeFasta(dir)));
            for (final SAMSequenceRecord sequence :
                    header.getSequenceDictionary().getSequences()) {
                Assert.assertNull(sequence.getMd5());
            }
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void noReferenceWritesTheHeaderAsGiven() {
        final SAMFileHeader written = writeAndReadBackHeader(headerWithoutM5s(), new CRAMLazyReferenceSource());
        for (final SAMSequenceRecord sequence : written.getSequenceDictionary().getSequences()) {
            Assert.assertNull(sequence.getMd5());
        }
    }

    /** The bytes of a CRAM holding one unmapped read, written with the given output identifier. */
    private static byte[] writeOneUnmappedRead(final String outputIdentifier) {
        final SAMFileHeader header = headerWithoutM5s();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final CRAMContainerStreamWriter writer =
                new CRAMContainerStreamWriter(out, null, new CRAMLazyReferenceSource(), header, outputIdentifier);
        writer.writeHeader();
        final SAMRecord read = new SAMRecord(header);
        read.setReadName("unmapped");
        read.setReadUnmappedFlag(true);
        read.setReadString("ACGT");
        read.setBaseQualityString("????");
        writer.writeAlignment(read);
        writer.finish(true);
        return out.toByteArray();
    }

    @Test
    public void theFileIdOfAFileUriIsItsFileName() {
        Assert.assertEquals(CRAMContainerStreamWriter.fileNameOf("file:///data/run1/sample.cram"), "sample.cram");
    }

    @Test
    public void theFileIdOfAPercentEncodedFileUriIsItsDecodedFileName() {
        Assert.assertEquals(CRAMContainerStreamWriter.fileNameOf("file:///data/my%20sample.cram"), "my sample.cram");
    }

    @Test
    public void theFileIdOfAPlainPathIsItsFileName() {
        Assert.assertEquals(CRAMContainerStreamWriter.fileNameOf("/data/run 1/sample.cram"), "sample.cram");
    }

    @Test
    public void aPlainPathKeepsAHashQuestionMarkOrPercentInItsFileName() {
        Assert.assertEquals(CRAMContainerStreamWriter.fileNameOf("/data/run#1/s#2.cram"), "s#2.cram");
        Assert.assertEquals(CRAMContainerStreamWriter.fileNameOf("/data/what?.cram"), "what?.cram");
        Assert.assertEquals(CRAMContainerStreamWriter.fileNameOf("/data/my%20s.cram"), "my%20s.cram");
    }

    @Test
    public void theFileIdOfAWindowsPathIsItsFileName() {
        Assert.assertEquals(CRAMContainerStreamWriter.fileNameOf("C:\\data\\run1\\sample.cram"), "sample.cram");
    }

    @Test
    public void anIdentifierWithoutASlashIsTheFileIdAsItIs() {
        Assert.assertEquals(CRAMContainerStreamWriter.fileNameOf("test"), "test");
        Assert.assertNull(CRAMContainerStreamWriter.fileNameOf(null));
    }

    @Test
    public void theSameRecordsWrittenToDifferentDirectoriesGiveIdenticalFiles() {
        final byte[] first = writeOneUnmappedRead("file:///data/run1/sample.cram");
        final byte[] second = writeOneUnmappedRead("file:///scratch/work/a1b2c3/sample.cram");
        Assert.assertEquals(first, second);
        final byte[] id = CramIO.readCramHeader(new ByteArrayInputStream(first)).getId();
        Assert.assertEquals(new String(id, StandardCharsets.UTF_8).replace("\0", ""), "sample.cram");
    }

    @Test
    public void aReferenceWithNoContigsLeavesAllM5sAbsent() {
        final SAMFileHeader written = writeAndReadBackHeader(headerWithoutM5s(), new ReferenceSource((Path) null));
        for (final SAMSequenceRecord sequence : written.getSequenceDictionary().getSequences()) {
            Assert.assertNull(sequence.getMd5());
        }
    }
}
