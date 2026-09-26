package htsjdk.samtools.fastq;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.util.IOUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.Test;

public class FastqReaderTest extends HtsjdkTest {

    /** One record preceded by two blank lines and followed by a line of spaces. */
    private static final String FASTQ_WITH_BLANK_LINES = String.join(
            "\n", "", "", "@SL-XBG:1:1:4:1663#0/2", "N".repeat(76), "+SL-XBG:1:1:4:1663#0/2", "B".repeat(76), "      ");

    /** A record of random bases of the given length, all at quality 30. */
    private static FastqRecord randomRecord(final Random random, final String name, final int length) {
        final char[] bases = new char[length];
        for (int i = 0; i < length; i++) {
            bases[i] = "ACGT".charAt(random.nextInt(4));
        }
        return new FastqRecord(
                name,
                new String(bases),
                "",
                String.valueOf(SAMUtils.phredToFastq(30)).repeat(length));
    }

    /** Reads every record from the given FASTQ text. */
    private static List<FastqRecord> readAll(final String fastq, final boolean skipBlankLines) {
        final List<FastqRecord> records = new ArrayList<>();
        try (FastqReader reader = new FastqReader(null, new BufferedReader(new StringReader(fastq)), skipBlankLines)) {
            reader.forEachRemaining(records::add);
        }
        return records;
    }

    private static List<FastqRecord> readAll(final String fastq) {
        return readAll(fastq, false);
    }

    @Test
    public void readsBackRecordsWrittenByFastqWriter() throws IOException {
        final Random random = new Random(42);
        final List<FastqRecord> records = List.of(
                randomRecord(random, "q1", 50), randomRecord(random, "q2", 100), randomRecord(random, "q3", 150));
        final Path fastq = Files.createTempFile("FastqReaderTest.", ".fastq");
        IOUtil.deleteOnExit(fastq);
        try (FastqWriter writer = new FastqWriterFactory().newWriter(fastq)) {
            records.forEach(writer::write);
        }

        final List<FastqRecord> readBack = new ArrayList<>();
        try (FastqReader reader = new FastqReader(fastq)) {
            reader.forEachRemaining(readBack::add);
        }
        Assert.assertEquals(readBack, records);
    }

    @Test(expectedExceptions = SAMException.class)
    public void throwsWhenARecordHasNoQualityHeaderLine() {
        readAll("@q1\nAACCGGTT\n+\n########\n@q2\nACGT\n####");
    }

    @Test(expectedExceptions = SAMException.class)
    public void throwsWhenTheFileDoesNotExist() throws IOException {
        final Path dir = Files.createTempDirectory("FastqReaderTest.");
        try {
            new FastqReader(dir.resolve("missing.fq")).close();
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void readsNoRecordsFromAnEmptyFile() throws IOException {
        final Path fastq = Files.createTempFile("FastqReaderTest.", ".fastq");
        IOUtil.deleteOnExit(fastq);
        try (FastqReader reader = new FastqReader(fastq)) {
            Assert.assertFalse(reader.hasNext());
        }
    }

    @Test(expectedExceptions = NoSuchElementException.class)
    public void nextThrowsWhenThereAreNoMoreRecords() throws IOException {
        final Path fastq = Files.createTempFile("FastqReaderTest.", ".fastq");
        IOUtil.deleteOnExit(fastq);
        try (FastqReader reader = new FastqReader(fastq)) {
            reader.next();
        }
    }

    @Test
    public void skipsBlankLinesWhenAskedTo() {
        final List<FastqRecord> records = readAll(FASTQ_WITH_BLANK_LINES, true);
        Assert.assertEquals(records.size(), 1);
        Assert.assertEquals(records.get(0).getReadName(), "SL-XBG:1:1:4:1663#0/2");
    }

    @Test(expectedExceptions = SAMException.class)
    public void throwsOnABlankLineWhenNotSkippingBlankLines() {
        readAll(FASTQ_WITH_BLANK_LINES, false);
    }

    @Test(expectedExceptions = SAMException.class)
    public void throwsWhenTheFileEndsAfterTheSequenceHeader() {
        readAll("@q1");
    }

    @Test(expectedExceptions = SAMException.class)
    public void throwsWhenTheFileEndsAfterTheSequenceLine() {
        readAll("@q1\nAACCGGTT");
    }

    @Test(expectedExceptions = SAMException.class)
    public void throwsWhenTheFileEndsAfterTheQualityHeader() {
        readAll("@q1\nAACCGGTT\n+");
    }

    @Test(expectedExceptions = SAMException.class)
    public void throwsWhenTheSequenceAndQualityLinesDifferInLength() {
        readAll("@q1\nAACC\n+\n########");
    }

    @Test(expectedExceptions = SAMException.class)
    public void throwsWhenTheSequenceHeaderLineIsEmpty() {
        readAll("\nAACC\n+q1\n####");
    }

    @Test(expectedExceptions = SAMException.class)
    public void throwsWhenTheQualityHeaderLineIsEmpty() {
        readAll("@q1\nAACC\n\n####");
    }
}
