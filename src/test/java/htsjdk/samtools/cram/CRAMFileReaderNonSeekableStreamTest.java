package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.CRAMFileWriter;
import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * A CRAMFileReader built over a plain, non-seekable InputStream cannot support query or index operations.
 * It should say so rather than failing with a NullPointerException.
 * See https://github.com/samtools/htsjdk/issues/426.
 */
public class CRAMFileReaderNonSeekableStreamTest extends HtsjdkTest {

    private static final String CONTIG = "1";
    private static final int REF_LENGTH = 200;

    private static byte[] referenceBases() {
        final byte[] bases = new byte[REF_LENGTH];
        final byte[] pattern = "ACGTACGTAC".getBytes();
        for (int i = 0; i < REF_LENGTH; i++) {
            bases[i] = pattern[i % pattern.length];
        }
        return bases;
    }

    private static ReferenceSource referenceSource() {
        final InMemoryReferenceSequenceFile referenceFile = new InMemoryReferenceSequenceFile();
        referenceFile.add(CONTIG, referenceBases());
        return new ReferenceSource(referenceFile);
    }

    private static SAMFileHeader makeHeader() {
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord(CONTIG, REF_LENGTH));
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        return header;
    }

    /** Writes a small indexed CRAM to disk and returns the CRAM bytes plus the index file. */
    private static java.util.AbstractMap.SimpleEntry<byte[], File> writeIndexedCram() throws IOException {
        final SAMFileHeader header = makeHeader();
        final java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("cram426");
        final java.nio.file.Path cram = dir.resolve("in.cram");
        final java.nio.file.Path index = dir.resolve("in.cram.bai");

        try (java.io.OutputStream cramOut = java.nio.file.Files.newOutputStream(cram);
                java.io.OutputStream indexOut = java.nio.file.Files.newOutputStream(index);
                CRAMFileWriter writer =
                        new CRAMFileWriter(cramOut, indexOut, true, referenceSource(), header, "test")) {
            final SAMRecord record = new SAMRecord(header);
            record.setReadName("r1");
            record.setReferenceIndex(0);
            record.setAlignmentStart(10);
            record.setCigarString("10M");
            record.setMappingQuality(30);
            record.setReadBases(Arrays.copyOfRange(referenceBases(), 9, 19));
            final byte[] qualities = new byte[10];
            Arrays.fill(qualities, (byte) 30);
            record.setBaseQualities(qualities);
            writer.addAlignment(record);
        }
        return new java.util.AbstractMap.SimpleEntry<>(java.nio.file.Files.readAllBytes(cram), index.toFile());
    }

    /**
     * Returns a reader built from an index plus a NON-seekable input stream -- the combination described in
     * the issue, where the reader believes it has an index but cannot seek in the data.
     */
    private static CRAMFileReader readerOverNonSeekableStream() throws IOException {
        final java.util.AbstractMap.SimpleEntry<byte[], File> cram = writeIndexedCram();
        // a plain ByteArrayInputStream is deliberately NOT a SeekableStream
        return new CRAMFileReader(
                new ByteArrayInputStream(cram.getKey()),
                cram.getValue(),
                referenceSource(),
                ValidationStringency.SILENT);
    }

    @Test
    public void testQueryOverNonSeekableStreamThrowsDescriptiveException() throws IOException {
        try (CRAMFileReader reader = readerOverNonSeekableStream()) {
            try {
                reader.query(new QueryInterval[] {new QueryInterval(0, 1, 100)}, false);
                Assert.fail("expected a descriptive exception when querying a non-seekable CRAM stream");
            } catch (final NullPointerException npe) {
                Assert.fail("query on a non-seekable stream still fails with a NullPointerException");
            } catch (final IllegalStateException expected) {
                Assert.assertTrue(
                        expected.getMessage().contains("SeekableStream"),
                        "the exception should explain that a SeekableStream is required, but was: "
                                + expected.getMessage());
            }
        }
    }

    /** Sequential iteration does not require seeking and must keep working. */
    @Test
    public void testSequentialIterationOverNonSeekableStreamStillWorks() throws IOException {
        try (CRAMFileReader reader = readerOverNonSeekableStream()) {
            int count = 0;
            final htsjdk.samtools.SAMRecordIterator iterator = reader.getIterator();
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
            Assert.assertEquals(count, 1, "sequential iteration should be unaffected by the seekability check");
        }
    }
}
