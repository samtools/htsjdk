package htsjdk.variant;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFContigHeaderLine;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFFilterHeaderLine;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Benchmarks the VCF and BCF codecs. Not a test; run by hand from the test classpath and compare runs of the same
 * command, since timings depend on JIT state and on the machine.
 *
 * <pre>
 * ./gradlew compileTestJava shadowJar
 * java -cp build/classes/java/test:'build/libs/*' htsjdk.variant.VariantCodecBenchmark generate --dir inputs
 * java -Xmx8g -cp build/classes/java/test:'build/libs/*' htsjdk.variant.VariantCodecBenchmark run --vcf inputs/wide.vcf.gz
 * </pre>
 *
 * <p>{@code generate} writes three deterministic files: {@code single.vcf.gz} (one sample), {@code wide.vcf.gz} (many
 * samples, a hundredth as many records) and {@code gvcf.vcf.gz} (one sample, mostly {@code <NON_REF>} reference
 * blocks), so a baseline never depends on data outside the repository.
 *
 * <p>{@code run} phases: <b>vcf-read</b> (records decoded, genotypes left lazily undecoded), <b>vcf-genotypes</b>
 * (every genotype decoded too), <b>vcf-write</b> (fully decoded records written to a discarding stream), and
 * <b>bcf-read</b>, <b>bcf-genotypes</b> and <b>bcf-write</b>, which run against a BCF written from the VCF into a
 * temporary directory first. The write phases hold the whole file in memory: size the heap accordingly or cap the
 * record count.
 */
public class VariantCodecBenchmark {

    private static final String USAGE = String.join(
            "\n",
            "Usage: VariantCodecBenchmark generate --dir <dir> [--records <N>] [--samples <N>] [--seed <N>]",
            "       VariantCodecBenchmark run --vcf <file.vcf[.gz]> [options]",
            "",
            "generate writes single.vcf.gz, wide.vcf.gz and gvcf.vcf.gz into <dir>:",
            "  --records <N>         Records in single and gvcf; wide gets N/100 (default: 300000)",
            "  --samples <N>         Samples in wide (default: 1000)",
            "  --seed <N>            Random seed (default: 42)",
            "",
            "run options:",
            "  --phases <list>       Comma-separated: vcf-read,vcf-genotypes,vcf-write,bcf-read,bcf-genotypes,bcf-write",
            "                        (default: all)",
            "  --max-records <N>     Cap on records read (default: no cap)",
            "  --warmups <N>         Unmeasured iterations before timing (default: 2)",
            "  --iterations <N>      Measured iterations (default: 5)",
            "  --label <text>        Row label for --tsv output (default: the VCF file name)",
            "  --tsv <path>          Append machine-readable results to this file",
            "  --help                Print this help message",
            "",
            "Exit codes: 0 = completed, 2 = error");

    static final List<String> ALL_PHASES =
            List.of("vcf-read", "vcf-genotypes", "vcf-write", "bcf-read", "bcf-genotypes", "bcf-write");

    public static void main(final String[] args) {
        if (args.length == 0 || hasFlag(args, "--help") || hasFlag(args, "-h")) {
            System.out.println(USAGE);
            System.exit(args.length == 0 ? 2 : 0);
        }
        try {
            System.exit(run(args));
        } catch (final Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    /** Runs the command line; returns 0 on completion, 2 on error. */
    public static int run(final String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println(USAGE);
            return 2;
        }
        final String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0]) {
            case "generate":
                return generate(rest);
            case "run":
                return runPhases(rest) == null ? 2 : 0;
            default:
                System.err.println("Unknown command " + args[0] + "\n" + USAGE);
                return 2;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // run
    // ---------------------------------------------------------------------------------------------

    /** One measured phase: elapsed nanos per iteration, sorted, and the records it processed. */
    record Result(String phase, long[] elapsedNanos, long records) {
        double minMillis() {
            return elapsedNanos[0] / 1e6;
        }

        double medianMillis() {
            return elapsedNanos[elapsedNanos.length / 2] / 1e6;
        }

        double maxMillis() {
            return elapsedNanos[elapsedNanos.length - 1] / 1e6;
        }
    }

    /** Runs the {@code run} command; returns the results, or null if the arguments were bad. */
    static List<Result> runPhases(final String[] args) throws IOException {
        Path vcfPath = null;
        Path tsvPath = null;
        String label = null;
        List<String> phases = ALL_PHASES;
        long maxRecords = Long.MAX_VALUE;
        int warmups = 2;
        int iterations = 5;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--vcf":
                    vcfPath = Paths.get(args[++i]);
                    break;
                case "--phases":
                    phases = Arrays.asList(args[++i].split(","));
                    break;
                case "--max-records":
                    maxRecords = Long.parseLong(args[++i]);
                    break;
                case "--warmups":
                    warmups = Integer.parseInt(args[++i]);
                    break;
                case "--iterations":
                    iterations = Integer.parseInt(args[++i]);
                    break;
                case "--label":
                    label = args[++i];
                    break;
                case "--tsv":
                    tsvPath = Paths.get(args[++i]);
                    break;
                default:
                    System.err.println("Unknown argument " + args[i] + "\n" + USAGE);
                    return null;
            }
        }
        if (vcfPath == null) {
            System.err.println("--vcf is required\n" + USAGE);
            return null;
        }
        for (final String phase : phases) {
            if (!ALL_PHASES.contains(phase)) {
                System.err.println("Unknown phase " + phase + "\n" + USAGE);
                return null;
            }
        }
        if (label == null) {
            label = vcfPath.getFileName().toString();
        }

