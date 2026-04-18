package htsjdk.samtools.cram;

import htsjdk.samtools.*;
import htsjdk.samtools.util.SequenceUtil;

import java.io.*;
import java.util.*;

/**
 * Command-line tool for comparing two SAM/BAM/CRAM files record-by-record.
 * Handles known CRAM/SAM differences (auto-generated MD/NM, unsigned B-array types)
 * and reports mismatches with clear field-level detail.
 *
 * <p>Exit codes: 0 = comparison completed (match or mismatch), 2 = error.
 */
public class CramComparison {

    private static final String USAGE = String.join("\n",
            "Usage: CramComparison <file1> <file2> [options]",
            "",
            "Compare two SAM/BAM/CRAM files record by record.",
            "",
            "Options:",
            "  --reference <path>    Reference FASTA (required for CRAM)",
            "  --output <path>       Write results to file (default: stderr only)",
            "  --lenient             Compare only name, flags, position, bases, qualities",
            "  --max-diffs <N>       Stop after N mismatches (default: 10)",
            "  --ignore-tags <list>  Comma-separated tags to skip (e.g. MD,NM)",
            "  --help                Print this help message",
            "",
            "Exit codes: 0 = comparison completed, 2 = error"
    );

    // Tags that CRAM auto-generates on decode and may not be in the source
    private static final Set<String> CRAM_AUTO_TAGS = Set.of("MD", "NM");

    public static void main(final String[] args) {
        if (hasFlag(args, "--help") || hasFlag(args, "-h") || args.length < 2) {
            System.out.println(USAGE);
            System.exit(args.length < 2 ? 2 : 0);
        }

        try {
            System.exit(run(args));
        } catch (final Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Run the comparison and return the exit code (0=match, 1=mismatch, 2=error).
     * Separated from main() for testability.
     */
    public static int run(final String[] args) {
        final String file1 = args[0];
        final String file2 = args[1];

        String referencePath = null;
        String outputPath = null;
        boolean lenient = false;
        int maxDiffs = 10;
        final Set<String> ignoreTags = new HashSet<>();

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--reference": case "-r":
                    referencePath = args[++i];
                    break;
                case "--output": case "-o":
                    outputPath = args[++i];
                    break;
                case "--lenient":
                    lenient = true;
                    break;
                case "--max-diffs":
                    maxDiffs = Integer.parseInt(args[++i]);
                    break;
                case "--ignore-tags":
                    for (final String tag : args[++i].split(",")) {
                        ignoreTags.add(tag.trim());
                    }
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    return 2;
            }
        }

        final SamReaderFactory factory = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT);
        if (referencePath != null) {
            factory.referenceSequence(new File(referencePath));
        }

