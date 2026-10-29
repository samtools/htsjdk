package htsjdk.samtools.util;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordSetBuilder;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Throughput of {@link AbstractAsyncWriter}: the time to write a fixed number of items through an asynchronous
 * writer and close it.
 *
 * <pre>
 * ./gradlew benchesJar
 * java -jar build/libs/htsjdk-*-benches.jar AsyncWriter
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 2, jvmArgsAppend = "-Xmx4g")
public class AsyncWriterBenchmark {

    private static final int ITEMS = 2_000_000;
    private static final Integer[] INTEGERS = new Integer[ITEMS];

    static {
        for (int i = 0; i < ITEMS; i++) {
            INTEGERS[i] = i;
        }
    }

    /** A writer whose writing thread only adds up what it is given, so that the hand-off itself is measured. */
    private static final class SummingAsyncWriter extends AbstractAsyncWriter<Integer> {
        long sum = 0;

        SummingAsyncWriter() {
            super(DEFAULT_QUEUE_SIZE);
        }

        @Override
        protected String getThreadNamePrefix() {
            return "SummingAsyncWriter";
        }

        @Override
        protected void synchronouslyWrite(final Integer item) {
            sum += item;
        }

        @Override
        protected void synchronouslyClose() {}
    }

    /** Hands two million items to a writing thread that does next to nothing with them. */
    @Benchmark
    public long handOff() {
        final SummingAsyncWriter writer = new SummingAsyncWriter();
        for (final Integer item : INTEGERS) {
            writer.write(item);
        }
        writer.close();
        return writer.sum;
    }

    /** Records for an asynchronous BAM writer, built once, before timing. */
    @State(Scope.Benchmark)
    public static class Records {
        SAMFileHeader header;
        List<SAMRecord> records;

        @Setup
        public void build() {
            final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
            for (int i = 0; i < 100_000; i++) {
                builder.addPair("r" + i, 0, 1 + i * 10, 1 + i * 10 + 200);
            }
            header = builder.getHeader();
            records = new ArrayList<>(builder.getRecords());
        }
    }

    /** An output stream that keeps only a count, so that writing costs nothing but the encoding and compression. */
    private static final class CountingSink extends OutputStream {
        long count;

        @Override
        public void write(final int b) {
            count++;
        }

        @Override
        public void write(final byte[] b, final int off, final int len) {
            count += len;
        }
    }

    /** Writes 200,000 records through an asynchronous BAM writer, whose writing thread encodes and compresses them. */
    @Benchmark
    public long asyncBamWriter(final Records records) {
        final CountingSink sink = new CountingSink();
        try (SAMFileWriter writer =
                new SAMFileWriterFactory().setUseAsyncIo(true).makeBAMWriter(records.header, true, sink)) {
            records.records.forEach(writer::addAlignment);
        }
        return sink.count;
    }
}
