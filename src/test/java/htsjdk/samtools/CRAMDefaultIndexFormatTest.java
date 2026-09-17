package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAIQueryIndex;
import htsjdk.samtools.reference.FastaReferenceWriter;
import htsjdk.samtools.reference.FastaReferenceWriterBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Which index format {@link SAMFileWriterFactory} writes beside a CRAM. */
public class CRAMDefaultIndexFormatTest extends HtsjdkTest {

    private static final String SEQUENCE_NAME = "chr1";
    private static final int SEQUENCE_LENGTH = 10_000;

    private Path referenceFasta;

    /** Writes an indexed FASTA; an in-memory reference produces spurious CRAM failures. */
    @BeforeClass
    public void writeReference() throws IOException {
        final Path directory = Files.createTempDirectory("cramIndexFormatReference");
        directory.toFile().deleteOnExit();
        referenceFasta = directory.resolve("reference.fasta");

        final byte[] bases = new byte[SEQUENCE_LENGTH];
        Arrays.fill(bases, (byte) 'A');
        try (final FastaReferenceWriter writer = new FastaReferenceWriterBuilder()
                .setFastaFile(referenceFasta)
                .setMakeFaiOutput(true)
                .setMakeDictOutput(true)
                .build()) {
            writer.startSequence(SEQUENCE_NAME).appendBases(bases);
        }
    }

    private static SAMFileHeader header() {
        final SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        header.addSequence(new SAMSequenceRecord(SEQUENCE_NAME, SEQUENCE_LENGTH));
        return header;
    }

    /** Writes a small coordinate-sorted CRAM through the factory. */
    private Path writeCram(final SAMFileWriterFactory factory) throws IOException {
        final Path directory = Files.createTempDirectory("cramIndexFormat");
        directory.toFile().deleteOnExit();
        final Path cram = directory.resolve("test.cram");

        final SAMFileHeader header = header();
        try (final SAMFileWriter writer =
                factory.setCreateIndex(true).makeCRAMWriter(header, true, cram, referenceFasta)) {
            for (int i = 0; i < 200; i++) {
                final SAMRecord record = new SAMRecord(header);
                record.setReadName("read" + i);
                record.setReferenceName(SEQUENCE_NAME);
                record.setAlignmentStart(1 + i * 10);
                record.setCigarString("20M");
                record.setReadBases("AAAAAAAAAAAAAAAAAAAA".getBytes());
                record.setBaseQualities(new byte[20]);
                record.setMappingQuality(60);
                writer.addAlignment(record);
            }
        }
        return cram;
    }

    private static Path sibling(final Path cram, final String extension) {
        return cram.resolveSibling(cram.getFileName() + extension);
    }

    private SamReader open(final Path cram) {
        return SamReaderFactory.makeDefault()
                .referenceSequence(referenceFasta)
                .validationStringency(ValidationStringency.SILENT)
                .open(cram);
    }

    @Test
    public void testAnIndexedCramWritesACraiByDefault() throws IOException {
        final Path cram = writeCram(new SAMFileWriterFactory());
        Assert.assertTrue(Files.exists(sibling(cram, ".crai")), "Expected a .crai beside the CRAM");
        Assert.assertFalse(Files.exists(sibling(cram, ".bai")), "Did not expect a .bai beside the CRAM");
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testTheDeprecatedToggleWritesABaiInstead() throws IOException {
        final Path cram = writeCram(new SAMFileWriterFactory().setCreateBaiIndexForCram(true));
        Assert.assertTrue(Files.exists(sibling(cram, ".bai")), "Expected a .bai beside the CRAM");
        Assert.assertFalse(Files.exists(sibling(cram, ".crai")), "Did not expect a .crai beside the CRAM");
    }

    @Test
    public void testTheDefaultCraiIsQueriedNatively() throws IOException {
        final Path cram = writeCram(new SAMFileWriterFactory());
        try (final SamReader reader = open(cram)) {
            Assert.assertTrue(reader.hasIndex());
            Assert.assertTrue(reader.indexing().getHtsIndex() instanceof CRAIQueryIndex);
            try (final SAMRecordIterator iterator = reader.queryOverlapping(SEQUENCE_NAME, 1, 100)) {
                Assert.assertTrue(iterator.hasNext(), "Querying the start of the CRAM returned nothing");
            }
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testABaiWrittenByTheToggleIsStillUsableForQuerying() throws IOException {
        final Path cram = writeCram(new SAMFileWriterFactory().setCreateBaiIndexForCram(true));
        try (final SamReader reader = open(cram)) {
            Assert.assertTrue(reader.indexing().getHtsIndex() instanceof BAMIndex);
            try (final SAMRecordIterator iterator = reader.queryOverlapping(SEQUENCE_NAME, 1, 100)) {
                Assert.assertTrue(iterator.hasNext(), "Querying the start of the CRAM returned nothing");
            }
        }
    }

    @Test
    public void testBothIndexFormatsReturnTheSameRecords() throws IOException {
        final Path craiIndexed = writeCram(new SAMFileWriterFactory());
        @SuppressWarnings("deprecation")
        final Path baiIndexed = writeCram(new SAMFileWriterFactory().setCreateBaiIndexForCram(true));

        try (final SamReader craiReader = open(craiIndexed);
                final SamReader baiReader = open(baiIndexed)) {
            Assert.assertEquals(
                    readNames(baiReader.queryOverlapping(SEQUENCE_NAME, 500, 1500)),
                    readNames(craiReader.queryOverlapping(SEQUENCE_NAME, 500, 1500)));
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testTheToggleSurvivesTheCopyConstructor() {
        final SAMFileWriterFactory original = new SAMFileWriterFactory().setCreateBaiIndexForCram(true);
        Assert.assertTrue(new SAMFileWriterFactory(original).toString().contains("createBaiIndexForCram=true"));
    }

    private static java.util.List<String> readNames(final SAMRecordIterator iterator) {
        final java.util.List<String> names = new java.util.ArrayList<>();
        try (final SAMRecordIterator closeable = iterator) {
            while (closeable.hasNext()) {
                names.add(closeable.next().getReadName());
            }
        }
        return names;
    }
}
