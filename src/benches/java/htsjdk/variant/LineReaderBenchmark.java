package htsjdk.variant;

import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.tribble.readers.AsciiLineReader;
import htsjdk.tribble.readers.LineReader;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.tribble.readers.SynchronousLineReader;
import htsjdk.tribble.readers.TabixReader;
import htsjdk.variant.VariantCodecBenchmark.Shape;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Throughput of the line readers beneath the VCF codec: the time to read every line of one whole file, per file
 * {@link Shape}.
 *
 * <p>{@code VCFFileReader} reads through {@link SynchronousLineReader}, so {@link VariantCodecBenchmark#vcfRead}
 * measures only that one. Index building reads through {@link AsciiLineReader} (over a bgzipped file, through
 * {@link BlockCompressedInputStream#readLine}) and a tabix query through {@link TabixReader#readLine}; those are
 * measured here.
 *
 * <pre>
 * ./gradlew benchesJar
 * java -jar build/libs/htsjdk-*-benches.jar LineReader                              # everything
 * java -jar build/libs/htsjdk-*-benches.jar LineReader.asciiLineReader -p shape=WIDE
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 2, jvmArgsAppend = "-Xmx8g")
public class LineReaderBenchmark {

    /**
     * The VCF to read: the bgzipped file, and its bytes decompressed once so that the in-memory cases measure the
     * reader alone.
     */
    @State(Scope.Benchmark)
    public static class Input {
        @Param({"SINGLE", "WIDE"})
        public Shape shape;

        @Param("build/bench-inputs")
        public String inputDir;

        Path vcfGz;
        byte[] uncompressed;

        @Setup
        public void load() throws IOException {
            vcfGz = shape.vcfIn(Paths.get(inputDir));
            try (final BlockCompressedInputStream in = new BlockCompressedInputStream(vcfGz)) {
                uncompressed = in.readAllBytes();
            }
        }
    }

    /** What index building reads through, over the decompressed bytes. */
    @Benchmark
    public long asciiLineReader(final Input input) throws IOException {
        final PositionalBufferedStream stream =
                new PositionalBufferedStream(new ByteArrayInputStream(input.uncompressed));
        return sumOfLineLengths(AsciiLineReader.from(stream));
    }

    /** What {@code VCFFileReader} reads through, over the decompressed bytes. */
    @Benchmark
    public long synchronousLineReader(final Input input) throws IOException {
        return sumOfLineLengths(new SynchronousLineReader(new ByteArrayInputStream(input.uncompressed)));
    }

    /** What index building reads through over a bgzipped file; the time includes decompression. */
    @Benchmark
    public long blockCompressedReadLine(final Input input) throws IOException {
        long total = 0;
        try (final BlockCompressedInputStream in = new BlockCompressedInputStream(input.vcfGz)) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                total += line.length();
            }
        }
        return total;
    }

    /** What a tabix query reads through, over a bgzipped file; the time includes decompression. */
    @Benchmark
    public long tabixReadLine(final Input input) throws IOException {
        long total = 0;
        try (final BlockCompressedInputStream in = new BlockCompressedInputStream(input.vcfGz)) {
            for (String line = TabixReader.readLine(in); line != null; line = TabixReader.readLine(in)) {
                total += line.length();
            }
        }
        return total;
    }

    /** Sums the lines' lengths, so that every decoded line is used and none is optimised away. */
    private static long sumOfLineLengths(final LineReader reader) throws IOException {
        long total = 0;
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            total += line.length();
        }
        return total;
    }
}
