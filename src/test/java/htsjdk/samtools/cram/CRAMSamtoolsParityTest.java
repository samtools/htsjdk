package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.IOUtil;
import htsjdk.utils.SamtoolsTestUtils;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Checks htsjdk's CRAM reading and writing against samtools over the CRAM test corpora in this repository: the
 * hts-specs CRAM suite and the htslib-derived compliance files.
 *
 * <ul>
 *   <li>Each corpus CRAM must decode in htsjdk as it does in samtools.</li>
 *   <li>Each corpus SAM, written to CRAM by htsjdk, must decode in htsjdk and in samtools as samtools' own
 *       SAM to CRAM to SAM round trip does.</li>
 * </ul>
 *
 * Records are compared field by field, tags included, with MD and NM as each decoder generates them. The
 * differences that remain are listed in {@link #KNOWN_DIFFERENCES} with their reason; a listed difference that
 * no longer occurs fails the test too, so the list shrinks as they are fixed. Skipped when samtools is absent.
 */
public class CRAMSamtoolsParityTest extends HtsjdkTest {
    private static final Path SPEC_DIR = Path.of("src/test/resources/htsjdk/hts-specs/test/cram");
    private static final Path SPEC_REFERENCE = SPEC_DIR.resolve("ce.fa");
    private static final Path COMPLIANCE_DIR = Path.of("src/test/resources/htsjdk/samtools/cram");

    /** A difference between htsjdk and samtools that is understood and accepted, for now. */
    private record KnownDifference(String caseName, Set<String> kinds, String reason) {}

    private static final List<KnownDifference> KNOWN_DIFFERENCES = List.of(
            new KnownDifference(
                    "decode spec30/1001_name",
                    Set.of("QNAME"),
                    "names generated for records stored without one: samtools derives them from the file name"),
            new KnownDifference(
                    "decode spec30/0706_tag", Set.of("tag:BC", "tag:BI", "tag:BS"), "unsigned B arrays: #499"),
            new KnownDifference(
                    "round trip decoded by htsjdk spec30/0706_tag",
                    Set.of("tag:BC", "tag:BI", "tag:BS"),
                    "unsigned B arrays: #499"),
            new KnownDifference(
                    "round trip decoded by samtools spec30/0706_tag",
                    Set.of("tag:BC", "tag:BI", "tag:BS"),
                    "unsigned B arrays: #499"),
            new KnownDifference(
                    "decode comp/auxf#values.2.1", Set.of("tag:BC", "tag:BI", "tag:BS"), "unsigned B arrays: #499"),
            new KnownDifference(
                    "decode comp/auxf#values.3.0", Set.of("tag:BC", "tag:BI", "tag:BS"), "unsigned B arrays: #499"),
            new KnownDifference(
                    "round trip decoded by htsjdk comp/auxf#values",
                    Set.of("tag:BC", "tag:BI", "tag:BS"),
                    "unsigned B arrays: #499"),
            new KnownDifference(
                    "round trip decoded by samtools comp/auxf#values",
                    Set.of("tag:BC", "tag:BI", "tag:BS"),
                    "unsigned B arrays: #499"),
            new KnownDifference(
                    "decode comp/xx#repeated.2.1",
                    Set.of("TLEN"),
                    "TLEN across a chain of more than two attached records: #1189"),
            new KnownDifference(
                    "decode comp/xx#repeated.3.0",
                    Set.of("TLEN"),
                    "TLEN across a chain of more than two attached records: #1189"),
            new KnownDifference(
                    "decode comp/md#1.2.1", Set.of("tag:NM"), "NM generated for a read with padding: #1187"),
            new KnownDifference(
                    "decode comp/md#1.3.0", Set.of("tag:NM"), "NM generated for a read with padding: #1187"),
            new KnownDifference(
                    "round trip decoded by htsjdk comp/md#1",
                    Set.of("tag:NM"),
                    "NM generated for a read with padding: #1187"),
            new KnownDifference(
                    "decode comp/amb#amb.3.0",
                    Set.of("tag:MD", "tag:NM"),
                    "MD and NM generated against a reference with ambiguity codes"),
            new KnownDifference(
                    "round trip decoded by samtools spec30/1200_overflow",
                    Set.of("tag:MD"),
                    "htsjdk writes the bases past the end of the reference as implicit matches to 'N', which"
                            + " samtools counts in MD; htslib writes them as explicit bases"),
            new KnownDifference(
                    "decode comp/xx#minimal.2.1",
                    Set.of("tag:MD", "tag:NM"),
                    "samtools generates MD and NM for records without bases in CRAM 2.1, which cannot mark"
                            + " bases as unknown"),
            new KnownDifference(
                    "round trip decoded by htsjdk comp/xx#minimal",
                    Set.of("FLAG", "MAPQ"),
                    "samtools' SAM parser turns a mapped read without a CIGAR into an unmapped one"),
            new KnownDifference(
                    "round trip decoded by samtools comp/xx#minimal",
                    Set.of("FLAG", "MAPQ"),
                    "samtools' SAM parser turns a mapped read without a CIGAR into an unmapped one"));

    /** Corpus SAMs that samtools cannot convert to CRAM, so they have no round trip to compare with. */
    private static final Map<String, String> SAMTOOLS_CANNOT_WRITE = Map.of(
            "spec30/0001_empty_eof", "no @SQ lines",
            "comp/xx#blank", "no @SQ lines",
            "comp/amb#amb", "the @SQ M5 does not match the reference");

    private Path tempDir;

    @BeforeClass
    public void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory("CRAMSamtoolsParityTest");
    }

    @AfterClass
    public void deleteTempDir() {
        if (tempDir != null) IOUtil.recursiveDelete(tempDir);
    }

    @DataProvider(name = "corpusCrams")
    public Object[][] corpusCrams() throws IOException {
        final List<Object[]> cases = new ArrayList<>();
        for (final Path cram : list(SPEC_DIR.resolve("3.0/passed"), ".cram")) {
            cases.add(new Object[] {"spec30/" + baseName(cram, ".cram"), cram, SPEC_REFERENCE});
        }
        for (final Path cram : list(SPEC_DIR.resolve("3.1/passed"), ".cram")) {
            cases.add(new Object[] {"spec31/" + baseName(cram, ".cram"), cram, SPEC_REFERENCE});
        }
        for (final Path cram : list(COMPLIANCE_DIR, ".cram")) {
            final String name = baseName(cram, ".cram");
            final Path reference = complianceReference(name);
            if (reference != null) cases.add(new Object[] {"comp/" + name, cram, reference});
        }
        return cases.toArray(Object[][]::new);
    }

    @DataProvider(name = "corpusSams")
    public Object[][] corpusSams() throws IOException {
        final List<Object[]> cases = new ArrayList<>();
        for (final Path sam : list(SPEC_DIR.resolve("3.0/passed"), ".sam")) {
            cases.add(new Object[] {"spec30/" + baseName(sam, ".sam"), sam, SPEC_REFERENCE});
        }
        for (final Path sam : list(COMPLIANCE_DIR, ".sam")) {
            final String name = baseName(sam, ".sam");
            final Path reference = complianceReference(name);
            if (reference != null) cases.add(new Object[] {"comp/" + name, sam, reference});
        }
        return cases.toArray(Object[][]::new);
    }

    @Test(dataProvider = "corpusCrams")
    public void corpusCramDecodesAsInSamtools(final String name, final Path cram, final Path reference)
            throws IOException {
        requireSamtools();
        final List<SAMRecord> samtoolsRecords = decodeWithSamtools(cram, reference);
        assertSameRecords("decode " + name, readWithHtsjdk(cram, reference), samtoolsRecords);
    }

    @Test(dataProvider = "corpusSams")
    public void htsjdkCramDecodesAsSamtoolsRoundTrip(final String name, final Path sam, final Path reference)
            throws IOException {
        requireSamtools();
        if (SAMTOOLS_CANNOT_WRITE.containsKey(name)) {
            throw new SkipException(name + ": " + SAMTOOLS_CANNOT_WRITE.get(name));
        }
        final Path samtoolsCram = tempDir.resolve(safeName(name) + ".samtools.cram");
        SamtoolsTestUtils.executeSamToolsCommand(String.format(
                "view --no-PG -C -T %s -o %s %s",
                reference.toAbsolutePath(), samtoolsCram.toAbsolutePath(), sam.toAbsolutePath()));
        final List<SAMRecord> samtoolsRoundTrip = decodeWithSamtools(samtoolsCram, reference);

        final Path htsjdkCram = tempDir.resolve(safeName(name) + ".htsjdk.cram");
        writeWithHtsjdk(sam, reference, htsjdkCram);
        assertSameRecords(
                "round trip decoded by htsjdk " + name, readWithHtsjdk(htsjdkCram, reference), samtoolsRoundTrip);
        assertSameRecords(
                "round trip decoded by samtools " + name, decodeWithSamtools(htsjdkCram, reference), samtoolsRoundTrip);
    }

    @Test
    public void everyKnownDifferenceNamesACorpusCase() throws IOException {
        final Set<String> caseNames = new TreeSet<>();
        for (final Object[] c : corpusCrams()) caseNames.add("decode " + c[0]);
        for (final Object[] c : corpusSams()) {
            caseNames.add("round trip decoded by htsjdk " + c[0]);
            caseNames.add("round trip decoded by samtools " + c[0]);
        }
        for (final KnownDifference known : KNOWN_DIFFERENCES) {
            Assert.assertTrue(caseNames.contains(known.caseName()), "no corpus case " + known.caseName());
        }
    }

    /**
     * Assert that the differences between two decodings of the same records are exactly the known ones for this
     * case: an unexpected difference fails, and so does a known one that no longer occurs.
     */
    private static void assertSameRecords(
            final String caseName, final List<SAMRecord> actual, final List<SAMRecord> expected) {
        final Map<String, String> differences = new TreeMap<>(); // kind -> first example
        if (actual.size() != expected.size()) {
            differences.put("COUNT", expected.size() + " -> " + actual.size());
        } else {
            for (int i = 0; i < actual.size(); i++) {
                final String where = "record " + i + " (" + expected.get(i).getReadName() + "): ";
                recordDifferences(actual.get(i), expected.get(i))
                        .forEach((kind, detail) -> differences.putIfAbsent(kind, where + detail));
            }
        }

        final Set<String> known = new TreeSet<>();
        KNOWN_DIFFERENCES.stream().filter(k -> k.caseName().equals(caseName)).forEach(k -> known.addAll(k.kinds()));
        final StringBuilder problems = new StringBuilder();
        differences.forEach((kind, example) -> {
            if (!known.contains(kind))
                problems.append("\n  unexpected ").append(kind).append(": ").append(example);
        });
        known.forEach(kind -> {
            if (!differences.containsKey(kind)) {
                problems.append("\n  known difference ").append(kind).append(" no longer occurs; remove it");
            }
        });
        Assert.assertTrue(problems.length() == 0, caseName + " (actual vs samtools):" + problems);
    }

    /** The fields in which two records differ, as a map from field (or "tag:XX") to "expected -> actual". */
    private static Map<String, String> recordDifferences(final SAMRecord actual, final SAMRecord expected) {
        final Map<String, String> differences = new LinkedHashMap<>();
        final Map<String, Function<SAMRecord, Object>> fields = new LinkedHashMap<>();
        fields.put("QNAME", SAMRecord::getReadName);
        fields.put("FLAG", SAMRecord::getFlags);
        fields.put("RNAME", SAMRecord::getReferenceName);
        fields.put("POS", SAMRecord::getAlignmentStart);
        fields.put("MAPQ", SAMRecord::getMappingQuality);
        fields.put("CIGAR", SAMRecord::getCigarString);
        fields.put("RNEXT", SAMRecord::getMateReferenceName);
        fields.put("PNEXT", SAMRecord::getMateAlignmentStart);
        fields.put("TLEN", SAMRecord::getInferredInsertSize);
        fields.put("SEQ", SAMRecord::getReadString);
        fields.put("QUAL", SAMRecord::getBaseQualityString);
        fields.forEach((field, getter) -> {
            final Object e = getter.apply(expected);
            final Object a = getter.apply(actual);
            if (!Objects.equals(e, a)) differences.put(field, e + " -> " + a);
        });

        final Map<String, Object> actualTags = tags(actual);
        final Map<String, Object> expectedTags = tags(expected);
        final Set<String> tagNames = new TreeSet<>(actualTags.keySet());
        tagNames.addAll(expectedTags.keySet());
        for (final String tag : tagNames) {
            final Object e = expectedTags.get(tag);
            final Object a = actualTags.get(tag);
            if (!tagValuesEqual(e, a)
                    || (e != null
                            && a != null
                            && expected.isUnsignedArrayAttribute(tag) != actual.isUnsignedArrayAttribute(tag))) {
                differences.put("tag:" + tag, render(e) + " -> " + render(a));
            }
        }
        return differences;
    }

    private static Map<String, Object> tags(final SAMRecord record) {
        final Map<String, Object> tags = new HashMap<>();
        for (final SAMRecord.SAMTagAndValue tagAndValue : record.getAttributes()) {
            tags.put(tagAndValue.tag, tagAndValue.value);
        }
        return tags;
    }

    private static boolean tagValuesEqual(final Object a, final Object b) {
        if (a instanceof byte[] x && b instanceof byte[] y) return Arrays.equals(x, y);
        if (a instanceof short[] x && b instanceof short[] y) return Arrays.equals(x, y);
        if (a instanceof int[] x && b instanceof int[] y) return Arrays.equals(x, y);
        if (a instanceof float[] x && b instanceof float[] y) return Arrays.equals(x, y);
        return Objects.equals(a, b);
    }

    private static String render(final Object value) {
        if (value instanceof byte[] x) return Arrays.toString(x);
        if (value instanceof short[] x) return Arrays.toString(x);
        if (value instanceof int[] x) return Arrays.toString(x);
        if (value instanceof float[] x) return Arrays.toString(x);
        return String.valueOf(value);
    }

    private List<SAMRecord> decodeWithSamtools(final Path cram, final Path reference) throws IOException {
        final Path sam = Files.createTempFile(tempDir, "samtools", ".sam");
        SamtoolsTestUtils.executeSamToolsCommand(String.format(
                "view --no-PG -h -T %s -o %s %s",
                reference.toAbsolutePath(), sam.toAbsolutePath(), cram.toAbsolutePath()));
        return readWithHtsjdk(sam, reference);
    }

    private static List<SAMRecord> readWithHtsjdk(final Path path, final Path reference) throws IOException {
        try (final SamReader reader = readerFactory(reference).open(path)) {
            final List<SAMRecord> records = new ArrayList<>();
            reader.forEach(records::add);
            return records;
        }
    }

    private static void writeWithHtsjdk(final Path sam, final Path reference, final Path cram) throws IOException {
        try (final SamReader reader = readerFactory(reference).open(sam)) {
            final SAMFileHeader header = reader.getFileHeader();
            try (final SAMFileWriter writer =
                    new SAMFileWriterFactory().makeCRAMWriter(header, true, cram, reference)) {
                reader.forEach(writer::addAlignment);
            }
        }
    }

    private static SamReaderFactory readerFactory(final Path reference) {
        return SamReaderFactory.make()
                .validationStringency(ValidationStringency.SILENT)
                .referenceSequence(reference);
    }

    private static void requireSamtools() {
        if (!SamtoolsTestUtils.isSamtoolsAvailable()) throw new SkipException("samtools is not available");
    }

    /** Files in a directory with the given extension, sorted by name. */
    private static List<Path> list(final Path dir, final String extension) throws IOException {
        final List<Path> paths = new ArrayList<>();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + extension)) {
            stream.forEach(paths::add);
        }
        paths.sort(null);
        return paths;
    }

    private static String baseName(final Path path, final String extension) {
        final String fileName = path.getFileName().toString();
        return fileName.substring(0, fileName.length() - extension.length());
    }

    /**
     * The reference for an htslib-derived compliance file, named for the part of the file name before '#'
     * (e.g. {@code c1.fa} for {@code c1#noseq.3.0.cram}), or null if the file isn't one of them.
     */
    private static Path complianceReference(final String name) {
        final int hash = name.indexOf('#');
        if (hash < 0) return null;
        final Path reference = COMPLIANCE_DIR.resolve(name.substring(0, hash) + ".fa");
        return Files.exists(reference) ? reference : null;
    }

    private static String safeName(final String name) {
        return name.replace('/', '_').replace('#', '_');
    }
}
