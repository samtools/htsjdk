package htsjdk.samtools.fastq;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.util.IOUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BasicFastqWriterTest extends HtsjdkTest {
    public class OutputStreamWrapper extends ByteArrayOutputStream {
        public int flushCalled = 0;
        public int writeCalled = 0;

        @Override
        public void flush() throws IOException {
            flushCalled++;
            super.flush();
        }

        @Override
        public void write(byte[] b) throws IOException {
            writeCalled++;
            super.write(b);
        }
    }

    /**
     * #1497
     */
    @Test
    public void testFlushNotSpammed() {
        OutputStreamWrapper loggedStream = new OutputStreamWrapper();
        PrintStream ps = new PrintStream(loggedStream);
        try (BasicFastqWriter fqw = new BasicFastqWriter(ps)) {
            for (int i = 0; i < 10000; i++) {
                fqw.write(new FastqRecord("name", "NNNN", null, "...."));
            }
        }
        // flush()/close() results in two flushes
        Assert.assertTrue(
                loggedStream.flushCalled <= 5, "flush called " + loggedStream.flushCalled + " times which is > 5");
    }

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

    /** Writes the records to a temporary FASTQ with a writer from {@link FastqWriterFactory} and returns its lines. */
    private static List<String> writeAndReadLines(final FastqRecord... records) throws IOException {
        final Path fastq = Files.createTempFile("BasicFastqWriterTest.", ".fastq");
        IOUtil.deleteOnExit(fastq);
        try (FastqWriter writer = new FastqWriterFactory().newWriter(fastq)) {
            for (final FastqRecord record : records) {
                writer.write(record);
            }
        }
        return IOUtil.slurpLines(fastq);
    }

    @Test
    public void writesFourLinesPerRecord() throws IOException {
        final Random random = new Random(42);
        final FastqRecord q1 = randomRecord(random, "q1", 50);
        final FastqRecord q2 = randomRecord(random, "q2", 48);
        final FastqRecord q3 = randomRecord(random, "q3", 55);

        final List<String> lines = writeAndReadLines(q1, q2, q3);

        Assert.assertEquals(lines.size(), 12);
        Assert.assertEquals(lines.get(0), "@q1");
        Assert.assertEquals(lines.get(1), q1.getReadString());
        Assert.assertEquals(lines.get(4), "@q2");
        Assert.assertEquals(lines.get(5), q2.getReadString());
        Assert.assertEquals(lines.get(8), "@q3");
        Assert.assertEquals(lines.get(9), q3.getReadString());
    }

    @Test
    public void writesARecordWithASingleBase() throws IOException {
        final List<String> lines = writeAndReadLines(randomRecord(new Random(42), "q1", 1));
        Assert.assertEquals(lines.get(1).length(), 1);
        Assert.assertEquals(lines.get(3).length(), 1);
    }

    @Test
    public void writesARecordWithNoBasesOrQualities() throws IOException {
        final List<String> lines = writeAndReadLines(randomRecord(new Random(42), "q1", 0));
        Assert.assertEquals(lines.get(1).length(), 0);
        Assert.assertEquals(lines.get(3).length(), 0);
    }
}
