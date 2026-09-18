package htsjdk.variant;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Throughput of the VCF and BCF codecs: the time to read or write one whole file, per file {@link Shape}.
 *
 * <pre>
 * ./gradlew benchesJar
 * java -jar build/libs/htsjdk-*-benches.jar VariantCodec                     # everything
 * java -jar build/libs/htsjdk-*-benches.jar VariantCodec.bcfWrite -p shape=WIDE
 * </pre>
 *
 * <p>The VCF inputs are generated on first use and kept in {@code inputDir}, so that two builds being compared read
 * the same bytes. The BCF input is written afresh by the build under test, because what it reads should be what it
 * writes.
 *
 * <p>Iterations within one JVM agree to within a percent, but separate JVMs running the same build differ by several
 * percent, and by around ten on the BCF read paths. To compare two builds, raise the fork count ({@code -f 5}) rather
 * than the iteration count, and alternate the two jars.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 2, jvmArgsAppend = "-Xmx8g")
public class VariantCodecBenchmark {

    /** The kinds of file the codecs are measured on. */
    public enum Shape {
        /** Many records, one sample. */
        SINGLE(1_000_000, 1, false),
        /** Few records, many samples: dominated by genotypes. Few, because the write benchmarks hold them all decoded. */
        WIDE(3_000, 1_000, false),
        /** One sample, mostly {@code <NON_REF>} reference blocks. */
        GVCF(1_000_000, 1, true);

        private static final long SEED = 42;
        private final int records;
        private final int samples;
        private final boolean gvcf;

        Shape(final int records, final int samples, final boolean gvcf) {
            this.records = records;
            this.samples = samples;
            this.gvcf = gvcf;
        }

        /** The generated VCF of this shape in {@code dir}, written first if it is not there yet. */
        Path vcfIn(final Path dir) throws IOException {
            final Path vcf = dir.resolve(String.format("%s-%dx%d-seed%d.vcf.gz", name(), records, samples, SEED));
            if (!Files.exists(vcf)) {
                Files.createDirectories(dir);
                // written under another name first so that an interrupted run cannot leave a truncated input behind
                final Path partial = dir.resolve("partial-" + vcf.getFileName());
                SyntheticVariants.generateFile(partial, records, samples, gvcf, SEED);
                Files.move(partial, vcf, StandardCopyOption.ATOMIC_MOVE);
            }
            return vcf;
        }
    }

    /** The VCF to read. */
    @State(Scope.Benchmark)
    public static class VcfInput {
        @Param
        public Shape shape;

        @Param("build/bench-inputs")
        public String inputDir;

        Path vcf;

        @Setup
        public void generate() throws IOException {
            vcf = shape.vcfIn(Paths.get(inputDir));
        }
    }

    /**
     * A file's records with their genotypes decoded, which is what a tool that has looked at them hands to a writer.
     * Undecoded genotypes would take the writers' pass-through shortcut and measure nothing. Each writer is given
     * records read from its own format, since the two readers leave attribute values in different forms.
     */
    static class DecodedRecords {
        VCFHeader header;
        final List<VariantContext> records = new ArrayList<>();

        void load(final Path input) {
            try (final VCFFileReader reader = new VCFFileReader(input, false);
                    final CloseableIterator<VariantContext> iterator = reader.iterator()) {
                header = reader.getFileHeader();
                while (iterator.hasNext()) {
                    records.add(decodeGenotypes(iterator.next()));
                }
            }
        }
    }

    @State(Scope.Benchmark)
    public static class DecodedVcfRecords extends DecodedRecords {
        @Setup
        public void load(final VcfInput input) {
            load(input.vcf);
        }
    }

    @State(Scope.Benchmark)
    public static class DecodedBcfRecords extends DecodedRecords {
        @Setup
        public void load(final BcfInput input) {
            load(input.bcf);
        }
    }

    /** The same records as BCF, as the build under test writes them. */
    @State(Scope.Benchmark)
    public static class BcfInput {
        Path bcf;

        @Setup
        public void write(final VcfInput input) throws IOException {
            bcf = Files.createTempFile("VariantCodecBenchmark", ".bcf");
            try (final VCFFileReader reader = new VCFFileReader(input.vcf, false);
                    final CloseableIterator<VariantContext> records = reader.iterator();
                    final VariantContextWriter writer = new VariantContextWriterBuilder()
                            .clearOptions()
                            .setOutputPath(bcf)
                            .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                            .build()) {
                writer.writeHeader(reader.getFileHeader());
                while (records.hasNext()) {
                    writer.add(decodeGenotypes(records.next()));
                }
            }
        }

        @TearDown
        public void delete() throws IOException {
            Files.deleteIfExists(bcf);
        }
    }

    /** Records decoded, genotypes left lazily undecoded. */
    @Benchmark
    public long vcfRead(final VcfInput input) {
        return read(input.vcf, false);
    }

    /** Records and every genotype decoded. */
    @Benchmark
    public long vcfGenotypes(final VcfInput input) {
        return read(input.vcf, true);
    }

    @Benchmark
    public long vcfWrite(final DecodedVcfRecords decoded) {
        return write(new VariantContextWriterBuilder().clearOptions().setOutputVCFStream(nowhere()), decoded);
    }

    @Benchmark
    public long bcfRead(final BcfInput input) {
        return read(input.bcf, false);
    }

    @Benchmark
    public long bcfGenotypes(final BcfInput input) {
        return read(input.bcf, true);
    }

    @Benchmark
    public long bcfWrite(final DecodedBcfRecords decoded) {
        return write(new VariantContextWriterBuilder().clearOptions().setOutputBCFStream(nowhere()), decoded);
    }

    private static long read(final Path input, final boolean decodeGenotypes) {
        long count = 0;
        try (final VCFFileReader reader = new VCFFileReader(input, false);
                final CloseableIterator<VariantContext> records = reader.iterator()) {
            while (records.hasNext()) {
                final VariantContext vc = records.next();
                if (decodeGenotypes) {
                    decodeGenotypes(vc);
                }
                count++;
            }
        }
        return count;
    }

    private static long write(final VariantContextWriterBuilder builder, final DecodedRecords decoded) {
        try (final VariantContextWriter writer = builder.build()) {
            writer.writeHeader(decoded.header);
            decoded.records.forEach(writer::add);
        }
        return decoded.records.size();
    }

    /** Iterating is what forces the decode; a lazy genotypes context answers {@code size()} from the header alone. */
    private static VariantContext decodeGenotypes(final VariantContext vc) {
        for (final Genotype genotype : vc.getGenotypes()) {
            genotype.getAlleles();
        }
        return vc;
    }

    private static OutputStream nowhere() {
        return OutputStream.nullOutputStream();
    }
}
