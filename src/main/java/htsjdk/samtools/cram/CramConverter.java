package htsjdk.samtools.cram;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.structure.CRAMCompressionProfile;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.util.CloserUtil;

import java.io.File;
import java.nio.file.Path;

/**
 * Simple command-line tool for converting between SAM/BAM/CRAM formats, primarily for
 * experimenting with CRAM 3.1 compression profiles.
 *
 * <p>Usage:
 * <pre>
 *   java -cp htsjdk.jar htsjdk.samtools.cram.CramConverter \
 *       input.bam output.cram --reference ref.fasta --profile archive
 * </pre>
 */
public class CramConverter {

    private static final String USAGE = String.join("\n",
            "Usage: CramConverter <input> <output> [options]",
            "",
            "Convert between SAM, BAM, and CRAM formats.",
            "",
            "Arguments:",
            "  input              Input file (.sam, .bam, or .cram)",
            "  output             Output file (.sam, .bam, or .cram)",
            "",
            "Options:",
            "  --reference <path> Reference FASTA (required for CRAM input or output)",
            "  --profile <name>   CRAM compression profile: fast, normal (default), small, archive",
            "  --help             Print this help message",
            "",
            "Examples:",
            "  # Convert BAM to CRAM 3.1 with default (normal) profile:",
            "  CramConverter input.bam output.cram --reference ref.fasta",
            "",
            "  # Convert CRAM to CRAM with archive profile:",
            "  CramConverter input.cram output.cram --reference ref.fasta --profile archive",
            "",
            "  # Convert CRAM to BAM:",
            "  CramConverter input.cram output.bam --reference ref.fasta",
            "",
            "  # Convert BAM to CRAM with fast profile (writes CRAM 3.0):",
            "  CramConverter input.bam output.cram --reference ref.fasta --profile fast"
    );

    /**
     * Entry point. Parses command-line arguments and performs the conversion.
     *
     * @param args command-line arguments (see USAGE for details)
     */
    public static void main(final String[] args) {
        if (hasFlag(args, "--help") || hasFlag(args, "-h")) {
            System.out.println(USAGE);
            System.exit(0);
        }
        if (args.length < 2) {
            System.err.println(USAGE);
            System.exit(1);
        }

        final String inputPath = args[0];
        final String outputPath = args[1];
        String referencePath = null;
        String profileName = "normal";

        // Parse optional arguments
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--reference":
                case "-r":
                    if (++i >= args.length) die("--reference requires a path argument");
                    referencePath = args[i];
                    break;
                case "--profile":
                case "-p":
                    if (++i >= args.length) die("--profile requires a name argument");
                    profileName = args[i];
                    break;
                default:
                    die("Unknown option: " + args[i]);
            }
        }

        // Resolve profile
        final CRAMCompressionProfile profile;
        try {
            profile = CRAMCompressionProfile.valueOfCaseInsensitive(profileName);
        } catch (final IllegalArgumentException e) {
            die(e.getMessage());
            return; // unreachable but keeps compiler happy
        }

        // Check reference requirement
        final boolean inputIsCram = inputPath.endsWith(".cram");
        final boolean outputIsCram = outputPath.endsWith(".cram");
        if ((inputIsCram || outputIsCram) && referencePath == null) {
            die("--reference is required when reading or writing CRAM files");
        }

        final Path refPath = referencePath != null ? new File(referencePath).toPath() : null;
        final CRAMEncodingStrategy strategy = profile.toStrategy();

        System.err.printf("Converting %s -> %s (profile=%s, version=%s)%n",
                inputPath, outputPath, profile.name().toLowerCase(), strategy.getCramVersion());

        // Read input
        final SamReaderFactory readerFactory = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT);
        if (refPath != null) {
            readerFactory.referenceSequence(refPath);
        }

        long count = 0;
        final long startTime = System.currentTimeMillis();

        try (final SamReader reader = readerFactory.open(new File(inputPath))) {
            final SAMFileHeader header = reader.getFileHeader();

            // Create writer
            final SAMFileWriterFactory writerFactory = new SAMFileWriterFactory()
                    .setCRAMEncodingStrategy(strategy);

            try (final SAMFileWriter writer = outputIsCram ?
                    writerFactory.makeCRAMWriter(header, true, new File(outputPath).toPath(), refPath) :
                    writerFactory.makeWriter(header, true, new File(outputPath).toPath(), refPath)) {

                for (final SAMRecord record : reader) {
                    writer.addAlignment(record);
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
        final long inputSize = new File(inputPath).length();
        final long outputSize = new File(outputPath).length();

        System.err.printf("Done. %,d records in %.1fs. Input: %,d bytes, Output: %,d bytes (%.1f%%)%n",
                count, elapsed / 1000.0, inputSize, outputSize,
                inputSize > 0 ? (100.0 * outputSize / inputSize) : 0);
    }

    private static boolean hasFlag(final String[] args, final String flag) {
        for (final String arg : args) {
            if (flag.equals(arg)) return true;
        }
        return false;
    }

    private static void die(final String message) {
        System.err.println("ERROR: " + message);
        System.err.println();
        System.err.println(USAGE);
        System.exit(1);
    }
}