        final boolean needsBcf = phases.stream().anyMatch(phase -> phase.startsWith("bcf-"));
        final Path tempDir = needsBcf ? Files.createTempDirectory("VariantCodecBenchmark") : null;
        try {
            final Path bcfPath = needsBcf ? writeBcf(vcfPath, tempDir.resolve("input.bcf"), maxRecords) : null;
            final List<Result> results = new ArrayList<>();
            for (final String phase : phases) {
                final Path input = phase.startsWith("bcf-") ? bcfPath : vcfPath;
                final long cap = maxRecords;
                final MeasuredPhase measured;
                if (phase.endsWith("-read")) {
                    measured = () -> readRecords(input, cap, false);
                } else if (phase.endsWith("-genotypes")) {
                    measured = () -> readRecords(input, cap, true);
                } else {
                    final boolean asBcf = phase.startsWith("bcf-");
                    final VCFHeader header;
                    final List<VariantContext> records;
                    try (final VCFFileReader reader = new VCFFileReader(input, false)) {
                        header = reader.getFileHeader();
                        records = loadDecoded(reader, cap);
                    }
                    measured = () -> writeRecords(header, records, asBcf);
                }
                final Result result = measure(phase, warmups, iterations, measured);
                results.add(result);
                System.out.printf(
                        "%-14s %10.2f ms median  %10.2f ms min  %,12d records%n",
                        phase, result.medianMillis(), result.minMillis(), result.records());
            }
            if (tsvPath != null) {
                writeTsv(tsvPath, label, results);
            }
            return results;
        } finally {
            if (tempDir != null) {
                try (final var files = Files.list(tempDir)) {
                    for (final Path file : files.toList()) {
                        Files.deleteIfExists(file);
                    }
                }
                Files.deleteIfExists(tempDir);
            }
        }
    }

    @FunctionalInterface
    private interface MeasuredPhase {
        long run() throws IOException;
    }

    private static Result measure(
            final String phase, final int warmups, final int iterations, final MeasuredPhase measured)
            throws IOException {
        for (int i = 0; i < warmups; i++) {
            measured.run();
        }
        final long[] elapsedNanos = new long[iterations];
        long records = 0;
        for (int i = 0; i < iterations; i++) {
            final long start = System.nanoTime();
            records = measured.run();
            elapsedNanos[i] = System.nanoTime() - start;
        }
        Arrays.sort(elapsedNanos);
        return new Result(phase, elapsedNanos, records);
    }

    /** Reads up to {@code maxRecords} records, decoding every genotype as well when asked to. */
    private static long readRecords(final Path input, final long maxRecords, final boolean decodeGenotypes) {
        long count = 0;
        try (final VCFFileReader reader = new VCFFileReader(input, false);
                final CloseableIterator<VariantContext> records = reader.iterator()) {
            while (records.hasNext() && count < maxRecords) {
                final VariantContext vc = records.next();
                if (decodeGenotypes) {
                    for (final Genotype genotype : vc.getGenotypes()) {
                        genotype.getAlleles();
                    }
                }
                count++;
            }
        }
        return count;
    }

    /**
     * Loads records with their genotypes decoded, which is what a tool that has looked at them hands to a writer.
     * Undecoded genotypes would take the writers' pass-through shortcut and measure nothing. Iterating is what forces
     * the decode; a lazy context answers {@code size()} from the header alone.
     */
    static List<VariantContext> loadDecoded(final VCFFileReader reader, final long maxRecords) {
        final List<VariantContext> records = new ArrayList<>();
        try (final CloseableIterator<VariantContext> iterator = reader.iterator()) {
            while (iterator.hasNext() && records.size() < maxRecords) {
                final VariantContext vc = iterator.next();
                for (final Genotype genotype : vc.getGenotypes()) {
                    genotype.getAlleles();
                }
                records.add(vc);
            }
        }
        return records;
    }

    private static long writeRecords(final VCFHeader header, final List<VariantContext> records, final boolean asBcf) {
        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder().clearOptions();
        if (asBcf) {
            builder.setOutputBCFStream(OutputStream.nullOutputStream());
        } else {
            builder.setOutputVCFStream(OutputStream.nullOutputStream());
        }
        try (final VariantContextWriter writer = builder.build()) {
            writer.writeHeader(header);
            for (final VariantContext vc : records) {
                writer.add(vc);
            }
        }
        return records.size();
    }

    private static Path writeBcf(final Path vcfPath, final Path bcfPath, final long maxRecords) {
        try (final VCFFileReader reader = new VCFFileReader(vcfPath, false);
                final VariantContextWriter writer = new VariantContextWriterBuilder()
                        .clearOptions()
                        .setOutputPath(bcfPath)
                        .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                        .build()) {
            writer.writeHeader(reader.getFileHeader());
            for (final VariantContext vc : loadDecoded(reader, maxRecords)) {
                writer.add(vc);
            }
        }
        return bcfPath;
    }

    private static void writeTsv(final Path tsvPath, final String label, final List<Result> results)
            throws IOException {
        final StringBuilder rows = new StringBuilder();
        if (!Files.exists(tsvPath)) {
            rows.append("label\tphase\tmin_ms\tmedian_ms\tmax_ms\trecords\n");
        }
        for (final Result result : results) {
            // the root locale, so that the numbers read the same whatever machine wrote them
            rows.append(String.format(
                    Locale.ROOT,
                    "%s\t%s\t%.2f\t%.2f\t%.2f\t%d\n",
                    label,
                    result.phase(),
                    result.minMillis(),
                    result.medianMillis(),
                    result.maxMillis(),
                    result.records()));
        }
        Files.writeString(
                tsvPath, rows.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    // ---------------------------------------------------------------------------------------------
    // generate
    // ---------------------------------------------------------------------------------------------

    private static final int CONTIGS = 22;
    private static final int CONTIG_LENGTH = 200_000_000;
    private static final String[] BASES = {"A", "C", "G", "T"};

    static int generate(final String[] args) throws IOException {
        Path dir = null;
        int records = 300_000;
        int samples = 1000;
        long seed = 42;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dir":
                    dir = Paths.get(args[++i]);
                    break;
                case "--records":
                    records = Integer.parseInt(args[++i]);
                    break;
                case "--samples":
                    samples = Integer.parseInt(args[++i]);
                    break;
                case "--seed":
                    seed = Long.parseLong(args[++i]);
                    break;
                default:
                    System.err.println("Unknown argument " + args[i] + "\n" + USAGE);
                    return 2;
            }
        }
        if (dir == null) {
            System.err.println("--dir is required\n" + USAGE);
            return 2;
        }
        Files.createDirectories(dir);
        generateFile(dir.resolve("single.vcf.gz"), records, 1, false, seed);
        generateFile(dir.resolve("wide.vcf.gz"), Math.max(1, records / 100), samples, false, seed + 1);
        generateFile(dir.resolve("gvcf.vcf.gz"), records, 1, true, seed + 2);
        return 0;
    }

    /** Writes a synthetic file whose content depends only on the arguments. */
    static void generateFile(
            final Path path, final int records, final int samples, final boolean gvcf, final long seed) {
        final Random random = new Random(seed);
        final List<String> sampleNames = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            sampleNames.add(String.format("sample%04d", i));
        }
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .clearOptions()
                .setOutputPath(path)
                .setOutputFileType(VariantContextWriterBuilder.OutputType.BLOCK_COMPRESSED_VCF)
                .build()) {
            writer.writeHeader(new VCFHeader(headerLines(gvcf), sampleNames));
            int contig = 1;
            int position = 0;
            for (int i = 0; i < records; i++) {
                position += 1 + random.nextInt(3000);
                if (position + 10_000 > CONTIG_LENGTH) {
                    contig++;
                    position = 1 + random.nextInt(3000);
                    if (contig > CONTIGS) {
                        break;
                    }
                }
                final VariantContext vc = gvcf && random.nextInt(100) < 70
                        ? referenceBlock("chr" + contig, position, random)
                        : variant("chr" + contig, position, sampleNames, gvcf, random);
                writer.add(vc);
                if (gvcf) {
                    position = vc.getEnd();
                }
            }
        }
    }

    private static Set<VCFHeaderLine> headerLines(final boolean gvcf) {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        for (int i = 1; i <= CONTIGS; i++) {
            final Map<String, String> contig = new LinkedHashMap<>();
            contig.put("ID", "chr" + i);
            contig.put("length", String.valueOf(CONTIG_LENGTH));
            lines.add(new VCFContigHeaderLine(contig, i - 1));
        }
        lines.add(new VCFFilterHeaderLine("LowQual", "Low quality"));
        lines.add(new VCFInfoHeaderLine("AC", VCFHeaderLineCount.A, VCFHeaderLineType.Integer, "Allele count"));
        lines.add(new VCFInfoHeaderLine("AF", VCFHeaderLineCount.A, VCFHeaderLineType.Float, "Allele frequency"));
        lines.add(new VCFInfoHeaderLine("AN", 1, VCFHeaderLineType.Integer, "Allele number"));
        lines.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "Depth"));
        lines.add(new VCFInfoHeaderLine("MQ", 1, VCFHeaderLineType.Float, "Mapping quality"));
        lines.add(new VCFInfoHeaderLine("QD", 1, VCFHeaderLineType.Float, "Quality by depth"));
        lines.add(new VCFInfoHeaderLine("FS", 1, VCFHeaderLineType.Float, "Fisher strand"));
        lines.add(new VCFInfoHeaderLine("DB", 0, VCFHeaderLineType.Flag, "In dbSNP"));
        if (gvcf) {
            lines.add(new VCFInfoHeaderLine("END", 1, VCFHeaderLineType.Integer, "End of the reference block"));
        }
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "Genotype"));
        lines.add(new VCFFormatHeaderLine("AD", VCFHeaderLineCount.R, VCFHeaderLineType.Integer, "Allele depths"));
        lines.add(new VCFFormatHeaderLine("DP", 1, VCFHeaderLineType.Integer, "Depth"));
        lines.add(new VCFFormatHeaderLine("GQ", 1, VCFHeaderLineType.Integer, "Genotype quality"));
        lines.add(new VCFFormatHeaderLine("PL", VCFHeaderLineCount.G, VCFHeaderLineType.Integer, "Phred likelihoods"));
        if (gvcf) {
            lines.add(new VCFFormatHeaderLine("MIN_DP", 1, VCFHeaderLineType.Integer, "Minimum depth in the block"));
        }
        return lines;
    }

    private static VariantContext variant(
            final String contig,
            final int position,
            final List<String> sampleNames,
            final boolean gvcf,
            final Random random) {
        // 15% indels, half deletions (long REF, shorter ALTs) and half insertions (one-base REF, long ALTs); 5% of
        // records carry a second ALT. Deletion ALTs are REF prefixes of distinct lengths, so a REF of at least three
        // bases always leaves room for two of them.
        final List<Allele> alleles = new ArrayList<>();
        final boolean indel = random.nextInt(100) < 15;
        final boolean deletion = indel && random.nextBoolean();
        final String ref = deletion ? randomBases(3 + random.nextInt(6), random) : randomBase(random);
        alleles.add(Allele.create(ref, true));
        final int altCount = random.nextInt(100) < 5 ? 2 : 1;
        while (alleles.size() < altCount + 1) {
            final String alt;
            if (deletion) {
                alt = alleles.size() == 1
                        ? ref.substring(0, 1)
                        : ref.substring(0, 2 + random.nextInt(ref.length() - 2));
            } else if (indel) {
                alt = ref + randomBases(1 + random.nextInt(7), random);
            } else {
                alt = randomBase(random);
            }
            final Allele allele = Allele.create(alt, false);
            if (!alt.equals(ref) && !alleles.contains(allele)) {
                alleles.add(allele);
            }
        }
        if (gvcf) {
            alleles.add(Allele.NON_REF_ALLELE);
        }

        final List<Genotype> genotypes = new ArrayList<>(sampleNames.size());
        final int[] alleleCounts = new int[alleles.size()];
        int totalDepth = 0;
        for (final String sample : sampleNames) {
            final Genotype genotype = genotype(sample, alleles, random);
            genotypes.add(genotype);
            for (final Allele allele : genotype.getAlleles()) {
                if (allele.isCalled()) {
                    alleleCounts[alleles.indexOf(allele)]++;
                }
            }
            totalDepth += Math.max(genotype.getDP(), 0);
        }
        final int calledAlleles = Arrays.stream(alleleCounts).sum();
        final Map<String, Object> attributes = new LinkedHashMap<>();
        final List<Integer> ac = new ArrayList<>();
        final List<Double> af = new ArrayList<>();
        for (int i = 1; i < alleles.size(); i++) {
            ac.add(alleleCounts[i]);
            af.add(calledAlleles == 0 ? 0.0 : Math.round(1000.0 * alleleCounts[i] / calledAlleles) / 1000.0);
        }
        attributes.put("AC", ac);
        attributes.put("AF", af);
        attributes.put("AN", calledAlleles);
        attributes.put("DP", totalDepth);
        attributes.put("MQ", Math.round(100 * (30 + 30 * random.nextDouble())) / 100.0);
        attributes.put("QD", Math.round(100 * (30 * random.nextDouble())) / 100.0);
        attributes.put("FS", Math.round(1000 * (10 * random.nextDouble())) / 1000.0);
        if (random.nextInt(100) < 30) {
            attributes.put("DB", true);
        }

        final VariantContextBuilder builder = new VariantContextBuilder(
                        "generated", contig, position, position + ref.length() - 1, alleles)
                .genotypes(genotypes)
                .attributes(attributes)
                .log10PError(-(10 + 3000 * random.nextDouble()) / 10.0);
        if (random.nextInt(100) < 10) {
            builder.filter("LowQual");
        } else {
            builder.passFilters();
        }
        if (random.nextInt(100) < 20) {
            builder.id("rs" + (1_000_000 + random.nextInt(9_000_000)));
        }
        return builder.make();
    }

    private static Genotype genotype(final String sample, final List<Allele> alleles, final Random random) {
        final Allele ref = alleles.get(0);
        final int firstAlt = 1;
        final int draw = random.nextInt(100);
        final List<Allele> called;
        if (draw < 2) {
            called = List.of(Allele.NO_CALL, Allele.NO_CALL);
        } else if (draw < 62) {
            called = List.of(ref, ref);
        } else if (draw < 92) {
            called = List.of(ref, alleles.get(firstAlt));
        } else {
            called = List.of(alleles.get(firstAlt), alleles.get(firstAlt));
        }
        final int depth = 5 + random.nextInt(60);
        final int[] ad = new int[alleles.size()];
        for (final Allele allele : called) {
            if (allele.isCalled()) {
                ad[alleles.indexOf(allele)] += depth / 2;
            }
        }
        final int genotypeCount = alleles.size() * (alleles.size() + 1) / 2;
        final int[] pl = new int[genotypeCount];
        for (int i = 0; i < genotypeCount; i++) {
            pl[i] = random.nextInt(1000);
        }
        final GenotypeBuilder builder = new GenotypeBuilder(sample, called)
                .DP(depth)
                .GQ(random.nextInt(100))
                .phased(draw >= 2 && random.nextInt(100) < 10);
        if (draw >= 2) {
            builder.AD(ad).PL(pl);
        }
        return builder.make();
    }

    private static VariantContext referenceBlock(final String contig, final int position, final Random random) {
        final Allele ref = Allele.create(randomBase(random), true);
        final List<Allele> alleles = List.of(ref, Allele.NON_REF_ALLELE);
        final int end = position + random.nextInt(2000);
        final int depth = 5 + random.nextInt(60);
        final Genotype genotype = new GenotypeBuilder("sample0000", List.of(ref, ref))
                .DP(depth)
                .GQ(random.nextInt(100))
                .PL(new int[] {0, 30 + random.nextInt(60), 300 + random.nextInt(600)})
                .attribute("MIN_DP", Math.max(1, depth - random.nextInt(5)))
                .make();
        return new VariantContextBuilder("generated", contig, position, end, alleles)
                .genotypes(genotype)
                .attribute("END", end)
                .make();
    }

    private static String randomBase(final Random random) {
        return BASES[random.nextInt(4)];
    }

    private static String randomBases(final int length, final Random random) {
        final StringBuilder bases = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            bases.append(randomBase(random));
        }
        return bases.toString();
    }

    private static boolean hasFlag(final String[] args, final String flag) {
        return Arrays.asList(args).contains(flag);
    }
}
