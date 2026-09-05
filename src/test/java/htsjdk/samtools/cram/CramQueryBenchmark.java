package htsjdk.samtools.cram;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamFiles;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.CloseableIterator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Measures the cost of indexed access to a CRAM file, so that a change to the index path can be
 * shown not to regress sequential reading and to improve region queries. Not a test: run it by hand
 * from the test classpath, and compare runs of the exact same command since phase timings depend on
 * JIT state.
 *
 * <pre>
 * ./gradlew compileTestJava shadowJar
 * java -cp build/classes/java/test:'build/libs/*' htsjdk.samtools.cram.CramQueryBenchmark \
 *     --cram sample.cram --reference ref.fa
 * </pre>
 *
 * <p>Phases, each independently selectable: <b>open</b> (open and close a reader, which is where
 * eager index work shows up), <b>query</b> (N seeded pseudo-random regions, queried and consumed),
 * <b>sequential</b> (full-file iteration, which must not regress), <b>bytes</b> (the same regions
 * again, counting bytes read from the CRAM stream rather than timing them) and <b>memory</b> (a
 * coarse estimate of the heap an open, index-ready reader retains).
 *
 * <p>Two confounds: decoding needs reference bases and the reference source caches one contig at a
 * time, so regions are queried in coordinate order and {@code --sequences} can restrict the run to
 * one contig. And the {@code bytes} phase must open the CRAM as a stream to count it, a different
 * reader code path from the timed phases, so only its byte count is reported.
 */
public class CramQueryBenchmark {

    private static final String USAGE = String.join(
            "\n",
            "Usage: CramQueryBenchmark --cram <file.cram> --reference <ref.fa> [options]",
            "",
            "Measure indexed-access cost for a CRAM file. The index is discovered next to the CRAM",
            "unless --index is given.",
            "",
            "Options:",
            "  --cram <path>         CRAM file to measure (required)",
            "  --reference <path>    Reference FASTA (required)",
            "  --index <path>        Index file; default is to discover it beside the CRAM",
            "  --phases <list>       Comma-separated: open,query,sequential,bytes,memory (default: all)",
            "  --regions <N>         Number of regions to query (default: 200)",
            "  --width <N>           Region width in bases (default: 1000)",
            "  --sequences <list>    Comma-separated sequence names to draw regions from",
            "                        (default: every sequence in the dictionary, weighted by length)",
            "  --seed <N>            Region sampling seed (default: 42)",
            "  --warmups <N>         Unmeasured iterations before timing (default: 1)",
            "  --iterations <N>      Measured iterations (default: 3)",
            "  --max-records <N>     Cap on records read in the sequential phase (default: no cap)",
            "  --label <text>        Row label for --tsv output (default: the CRAM file name)",
            "  --tsv <path>          Append machine-readable results to this file",
            "  --help                Print this help message",
            "",
            "Exit codes: 0 = benchmark completed, 2 = error");

    private static final List<String> ALL_PHASES = List.of("open", "query", "sequential", "bytes", "memory");

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