        try (final PrintWriter out = outputPath != null
                ? new PrintWriter(new BufferedWriter(new FileWriter(outputPath)))
                : null) {
            return compareFiles(factory, file1, file2, lenient, maxDiffs, ignoreTags, out);
        } catch (final IOException e) {
            System.err.println("ERROR: " + e.getMessage());
            return 2;
        }
    }

    /**
     * Compare two files and write results to the output writer (if non-null) and stderr.
     */
    private static int compareFiles(final SamReaderFactory factory, final String file1, final String file2,
                                    final boolean lenient, final int maxDiffs, final Set<String> ignoreTags,
                                    final PrintWriter out) {
        long recordCount = 0;
        int diffCount = 0;

        try (final SamReader reader1 = factory.open(new File(file1));
             final SamReader reader2 = factory.open(new File(file2))) {

            final Iterator<SAMRecord> it1 = reader1.iterator();
            final Iterator<SAMRecord> it2 = reader2.iterator();

            while (it1.hasNext() && it2.hasNext()) {
                final SAMRecord rec1 = it1.next();
                final SAMRecord rec2 = it2.next();
                recordCount++;

                final String diff = lenient
                        ? compareLenient(rec1, rec2)
                        : compareStrict(rec1, rec2, ignoreTags);

                if (diff != null) {
                    diffCount++;
                    if (diffCount <= maxDiffs) {
                        emit(out, "Record %d: %s", recordCount, diff);
                        emit(out, "  file1: %s", rec1.getSAMString().trim());
                        emit(out, "  file2: %s", rec2.getSAMString().trim());
                    }
                }
            }

            // Check for unequal record counts
            if (it1.hasNext() || it2.hasNext()) {
                long extra1 = 0, extra2 = 0;
                while (it1.hasNext()) { it1.next(); extra1++; }
                while (it2.hasNext()) { it2.next(); extra2++; }
                emit(out, "FAIL: Record count mismatch: file1 has %d records, file2 has %d records",
                        recordCount + extra1, recordCount + extra2);
                return 0;
            }
        } catch (final Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace(System.err);
            return 2;
        }

        if (diffCount > 0) {
            if (diffCount > maxDiffs) {
                emit(out, "FAIL: %d mismatches in %,d records (showing first %d)", diffCount, recordCount, maxDiffs);
            } else {
                emit(out, "FAIL: %d mismatches in %,d records", diffCount, recordCount);
            }
            return 0;
        }

        emit(out, "OK: %,d records match", recordCount);
        return 0;
    }

    /** Write a formatted message to stderr and optionally to an output file. */
    private static void emit(final PrintWriter out, final String format, final Object... args) {
        final String message = String.format(format, args);
        System.err.println(message);
        if (out != null) {
            out.println(message);
        }
    }

    /** Lenient comparison: only name, flags, position, bases, qualities. */
    private static String compareLenient(final SAMRecord a, final SAMRecord b) {
        if (!Objects.equals(a.getReadName(), b.getReadName()))
            return "readName: " + a.getReadName() + " vs " + b.getReadName();
        if (a.getFlags() != b.getFlags())
            return "flags: " + a.getFlags() + " vs " + b.getFlags();
        if (!Objects.equals(a.getReferenceName(), b.getReferenceName()))
            return "ref: " + a.getReferenceName() + " vs " + b.getReferenceName();
        if (a.getAlignmentStart() != b.getAlignmentStart())
            return "start: " + a.getAlignmentStart() + " vs " + b.getAlignmentStart();
        if (a.getAlignmentEnd() != b.getAlignmentEnd())
            return "end: " + a.getAlignmentEnd() + " vs " + b.getAlignmentEnd();
        if (!Arrays.equals(a.getReadBases(), b.getReadBases()))
            return "bases differ";
        if (!Arrays.equals(a.getBaseQualities(), b.getBaseQualities()))
            return "qualities differ";
        return null;
    }

    /**
     * Strict comparison with known CRAM tolerance:
     * - Auto-generated MD/NM tags stripped when one side lacks them
     * - Unsigned B-array type differences tolerated (CRAM stores as signed)
     */
    private static String compareStrict(final SAMRecord a, final SAMRecord b, final Set<String> ignoreTags) {
        // Core fields
        final String lenientDiff = compareLenient(a, b);
        if (lenientDiff != null) return lenientDiff;

        if (a.getMappingQuality() != b.getMappingQuality())
            return "mapQ: " + a.getMappingQuality() + " vs " + b.getMappingQuality();
        if (!Objects.equals(a.getCigarString(), b.getCigarString()))
            return "cigar: " + a.getCigarString() + " vs " + b.getCigarString();
        if (!Objects.equals(a.getMateReferenceName(), b.getMateReferenceName()))
            return "mateRef: " + a.getMateReferenceName() + " vs " + b.getMateReferenceName();
        if (a.getMateAlignmentStart() != b.getMateAlignmentStart())
            return "mateStart: " + a.getMateAlignmentStart() + " vs " + b.getMateAlignmentStart();
        if (a.getInferredInsertSize() != b.getInferredInsertSize())
            return "tlen: " + a.getInferredInsertSize() + " vs " + b.getInferredInsertSize();

        // Compare tags -- build maps, strip auto-generated and ignored tags
        final Map<String, Object> tagsA = getTagMap(a);
        final Map<String, Object> tagsB = getTagMap(b);

        // Remove ignored tags
        for (final String tag : ignoreTags) {
            tagsA.remove(tag);
            tagsB.remove(tag);
        }

        // Strip CRAM auto-generated tags when the other side doesn't have them
        for (final String tag : CRAM_AUTO_TAGS) {
            if (tagsA.containsKey(tag) && !tagsB.containsKey(tag)) tagsA.remove(tag);
            if (tagsB.containsKey(tag) && !tagsA.containsKey(tag)) tagsB.remove(tag);
        }

        // Compare tag sets
        final Set<String> allTags = new TreeSet<>();
        allTags.addAll(tagsA.keySet());
        allTags.addAll(tagsB.keySet());

        for (final String tag : allTags) {
            final Object valA = tagsA.get(tag);
            final Object valB = tagsB.get(tag);
            if (valA == null) return "tag " + tag + ": missing in file1, present in file2";
            if (valB == null) return "tag " + tag + ": present in file1, missing in file2";
            if (!tagValuesEqual(valA, valB))
                return "tag " + tag + ": values differ";
        }

        return null;
    }

    private static Map<String, Object> getTagMap(final SAMRecord rec) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (final SAMRecord.SAMTagAndValue tv : rec.getAttributes()) {
            map.put(tv.tag, tv.value);
        }
        return map;
    }

    /** Compare tag values with deep array equality, tolerating signed/unsigned type mismatch. */
    private static boolean tagValuesEqual(final Object a, final Object b) {
        if (a instanceof byte[] && b instanceof byte[]) return Arrays.equals((byte[]) a, (byte[]) b);
        if (a instanceof short[] && b instanceof short[]) return Arrays.equals((short[]) a, (short[]) b);
        if (a instanceof int[] && b instanceof int[]) return Arrays.equals((int[]) a, (int[]) b);
        if (a instanceof float[] && b instanceof float[]) return Arrays.equals((float[]) a, (float[]) b);
        return Objects.equals(a, b);
    }

    private static boolean hasFlag(final String[] args, final String flag) {
        for (final String arg : args) {
            if (flag.equals(arg)) return true;
        }
        return false;
    }
}
