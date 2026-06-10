package htsjdk.samtools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simple command-line tool for reading and optionally converting BAM files, primarily
 * for experimenting with BAM read-path profiling.
 *
 * <p>Usage:
 * <pre>
 *   java -cp htsjdk.jar htsjdk.samtools.BamConverter input.bam [output.bam]
 * </pre>
 *
 * <p>If no output is specified, records are read and iterated but not written.
 */
public class BamConverter {

    private static final String USAGE = String.join(
            "\n",
            "Usage: BamConverter <input> [output]",
            "",
            "Read and optionally convert a BAM file.",
            "",
            "Arguments:",
            "  input              Input BAM file",
            "  output             Optional output BAM file (omit to read-only)");

    /**
     * Entry point. Parses command-line arguments and performs the read/conversion.
     *
     * @param args command-line arguments (see USAGE for details)
     */
    public static void main(final String[] args) {
        if (hasFlag(args, "--help") || hasFlag(args, "-h")) {
            System.out.println(USAGE);
            System.exit(0);
        }
        if (args.length < 1) {
            System.err.println(USAGE);
            System.exit(1);
        }

        final boolean eager = hasFlag(args, "--eager");
        // Collect positional args (non-flag arguments)
        final String[] positional =
                java.util.Arrays.stream(args).filter(a -> !a.startsWith("--")).toArray(String[]::new);
        if (positional.length < 1) {
            System.err.println(USAGE);
            System.exit(1);
        }
        final String inputPath = positional[0];
        final String outputPath = positional.length > 1 ? positional[1] : null;
        final Path input = Paths.get(inputPath);
        final Path output = outputPath != null ? Paths.get(outputPath) : null;

        if (outputPath != null) {
            System.err.printf("Converting %s -> %s%s%n", inputPath, outputPath, eager ? " (eager decode)" : "");
        } else {
            System.err.printf("Reading %s (no output%s)%n", inputPath, eager ? ", eager decode" : "");
        }

        final SamReaderFactory readerFactory =
                SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT);

        long count = 0;
        final long startTime = System.currentTimeMillis();

        try (final SamReader reader = readerFactory.open(input)) {
            final SAMFileHeader header = reader.getFileHeader();

            if (output != null) {
                final SAMFileWriterFactory writerFactory = new SAMFileWriterFactory();
                try (final SAMFileWriter writer = writerFactory.makeBAMWriter(header, true, output)) {
                    for (final SAMRecord record : reader) {
                        if (eager) record.eagerDecode();
                        writer.addAlignment(record);
                        count++;
                        if (count % 1_000_000 == 0) {
                            System.err.printf("  ... %,d records%n", count);
                        }
                    }
                }
            } else {
                for (final SAMRecord record : reader) {
                    if (eager) record.eagerDecode();
                    count++;
                    if (count % 1_000_000 == 0) {
                        System.err.printf("  ... %,d records%n", count);
                    }
                }
            }
        } catch (final Exception e) {
            die("Error: " + e.getMessage());
        }

        final long elapsed = System.currentTimeMillis() - startTime;
        final long inputSize = fileSize(input);

        if (output != null) {
            final long outputSize = fileSize(output);
            System.err.printf(
                    "Done. %,d records in %.1fs. Input: %,d bytes, Output: %,d bytes (%.1f%%)%n",
                    count,
                    elapsed / 1000.0,
                    inputSize,
                    outputSize,
                    inputSize > 0 ? (100.0 * outputSize / inputSize) : 0);
        } else {
            System.err.printf("Done. %,d records in %.1fs. Input: %,d bytes%n", count, elapsed / 1000.0, inputSize);
        }
    }

    private static boolean hasFlag(final String[] args, final String flag) {
        for (final String arg : args) {
            if (flag.equals(arg)) return true;
        }
        return false;
    }

    /**
     * Returns the size of the file at the given path in bytes, or 0 if it cannot be determined.
     * Mirrors the lenient behavior of the previous {@code java.io.File#length()} usage.
     *
     * @param path the file to size
     * @return the size in bytes, or 0 if the file is missing or cannot be read
     */
    private static long fileSize(final Path path) {
        try {
            return Files.size(path);
        } catch (final IOException e) {
            return 0;
        }
    }

    private static void die(final String message) {
        System.err.println("ERROR: " + message);
        System.err.println();
        System.err.println(USAGE);
        System.exit(1);
    }
}