    /**
     * Run the benchmark and return the exit code (0 = completed, 2 = error).
     * Separated from {@link #main} so it can be driven from a test or another tool.
     */
    public static int run(final String[] args) throws IOException {
        Path cramPath = null;
        Path referencePath = null;
        Path indexPath = null;
        Path tsvPath = null;
        String label = null;
        List<String> phases = ALL_PHASES;
        Set<String> sequenceNames = null;
        int regionCount = 200;
        int regionWidth = 1000;
        long seed = 42;
        int warmups = 1;
        int iterations = 3;
        long maxRecords = Long.MAX_VALUE;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--cram":
                    cramPath = Paths.get(args[++i]);
                    break;
                case "--reference":
                case "-r":
                    referencePath = Paths.get(args[++i]);
                    break;
                case "--index":
                    indexPath = Paths.get(args[++i]);
                    break;
                case "--phases":
                    phases = Arrays.asList(args[++i].split(","));
                    break;
                case "--regions":
                    regionCount = Integer.parseInt(args[++i]);
                    break;
                case "--width":
                    regionWidth = Integer.parseInt(args[++i]);
                    break;
                case "--sequences":
                    sequenceNames = new LinkedHashSet<>(Arrays.asList(args[++i].split(",")));
                    break;
                case "--seed":
                    seed = Long.parseLong(args[++i]);
                    break;
                case "--warmups":
                    warmups = Integer.parseInt(args[++i]);
                    break;
                case "--iterations":
                    iterations = Integer.parseInt(args[++i]);
                    break;
                case "--max-records":
                    maxRecords = Long.parseLong(args[++i]);
                    break;
                case "--label":
                    label = args[++i];
                    break;
                case "--tsv":
                    tsvPath = Paths.get(args[++i]);
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    return 2;
            }
        }

        if (cramPath == null || referencePath == null) {
            System.err.println("Both --cram and --reference are required.");
            return 2;
        }
        for (final String phase : phases) {
            if (!ALL_PHASES.contains(phase)) {
                System.err.println("Unknown phase '" + phase + "'; expected one of " + ALL_PHASES);
                return 2;
            }
        }
        if (label == null) {
            label = cramPath.getFileName().toString();
        }

        final SamReaderFactory factory = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .referenceSequence(referencePath);

        final SAMSequenceDictionary dictionary;
        try (final SamReader reader = openReader(factory, cramPath, indexPath)) {
            if (!reader.hasIndex()) {
                System.err.println("No index found for " + cramPath + "; region queries need one.");
                return 2;
            }
            dictionary = reader.getFileHeader().getSequenceDictionary();
        }

        final List<Region> regions = sampleRegions(dictionary, sequenceNames, regionCount, regionWidth, seed);
        if (regions.isEmpty()) {
            System.err.println("No sequences long enough to sample " + regionWidth + "bp regions from.");
            return 2;
        }

        System.out.printf("CRAM:       %s (%,d bytes)%n", cramPath, Files.size(cramPath));
        System.out.printf("Reference:  %s%n", referencePath);
        System.out.printf(
                "Regions:    %,d x %,dbp, seed %d, drawn from %d sequence(s)%n",
                regions.size(),
                regionWidth,
                seed,
                regions.stream().map(Region::sequence).distinct().count());
        System.out.printf("Iterations: %d measured, %d warmup%n%n", iterations, warmups);

        // The lambdas below capture these, so they need to be effectively final.
        final Path cram = cramPath;
        final Path index = indexPath;
        final long recordCap = maxRecords;

        final List<Result> results = new ArrayList<>();
        if (phases.contains("open")) {
            results.add(measure("open", warmups, iterations, () -> {
                try (final SamReader reader = openReader(factory, cram, index)) {
                    // Opening is the whole measurement; touching the header keeps the call from
                    // being optimised away and confirms the reader is actually usable.
                    return reader.getFileHeader().getSequenceDictionary().size();
                }
            }));
        }
        if (phases.contains("query")) {
            results.add(measure("query", warmups, iterations, () -> {
                try (final SamReader reader = openReader(factory, cram, index)) {
                    return countQueriedRecords(reader, regions);
                }
            }));
        }
        if (phases.contains("sequential")) {
            results.add(measure("sequential", warmups, iterations, () -> {
                try (final SamReader reader = openReader(factory, cram, index)) {
                    return countSequentialRecords(reader, recordCap);
                }
            }));
        }

        printTimings(results, regions.size());

        long queryBytes = -1;
        if (phases.contains("bytes")) {
            queryBytes = countQueryBytes(factory, cramPath, indexPath, regions);
            System.out.printf(
                    "%nBytes read from the CRAM stream for %,d queries: %,d (%,d per query)%n",
                    regions.size(), queryBytes, queryBytes / regions.size());
        }

        if (phases.contains("memory")) {
            final long retained = measureRetainedHeap(factory, cram, index, dictionary);
            System.out.printf("%nHeap retained by an open, queried reader: %,d bytes%n", retained);
        }

        if (tsvPath != null) {
            writeTsv(tsvPath, label, regions.size(), regionWidth, results, queryBytes);
            System.out.printf("%nAppended results to %s%n", tsvPath);
        }

        return 0;
    }

    private static SamReader openReader(final SamReaderFactory factory, final Path cramPath, final Path indexPath) {
        final SamInputResource resource = indexPath == null
                ? SamInputResource.of(cramPath)
                : SamInputResource.of(cramPath).index(indexPath);
        return factory.open(resource);
    }

    /** Query every region and return the total number of records returned. */
    private static long countQueriedRecords(final SamReader reader, final List<Region> regions) {
        long records = 0;
        for (final Region region : regions) {
            try (final CloseableIterator<SAMRecord> iterator =
                    reader.queryOverlapping(region.sequence(), region.start(), region.end())) {
                while (iterator.hasNext()) {
                    iterator.next();
                    records++;
                }
            }
        }
        return records;
    }

    /** Iterate the whole file, stopping after {@code maxRecords}, and return the count. */
    private static long countSequentialRecords(final SamReader reader, final long maxRecords) {
        long records = 0;
        try (final CloseableIterator<SAMRecord> iterator = reader.iterator()) {
            while (iterator.hasNext() && records < maxRecords) {
                iterator.next();
                records++;
            }
        }
        return records;
    }

    /**
     * Re-run the queries against a byte-counting stream and return the number of CRAM bytes read.
     * This deliberately opens the CRAM as a stream rather than a path, because that is the only way
     * to interpose a counter; the resulting timings are not comparable to the timed phases and so
     * are not reported.
     */
    private static long countQueryBytes(
            final SamReaderFactory factory, final Path cramPath, final Path indexPath, final List<Region> regions)
            throws IOException {
        final Path resolvedIndex = indexPath == null ? SamFiles.findIndex(cramPath) : indexPath;
        if (resolvedIndex == null) {
            throw new IOException("No index found beside " + cramPath);
        }

        try (final CountingSeekableStream cramStream = new CountingSeekableStream(new SeekablePathStream(cramPath));
                final SeekableStream indexStream = new SeekablePathStream(resolvedIndex)) {
            final SamInputResource resource = SamInputResource.of(cramStream).index(indexStream);
            try (final SamReader reader = factory.open(resource)) {
                final long bytesAfterOpen = cramStream.getBytesRead();
                countQueriedRecords(reader, regions);
                return cramStream.getBytesRead() - bytesAfterOpen;
            }
        }
    }

    /**
     * Estimate the heap an open, index-ready reader holds on to.
     *
     * <p>The reader is forced into an index-ready state by running one real query rather than by
     * asking for the index object, so that the measurement does not name any particular index
     * implementation. The query targets the shortest sequence in the dictionary, because decoding a
     * record caches that sequence's bases and a long contig would dwarf the index itself.
     *
     * <p>This is a coarse {@code Runtime}-based estimate, not a heap dump: treat a difference of a
     * few megabytes as real and anything smaller as noise.
     */
    private static long measureRetainedHeap(
            final SamReaderFactory factory,
            final Path cramPath,
            final Path indexPath,
            final SAMSequenceDictionary dictionary)
            throws IOException {
        final SAMSequenceRecord shortest = dictionary.getSequences().stream()
                .min(Comparator.comparingInt(SAMSequenceRecord::getSequenceLength))
                .orElseThrow(() -> new IOException("Sequence dictionary is empty"));
        final Region probe = new Region(shortest.getSequenceName(), 1, shortest.getSequenceLength());

        final long baseline = usedHeapAfterGc();
        try (final SamReader reader = openReader(factory, cramPath, indexPath)) {
            countQueriedRecords(reader, List.of(probe));
            return usedHeapAfterGc() - baseline;
        }
    }

    private static long usedHeapAfterGc() {
        final Runtime runtime = Runtime.getRuntime();
        // Repeat because a single collection often leaves recently-dead objects uncollected.
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * Draw {@code count} regions of {@code width} bases, choosing sequences in proportion to their
     * length so that the sample resembles a genome-wide access pattern.
     *
     * @param dictionary the sequence dictionary to draw from
     * @param allowedSequences if non-null, only these sequence names are considered
     * @param count number of regions to produce
     * @param width region width in bases
     * @param seed seed for the sampler, so runs are reproducible
     * @return the sampled regions, or an empty list if no sequence is at least {@code width} long
     */
    static List<Region> sampleRegions(
            final SAMSequenceDictionary dictionary,
            final Set<String> allowedSequences,
            final int count,
            final int width,
            final long seed) {
        final List<SAMSequenceRecord> candidates = dictionary.getSequences().stream()
                .filter(sequence -> allowedSequences == null || allowedSequences.contains(sequence.getSequenceName()))
                .filter(sequence -> sequence.getSequenceLength() >= width)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        // Cumulative lengths let a single uniform draw pick a sequence in proportion to its length.
        final long[] cumulativeLengths = new long[candidates.size()];
        long total = 0;
        for (int i = 0; i < candidates.size(); i++) {
            total += candidates.get(i).getSequenceLength();
            cumulativeLengths[i] = total;
        }

        final Random random = new Random(seed);
        final List<Region> regions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final long draw = (long) (random.nextDouble() * total);
            int index = Arrays.binarySearch(cumulativeLengths, draw);
            if (index < 0) {
                index = -(index + 1);
            }
            final SAMSequenceRecord sequence = candidates.get(Math.min(index, candidates.size() - 1));
            final int start = 1 + random.nextInt(sequence.getSequenceLength() - width + 1);
            regions.add(new Region(sequence.getSequenceName(), start, start + width - 1));
        }

        // Query in coordinate order. Decoding a CRAM record needs its reference sequence, and the
        // reference source caches only the contig it last served, so jumping between contigs reloads
        // hundreds of megabases per query and swamps everything the index does. Coordinate order is
        // also how tools that walk an interval list actually query.
        regions.sort(Comparator.comparingInt((final Region region) -> dictionary.getSequenceIndex(region.sequence()))
                .thenComparingInt(Region::start));
        return regions;
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

    private static void printTimings(final List<Result> results, final int regionCount) {
        if (results.isEmpty()) {
            return;
        }
        System.out.printf("%-12s %12s %12s %12s %14s%n", "Phase", "Min (ms)", "Median (ms)", "Max (ms)", "Records");
        System.out.println("-".repeat(66));
        for (final Result result : results) {
            System.out.printf(
                    "%-12s %12.2f %12.2f %12.2f %,14d%n",
                    result.phase(), result.minMillis(), result.medianMillis(), result.maxMillis(), result.records());
        }
        for (final Result result : results) {
            if (result.phase().equals("query")) {
                System.out.printf(
                        "%nPer query: %.3f ms (median over %,d regions)%n",
                        result.medianMillis() / regionCount, regionCount);
            }
        }
    }

    private static void writeTsv(
            final Path tsvPath,
            final String label,
            final int regionCount,
            final int regionWidth,
            final List<Result> results,
            final long queryBytes)
            throws IOException {
        final StringBuilder rows = new StringBuilder();
        if (!Files.exists(tsvPath)) {
            rows.append("label\tphase\tregions\twidth\tmin_ms\tmedian_ms\tmax_ms\trecords\tquery_bytes\n");
        }
        for (final Result result : results) {
            rows.append(String.format(
                    "%s\t%s\t%d\t%d\t%.2f\t%.2f\t%.2f\t%d\t%s%n",
                    label,
                    result.phase(),
                    regionCount,
                    regionWidth,
                    result.minMillis(),
                    result.medianMillis(),
                    result.maxMillis(),
                    result.records(),
                    queryBytes < 0 ? "NA" : Long.toString(queryBytes)));
        }
        Files.writeString(
                tsvPath, rows.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static boolean hasFlag(final String[] args, final String flag) {
        return Arrays.asList(args).contains(flag);
    }

    /** A region to query, in 1-based inclusive coordinates. */
    record Region(String sequence, int start, int end) {}

    /** One phase's timings; {@code sortedNanos} is ascending so the quantiles are cheap. */
    private record Result(String phase, long[] sortedNanos, long records) {
        double minMillis() {
            return sortedNanos[0] / 1_000_000.0;
        }

        double medianMillis() {
            return sortedNanos[sortedNanos.length / 2] / 1_000_000.0;
        }

        double maxMillis() {
            return sortedNanos[sortedNanos.length - 1] / 1_000_000.0;
        }
    }

    /** A phase to time; returns a record count so the work cannot be optimised away. */
    @FunctionalInterface
    private interface MeasuredPhase {
        long run() throws IOException;
    }

    /** Wraps a {@link SeekableStream} to count the bytes actually delivered to the caller. */
    private static final class CountingSeekableStream extends SeekableStream {
        private final SeekableStream delegate;
        private long bytesRead;

        CountingSeekableStream(final SeekableStream delegate) {
            this.delegate = delegate;
        }

        long getBytesRead() {
            return bytesRead;
        }

        @Override
        public long length() {
            return delegate.length();
        }

        @Override
        public long position() throws IOException {
            return delegate.position();
        }

        @Override
        public void seek(final long position) throws IOException {
            delegate.seek(position);
        }

        @Override
        public int read() throws IOException {
            final int value = delegate.read();
            if (value >= 0) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) throws IOException {
            final int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                bytesRead += count;
            }
            return count;
        }

        @Override
        public boolean eof() throws IOException {
            return delegate.eof();
        }

        @Override
        public String getSource() {
            return delegate.getSource();
        }

        @Override
        public void close() {
            try {
                delegate.close();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
