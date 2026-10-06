package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.TribbleException;
import htsjdk.utils.BcftoolsTestUtils;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeLikelihoods;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Runs the reader over the hts-specs VCF conformance corpus vendored under {@code src/test/resources/htsjdk/hts-specs}.
 *
 * <p>Every file in a {@code passed/} directory is valid for its version and must decode completely, genotypes
 * included. Every file in a {@code failed/} directory is invalid in some way; the reader is not required to reject it,
 * but it must either decode it or throw a {@link TribbleException} -- never anything else, and never hang.
 *
 * <p>Files the reader cannot handle yet are listed in {@link #PASSED_NOT_YET_DECODABLE} and
 * {@link #FAILED_WITH_WRONG_EXCEPTION}, with the reason. A listed file that starts behaving is reported as a failure so
 * the entry gets removed: the lists may only ever shrink. {@link #PASSED_BUT_INVALID} holds the corpus's own mistakes.
 *
 * <p>Every {@code passed/} file that decodes is also written back out, as VCF and as BCF, and must come back the same;
 * the files that cannot are listed, the same way, next to those tests.
 */
public class VCFSpecCorpusTest extends HtsjdkTest {
    private static final Path CORPUS = Paths.get("src/test/resources/htsjdk/hts-specs/test/vcf");
    private static final List<String> VERSIONS = List.of("4.1", "4.2", "4.3", "4.5");

    private static final String GT_NAMES_UNDEFINED_ALLELE =
            "the second sample at 1:1900 has GT 0|1 on a record whose ALT is '.': the genotype names an allele the"
                    + " record does not define (htslib accepts it only because it stores GT as bare integers)";
    /** {@code passed/} files the reader cannot fully decode today, keyed by path relative to the corpus root. */
    private static final Map<String, String> PASSED_NOT_YET_DECODABLE = Map.ofEntries();

    /**
     * {@code passed/} files that are in fact invalid and that the reader is right to reject. Unlike the list above,
     * these are not expected to change.
     */
    private static final Map<String, String> PASSED_BUT_INVALID = Map.ofEntries(
            Map.entry("4.1/passed/passed_body_alt.vcf", GT_NAMES_UNDEFINED_ALLELE),
            Map.entry("4.2/passed/passed_body_alt.vcf", GT_NAMES_UNDEFINED_ALLELE),
            Map.entry("4.3/passed/passed_body_alt.vcf", GT_NAMES_UNDEFINED_ALLELE));

    /** {@code failed/} files where the reader throws something other than a TribbleException today. */
    private static final Map<String, String> FAILED_WITH_WRONG_EXCEPTION = Map.of();

    @DataProvider
    public Object[][] passedFiles() throws IOException {
        return corpusFiles("passed");
    }

    @DataProvider
    public Object[][] failedFiles() throws IOException {
        return corpusFiles("failed");
    }

    @Test(dataProvider = "passedFiles", timeOut = 30_000)
    public void passedFileDecodesFully(final String relativePath) {
        final String knownReason = PASSED_NOT_YET_DECODABLE.get(relativePath);
        final DecodeFailure failure = decodeFully(CORPUS.resolve(relativePath));
        if (failure != null) {
            if (knownReason != null) {
                return;
            }
            if (PASSED_BUT_INVALID.containsKey(relativePath)) {
                Assert.assertTrue(
                        failure.cause instanceof TribbleException,
                        relativePath + " is invalid and must be rejected with a TribbleException, not "
                                + failure.cause);
                return;
            }
            throw new AssertionError(
                    relativePath + " should decode fully but " + failure.where + " threw " + failure.cause,
                    failure.cause);
        }
        Assert.assertNull(
                knownReason,
                relativePath + " now decodes; remove it from PASSED_NOT_YET_DECODABLE (" + knownReason + ")");
        Assert.assertFalse(
                PASSED_BUT_INVALID.containsKey(relativePath),
                relativePath + " is invalid but decoded without complaint: " + PASSED_BUT_INVALID.get(relativePath));
    }

    @Test(dataProvider = "failedFiles", timeOut = 30_000)
    public void failedFileDecodesOrThrowsTribbleException(final String relativePath) {
        final String knownReason = FAILED_WITH_WRONG_EXCEPTION.get(relativePath);
        final DecodeFailure failure = decodeFully(CORPUS.resolve(relativePath));
        if (failure != null && !(failure.cause instanceof TribbleException)) {
            if (knownReason != null) {
                return;
            }
            throw new AssertionError(
                    relativePath + ": " + failure.where + " threw "
                            + failure.cause.getClass().getName() + ": " + failure.cause.getMessage(),
                    failure.cause);
        }
        Assert.assertNull(
                knownReason,
                relativePath + " now behaves; remove it from FAILED_WITH_WRONG_EXCEPTION (" + knownReason + ")");
    }

    /** What was being decoded when it went wrong, for the assertion message. */
    private static final class DecodeFailure {
        final String where;
        final Throwable cause;

        DecodeFailure(final String where, final Throwable cause) {
            this.where = where;
            this.cause = cause;
        }
    }

    /**
     * Decodes the header and every record, and forces the lazily parsed genotypes to be decoded too. Typed decoding
     * ({@link VariantContext#fullyDecode}) is deliberately not used: it demands a header line for every key, and the
     * corpus's valid files routinely use keys they never declare.
     *
     * @return null when everything decoded, else the first failure
     */
    private static DecodeFailure decodeFully(final Path vcf) {
        try (final VCFFileReader reader = new VCFFileReader(vcf, false)) {
            reader.getFileHeader();
            // creating the iterator decodes the first record, and each next() decodes the record after the one it
            // returns
            String where = "the first record";
            // the iterator reads from a stream of its own, which closing the reader does not close
            try (final CloseableIterator<VariantContext> records = reader.iterator()) {
                while (records.hasNext()) {
                    where = "the record after " + where;
                    final VariantContext vc = records.next();
                    where = "the record at " + vc.getContig() + ":" + vc.getStart();
                    decodeLazyFields(vc);
                }
            } catch (final Throwable t) {
                return new DecodeFailure(where, t);
            }
        } catch (final Throwable t) {
            return new DecodeFailure("opening the file or reading its header", t);
        }
        return null;
    }

    /** Forces the INFO fields and the lazily parsed genotypes of a record to be decoded. */
    private static void decodeLazyFields(final VariantContext vc) {
        vc.getAttributes();
        for (final Genotype genotype : vc.getGenotypes()) {
            genotype.getAlleles();
            genotype.getExtendedAttributes();
        }
    }

    private static Object[][] corpusFiles(final String outcome) throws IOException {
        final List<Object[]> files = new ArrayList<>();
        for (final String version : VERSIONS) {
            final Path dir = CORPUS.resolve(version).resolve(outcome);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (final Stream<Path> listing = Files.list(dir)) {
                listing.filter(p -> p.getFileName().toString().endsWith(".vcf"))
                        .sorted()
                        .forEach(p ->
                                files.add(new Object[] {CORPUS.relativize(p).toString()}));
            }
        }
        return files.toArray(new Object[0][]);
    }

    // Round trips: each file is read with its genotypes decoded and written back out, as VCF and as BCF, with a header
    // line for every key and contig it uses but does not declare.

    private static final String SYNTHETIC_DESCRIPTION = "Dummy";

    private static final int QUAL_COLUMN = 5;
    private static final int FILTER_COLUMN = 6;
    private static final int INFO_COLUMN = 7;
    private static final int FORMAT_COLUMN = 8;

    /** An exception a listed file is known to fail with, recognised by its type and a fragment of its message. */
    private static final class KnownException {
        final Class<? extends Throwable> type;
        final String messageFragment;
        final String reason;

        KnownException(final Class<? extends Throwable> type, final String messageFragment, final String reason) {
            this.type = type;
            this.messageFragment = messageFragment;
            this.reason = reason;
        }

        boolean matches(final Throwable thrown) {
            return type.isInstance(thrown)
                    && String.valueOf(thrown.getMessage()).contains(messageFragment);
        }

        @Override
        public String toString() {
            return type.getSimpleName() + " containing \"" + messageFragment + "\" (" + reason + ")";
        }
    }

    /**
     * A way two files' records are known to differ, recognised by a normalisation of each record line that removes
     * exactly that difference.
     */
    private static final class KnownDifference {
        final UnaryOperator<String> normalisation;
        final String reason;

        KnownDifference(final UnaryOperator<String> normalisation, final String reason) {
            this.normalisation = normalisation;
            this.reason = reason;
        }
    }

    private static final KnownException CONTIG_IN_ANGLE_BRACKETS = new KnownException(
            IllegalArgumentException.class,
            "ID cannot contain angle brackets",
            "a CHROM in angle brackets (<1>) cannot be declared, as VCFContigHeaderLine rejects the ID, and BCF needs"
                    + " every contig declared; htslib rejects the name too, so bcftools cannot write the file as BCF"
                    + " either");
    private static final KnownException NO_CONTIG_LINES = new KnownException(
            IllegalStateException.class,
            "Cannot write BCF2 file with missing contig lines",
            "the file has no records and so no contigs to declare, and the BCF writer refuses a header without contig"
                    + " lines, where bcftools writes an empty contig dictionary");
    private static final KnownException INFO_NUMBER_G_COUNTED = new KnownException(
            TribbleException.InvalidHeader.class,
            "Discordant field size detected for field MY at 18:200",
            "the BCF writer requires an Integer or Float INFO field to have as many values as its Number says and"
                    + " rejects MY=0.777,-0.123 (Number=G, one ALT), which the corpus counts valid because G is not"
                    + " defined for INFO, and which bcftools writes as it is");
    private static final KnownException EMPTY_LOCAL_ALLELES = new KnownException(
            TribbleException.class,
            "Could not decode field LAA",
            "the BCF writer throws on the empty LAA and LEC values (a sample written ':') that VCF 4.5 allows for a"
                    + " sample with no local alleles, where bcftools reads them as missing");

    private static final KnownDifference FLOATS_REFORMATTED = new KnownDifference(
            VCFSpecCorpusTest::withDecimalsFormattedAsFloats,
            "a Float value read from VCF text is written back as it was read (-0.13, -0.00), but read from BCF it is"
                    + " a number, which the VCF writer formats with three decimals below 1 and without the sign of"
                    + " negative zero (-0.130, 0.00)");
    private static final KnownDifference FILTERS_SORTED = new KnownDifference(
            VCFSpecCorpusTest::withFiltersSorted,
            "the VCF writer sorts a record's filters, writing q10;dp10 as dp10;q10 and STD_FILTER;PASS as"
                    + " PASS;STD_FILTER, where the file keeps its order and htsjdk's BCF has the order of the HashSet"
                    + " the reader keeps them in");
    private static final KnownDifference QUAL_ROUNDED = new KnownDifference(
            VCFSpecCorpusTest::withQualRounded,
            "the VCF writer rounds QUAL to two decimal places, writing 5.3e-10 as 0, where the file and BCF keep it");

    private static final String FLAG_VALUES_AND_BARE_KEYS =
            "the reader takes a Flag given a value as set or not, so DB=1 is written DB and DB=0 is dropped, and a key"
                    + " used both bare and with a value (H2, H2=0) is declared a String, as htslib declares it, whose"
                    + " bare use the reader reads as missing, so it is written H2=.; bcftools keeps both as written";
    private static final KnownDifference FLAG_VALUES_AND_BARE_KEYS_CHANGED = new KnownDifference(
            withoutInfoKeys("DB", "H2", "H3", "SOMATIC", "VALIDATED", "1000G"), FLAG_VALUES_AND_BARE_KEYS);
    private static final KnownDifference FLAG_VALUES_BARE_KEYS_AND_PERCENT_ENCODING_CHANGED = new KnownDifference(
            withoutInfoKeys("DB", "H2", "H3", "SOMATIC", "VALIDATED", "1000G", "EXPLAIN", "MY3"),
            FLAG_VALUES_AND_BARE_KEYS
                    + "; also, the writer percent-encodes the = in EXPLAIN's value, which VCF 4.3 requires, and the"
                    + " reader decodes MY3's %03 to a control character, which the writer writes as it is, where"
                    + " bcftools leaves both values as written");

    /** {@code passed/} files whose VCF, read back and written again, does not come out the same. */
    private static final Map<String, KnownDifference> VCF_NOT_YET_IDEMPOTENT = Map.of();

    /** {@code passed/} files the BCF writer cannot write. */
    private static final Map<String, KnownException> NOT_YET_WRITABLE_AS_BCF = Map.ofEntries(
            Map.entry("4.1/passed/complexfile_passed_000.vcf", CONTIG_IN_ANGLE_BRACKETS),
            Map.entry("4.1/passed/passed_body_chrom.vcf", CONTIG_IN_ANGLE_BRACKETS),
            Map.entry("4.1/passed/passed_body_id.vcf", CONTIG_IN_ANGLE_BRACKETS),
            Map.entry("4.1/passed/passed_body_info.vcf", INFO_NUMBER_G_COUNTED),
            Map.entry("4.1/passed/passed_body_pos.vcf", CONTIG_IN_ANGLE_BRACKETS),
            Map.entry("4.1/passed/passed_fileformat_header_000.vcf", NO_CONTIG_LINES),
            Map.entry("4.1/passed/passed_fileformat_header_001.vcf", NO_CONTIG_LINES),
            Map.entry("4.2/passed/complexfile_passed_000.vcf", CONTIG_IN_ANGLE_BRACKETS),
            Map.entry("4.2/passed/passed_body_chrom.vcf", CONTIG_IN_ANGLE_BRACKETS),
            Map.entry("4.2/passed/passed_body_id.vcf", CONTIG_IN_ANGLE_BRACKETS),
            Map.entry("4.2/passed/passed_body_info.vcf", INFO_NUMBER_G_COUNTED),
            Map.entry("4.2/passed/passed_body_pos.vcf", CONTIG_IN_ANGLE_BRACKETS),
            Map.entry("4.2/passed/passed_fileformat_header_000.vcf", NO_CONTIG_LINES),
            Map.entry("4.2/passed/passed_fileformat_header_001.vcf", NO_CONTIG_LINES),
            Map.entry("4.3/passed/complexfile_passed_000.vcf", CONTIG_IN_ANGLE_BRACKETS),
            Map.entry("4.3/passed/passed_body_chrom.vcf", CONTIG_IN_ANGLE_BRACKETS),
            Map.entry("4.3/passed/passed_body_id.vcf", CONTIG_IN_ANGLE_BRACKETS),
            Map.entry("4.3/passed/passed_body_info.vcf", INFO_NUMBER_G_COUNTED),
            Map.entry("4.3/passed/passed_body_pos.vcf", CONTIG_IN_ANGLE_BRACKETS),
            Map.entry("4.3/passed/passed_fileformat_header_000.vcf", NO_CONTIG_LINES),
            Map.entry("4.3/passed/passed_fileformat_header_001.vcf", NO_CONTIG_LINES),
            Map.entry("4.5/passed/zero_length_LAA.vcf", EMPTY_LOCAL_ALLELES));

    /** {@code passed/} files whose BCF, read back and written as VCF, differs from the VCF written directly. */
    private static final Map<String, KnownDifference> BCF_NOT_YET_READ_BACK_AS_WRITTEN = Map.ofEntries(
            Map.entry("4.2/passed/passed_body_samples.vcf", FLOATS_REFORMATTED),
            Map.entry("4.3/passed/passed_body_samples.vcf", FLOATS_REFORMATTED));

    /** {@code passed/} files where bcftools reads different records from htsjdk's VCF and from htsjdk's BCF. */
    private static final Map<String, KnownDifference> BCFTOOLS_NOT_YET_READING_THE_SAME_RECORDS = Map.ofEntries(
            Map.entry("4.1/passed/passed_body_filter.vcf", FILTERS_SORTED),
            Map.entry("4.1/passed/passed_body_qual.vcf", QUAL_ROUNDED),
            Map.entry("4.2/passed/passed_body_filter.vcf", FILTERS_SORTED),
            Map.entry("4.2/passed/passed_body_qual.vcf", QUAL_ROUNDED),
            Map.entry("4.3/passed/passed_body_filter.vcf", FILTERS_SORTED),
            Map.entry("4.3/passed/passed_body_qual.vcf", QUAL_ROUNDED));

    /** {@code passed/} files where bcftools reads different records from the file itself and from htsjdk's VCF. */
    private static final Map<String, KnownDifference> VCF_NOT_YET_MATCHING_THE_CORPUS_FILE = Map.ofEntries(
            Map.entry("4.1/passed/complexfile_passed_000.vcf", FILTERS_SORTED),
            Map.entry("4.1/passed/passed_body_filter.vcf", FILTERS_SORTED),
            Map.entry("4.1/passed/passed_body_info.vcf", FLAG_VALUES_AND_BARE_KEYS_CHANGED),
            Map.entry("4.1/passed/passed_body_qual.vcf", QUAL_ROUNDED),
            Map.entry("4.2/passed/complexfile_passed_000.vcf", FILTERS_SORTED),
            Map.entry("4.2/passed/passed_body_filter.vcf", FILTERS_SORTED),
            Map.entry("4.2/passed/passed_body_info.vcf", FLAG_VALUES_AND_BARE_KEYS_CHANGED),
            Map.entry("4.2/passed/passed_body_qual.vcf", QUAL_ROUNDED),
            Map.entry("4.3/passed/complexfile_passed_000.vcf", FILTERS_SORTED),
            Map.entry("4.3/passed/passed_body_filter.vcf", FILTERS_SORTED),
            Map.entry("4.3/passed/passed_body_info.vcf", FLAG_VALUES_BARE_KEYS_AND_PERCENT_ENCODING_CHANGED),
            Map.entry("4.3/passed/passed_body_qual.vcf", QUAL_ROUNDED));

    /** The {@code passed/} files that decode, which are the ones there is something to write back out. */
    @DataProvider
    public Object[][] decodablePassedFiles() throws IOException {
        return Arrays.stream(passedFiles())
                .filter(file ->
                        !PASSED_BUT_INVALID.containsKey(file[0]) && !PASSED_NOT_YET_DECODABLE.containsKey(file[0]))
                .toArray(Object[][]::new);
    }

    /** The decodable {@code passed/} files the BCF writer can write, which are the ones there is a BCF to read. */
    @DataProvider
    public Object[][] bcfWritablePassedFiles() throws IOException {
        return Arrays.stream(decodablePassedFiles())
                .filter(file -> !NOT_YET_WRITABLE_AS_BCF.containsKey(file[0]))
                .toArray(Object[][]::new);
    }

    @Test
    public void everyListedFileIsOneTheListsTestsRun() throws IOException {
        assertListedFilesAreProvided(PASSED_NOT_YET_DECODABLE, "PASSED_NOT_YET_DECODABLE", passedFiles());
        assertListedFilesAreProvided(PASSED_BUT_INVALID, "PASSED_BUT_INVALID", passedFiles());
        assertListedFilesAreProvided(FAILED_WITH_WRONG_EXCEPTION, "FAILED_WITH_WRONG_EXCEPTION", failedFiles());
        assertListedFilesAreProvided(VCF_NOT_YET_IDEMPOTENT, "VCF_NOT_YET_IDEMPOTENT", decodablePassedFiles());
        assertListedFilesAreProvided(NOT_YET_WRITABLE_AS_BCF, "NOT_YET_WRITABLE_AS_BCF", decodablePassedFiles());
        assertListedFilesAreProvided(
                BCF_NOT_YET_READ_BACK_AS_WRITTEN, "BCF_NOT_YET_READ_BACK_AS_WRITTEN", bcfWritablePassedFiles());
        assertListedFilesAreProvided(
                BCFTOOLS_NOT_YET_READING_THE_SAME_RECORDS,
                "BCFTOOLS_NOT_YET_READING_THE_SAME_RECORDS",
                bcfWritablePassedFiles());
        assertListedFilesAreProvided(
                VCF_NOT_YET_MATCHING_THE_CORPUS_FILE, "VCF_NOT_YET_MATCHING_THE_CORPUS_FILE", decodablePassedFiles());
    }

    /** Guards against a moved or emptied corpus, which would leave the data-driven tests with nothing to run. */
    @Test
    public void everyDataProviderYieldsMostOfTheCorpus() throws IOException {
        Assert.assertTrue(passedFiles().length >= 70, "passed/ files: " + passedFiles().length);
        Assert.assertTrue(failedFiles().length >= 600, "failed/ files: " + failedFiles().length);
        Assert.assertTrue(decodablePassedFiles().length >= 70, "decodable files: " + decodablePassedFiles().length);
        Assert.assertTrue(
                bcfWritablePassedFiles().length >= 50, "BCF-writable files: " + bcfWritablePassedFiles().length);
    }

    @Test(dataProvider = "decodablePassedFiles", timeOut = 30_000)
    public void passedFileWrittenAsVcfComesOutTheSameWhenReadAndWrittenAgain(final String relativePath)
            throws IOException {
        final Path dir = Files.createTempDirectory("VCFSpecCorpusTest.");
        try {
            final Path vcf = corpusFileAsVcf(relativePath, dir);
            assertSameUnlessListed(
                    relativePath,
                    Files.readAllLines(rewrittenAsVcf(vcf, dir)),
                    Files.readAllLines(vcf),
                    VCF_NOT_YET_IDEMPOTENT,
                    "VCF_NOT_YET_IDEMPOTENT");
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test(dataProvider = "decodablePassedFiles", timeOut = 30_000)
    public void passedFileCanBeWrittenAsBcf(final String relativePath) throws IOException {
        final KnownException known = NOT_YET_WRITABLE_AS_BCF.get(relativePath);
        final Path dir = Files.createTempDirectory("VCFSpecCorpusTest.");
        try {
            corpusFileAsBcf(relativePath, dir);
        } catch (final Exception e) {
            if (known == null) {
                throw new AssertionError(relativePath + " could not be written as BCF: " + e, e);
            }
            if (!known.matches(e)) {
                throw new AssertionError(relativePath + " should fail with " + known + " but threw " + e, e);
            }
            return;
        } finally {
            IOUtil.recursiveDelete(dir);
        }
        Assert.assertNull(
                known,
                relativePath + " can now be written as BCF; remove it from NOT_YET_WRITABLE_AS_BCF (" + known + ")");
    }

    /**
     * Compares the records as text, after the headers: the headers differ legitimately, since only the BCF's declares
     * every contig, and the text is exactly what a user sees, so any value lost, added or reformatted on the way
     * through BCF shows up.
     */
    @Test(dataProvider = "bcfWritablePassedFiles", timeOut = 30_000)
    public void passedFileWrittenAsBcfReadsBackAsTheVcfWrittenDirectly(final String relativePath) throws IOException {
        final Path dir = Files.createTempDirectory("VCFSpecCorpusTest.");
        try {
            assertSameUnlessListed(
                    relativePath,
                    recordLines(rewrittenAsVcf(corpusFileAsBcf(relativePath, dir), dir)),
                    recordLines(corpusFileAsVcf(relativePath, dir)),
                    BCF_NOT_YET_READ_BACK_AS_WRITTEN,
                    "BCF_NOT_YET_READ_BACK_AS_WRITTEN");
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test(dataProvider = "bcfWritablePassedFiles", timeOut = 30_000)
    public void bcftoolsReadsTheSameRecordsFromTheVcfAndTheBcf(final String relativePath) throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) {
            throw new SkipException("bcftools not available");
        }
        final Path dir = Files.createTempDirectory("VCFSpecCorpusTest.");
        try {
            assertSameUnlessListed(
                    relativePath,
                    bcftoolsRecordLines(corpusFileAsBcf(relativePath, dir)),
                    bcftoolsRecordLines(corpusFileAsVcf(relativePath, dir)),
                    BCFTOOLS_NOT_YET_READING_THE_SAME_RECORDS,
                    "BCFTOOLS_NOT_YET_READING_THE_SAME_RECORDS");
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    /**
     * Compares what bcftools reads from the corpus file with what it reads from htsjdk's VCF of it, once two deliberate
     * conventions of htsjdk's that would make nearly every file differ are applied to both: the VCF writer sorts the
     * INFO keys, and the reader reads GL as PL.
     */
    @Test(dataProvider = "decodablePassedFiles", timeOut = 30_000)
    public void bcftoolsReadsTheSameRecordsFromTheCorpusFileAndTheVcf(final String relativePath) throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) {
            throw new SkipException("bcftools not available");
        }
        final Path dir = Files.createTempDirectory("VCFSpecCorpusTest.");
        try {
            assertSameUnlessListed(
                    relativePath,
                    withHtsjdkConventions(bcftoolsRecordLines(corpusFileAsVcf(relativePath, dir))),
                    withHtsjdkConventions(bcftoolsRecordLines(CORPUS.resolve(relativePath))),
                    VCF_NOT_YET_MATCHING_THE_CORPUS_FILE,
                    "VCF_NOT_YET_MATCHING_THE_CORPUS_FILE");
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    /**
     * Asserts that a file's lines come out as expected or, for a file listed with a known difference, that they don't
     * and that the listed difference is the only one: normalising every record line for it makes the two the same.
     */
    private static void assertSameUnlessListed(
            final String relativePath,
            final List<String> actual,
            final List<String> expected,
            final Map<String, KnownDifference> knownDifferences,
            final String listName) {
        final KnownDifference known = knownDifferences.get(relativePath);
        if (known == null) {
            Assert.assertEquals(actual, expected, relativePath);
            return;
        }
        Assert.assertNotEquals(
                actual,
                expected,
                relativePath + " now matches; remove it from " + listName + " (" + known.reason + ")");
        Assert.assertEquals(
                normalised(actual, known),
                normalised(expected, known),
                relativePath + " differs in more than the way " + listName + " lists (" + known.reason + ")");
    }

    private static void assertListedFilesAreProvided(
            final Map<String, ?> knownFailures, final String listName, final Object[][] providedFiles) {
        final Set<Object> provided =
                Arrays.stream(providedFiles).map(file -> file[0]).collect(Collectors.toSet());
        for (final String listed : knownFailures.keySet()) {
            Assert.assertTrue(
                    provided.contains(listed),
                    listName + " lists " + listed + ", which is not among the files its test runs on");
        }
    }

    /** A file's header and its records, with their INFO fields and genotypes decoded. */
    private static final class DecodedFile {
        final VCFHeader header;
        final List<VariantContext> records;

        DecodedFile(final VCFHeader header, final List<VariantContext> records) {
            this.header = header;
            this.records = records;
        }
    }

    private static DecodedFile readDecoded(final Path vcfOrBcf) {
        try (final VCFFileReader reader = new VCFFileReader(vcfOrBcf, false);
                final CloseableIterator<VariantContext> iterator = reader.iterator()) {
            final List<VariantContext> records = new ArrayList<>();
            while (iterator.hasNext()) {
                final VariantContext vc = iterator.next();
                decodeLazyFields(vc);
                records.add(vc);
            }
            return new DecodedFile(reader.getFileHeader(), records);
        }
    }

    /**
     * Reads a corpus file with a header line added for every INFO, FILTER and FORMAT key it uses but does not declare,
     * as htslib adds one when it meets such a key; the BCF writer needs every key in its dictionary. The lines go into
     * the file's text, which is then read again, so that each record is decoded as the header it is written with
     * says.
     */
    private static DecodedFile readCorpusFileWithEveryKeyDeclared(final String relativePath, final Path dir)
            throws IOException {
        final Path corpusFile = CORPUS.resolve(relativePath);
        final List<String> lines = new ArrayList<>(Files.readAllLines(corpusFile));
        final int columnHeaderLine = IntStream.range(0, lines.size())
                .filter(i -> lines.get(i).startsWith("#CHROM"))
                .findFirst()
                .orElseThrow();
        lines.addAll(
                columnHeaderLine,
                undeclaredKeyLines(readDecoded(corpusFile)).stream()
                        .map(line -> "##" + line)
                        .collect(Collectors.toList()));
        final Path declared = Files.createTempFile(dir, "declared.", ".vcf");
        Files.write(declared, lines);
        return readDecoded(declared);
    }

    /** Writes a corpus file as VCF, every key it uses declared. */
    private static Path corpusFileAsVcf(final String relativePath, final Path dir) throws IOException {
        final DecodedFile corpusFile = readCorpusFileWithEveryKeyDeclared(relativePath, dir);
        return write(corpusFile.header, corpusFile.records, dir, ".vcf");
    }

    /** Writes a corpus file as BCF, every key it uses and every contig it is on declared. */
    private static Path corpusFileAsBcf(final String relativePath, final Path dir) throws IOException {
        final DecodedFile corpusFile = readCorpusFileWithEveryKeyDeclared(relativePath, dir);
        return write(
                withUndeclaredContigsDeclared(corpusFile.header, corpusFile.records), corpusFile.records, dir, ".bcf");
    }

    /** Reads a VCF or BCF this test wrote, which declares everything it uses, and writes it as VCF. */
    private static Path rewrittenAsVcf(final Path vcfOrBcf, final Path dir) throws IOException {
        final DecodedFile written = readDecoded(vcfOrBcf);
        return write(written.header, written.records, dir, ".vcf");
    }

    /**
     * Writes the records to a new file in the directory, as VCF or BCF according to the suffix, without the default
     * on-the-fly index, which would need contig lengths the corpus files mostly lack.
     */
    private static Path write(
            final VCFHeader header, final List<VariantContext> records, final Path dir, final String suffix)
            throws IOException {
        final Path output = Files.createTempFile(dir, "written.", suffix);
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .clearOptions()
                .setOutputPath(output)
                .build()) {
            writer.writeHeader(header);
            records.forEach(writer::add);
        }
        return output;
    }

    /** The lines of a VCF after its header. */
    private static List<String> recordLines(final Path vcf) throws IOException {
        return Files.readAllLines(vcf).stream()
                .filter(line -> !line.startsWith("#"))
                .collect(Collectors.toList());
    }

    /** The record lines bcftools writes for a VCF or BCF. */
    private static List<String> bcftoolsRecordLines(final Path vcfOrBcf) {
        return BcftoolsTestUtils.executeBcftoolsForStdout("view", "--no-header", vcfOrBcf.toString());
    }

    /**
     * Header lines for the INFO, FILTER and FORMAT keys a file's records use but its header does not declare. An INFO
     * key that never has a value is a Flag, a standard FORMAT key gets its standard line, and any other key is a String
     * with Number=. (htslib uses Number=1), so that a comma-separated value stays a list.
     */
    private static List<VCFHeaderLine> undeclaredKeyLines(final DecodedFile file) {
        final VCFHeader header = file.header;
        final Map<String, Boolean> undeclaredInfoKeyIsFlag = new LinkedHashMap<>();
        final Set<String> undeclaredFilters = new LinkedHashSet<>();
        final Set<String> undeclaredFormatKeys = new LinkedHashSet<>();
        for (final VariantContext vc : file.records) {
            vc.getAttributes().forEach((key, value) -> {
                if (!header.hasInfoLine(key)) {
                    undeclaredInfoKeyIsFlag.merge(key, Boolean.TRUE.equals(value), Boolean::logicalAnd);
                }
            });
            for (final String filter : vc.getFilters()) {
                if (!header.hasFilterLine(filter)) {
                    undeclaredFilters.add(filter);
                }
            }
            for (final String key : vc.calcVCFGenotypeKeys(header)) {
                if (!header.hasFormatLine(key)) {
                    undeclaredFormatKeys.add(key);
                }
            }
        }
        final List<VCFHeaderLine> lines = new ArrayList<>();
        undeclaredInfoKeyIsFlag.forEach((key, isFlag) -> lines.add(
                isFlag
                        ? new VCFInfoHeaderLine(key, 0, VCFHeaderLineType.Flag, SYNTHETIC_DESCRIPTION)
                        : new VCFInfoHeaderLine(
                                key, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, SYNTHETIC_DESCRIPTION)));
        for (final String filter : undeclaredFilters) {
            lines.add(new VCFFilterHeaderLine(filter, SYNTHETIC_DESCRIPTION));
        }
        for (final String key : undeclaredFormatKeys) {
            final VCFFormatHeaderLine standard = VCFStandardHeaderLines.getFormatLine(key, false);
            lines.add(
                    standard != null
                            ? standard
                            : new VCFFormatHeaderLine(
                                    key,
                                    VCFHeaderLineCount.UNBOUNDED,
                                    VCFHeaderLineType.String,
                                    SYNTHETIC_DESCRIPTION));
        }
        return lines;
    }

    /**
     * Returns a copy of the header with a contig line, ID only, for every contig the records are on but it does not
     * declare; the BCF writer needs every contig in its dictionary.
     */
    private static VCFHeader withUndeclaredContigsDeclared(final VCFHeader header, final List<VariantContext> records) {
        final VCFHeader declared = new VCFHeader(header);
        final Set<String> contigs = header.getContigLines().stream()
                .map(VCFContigHeaderLine::getID)
                .collect(Collectors.toCollection(HashSet::new));
        for (final VariantContext vc : records) {
            if (contigs.add(vc.getContig())) {
                declared.addMetaDataLine(new VCFContigHeaderLine(Map.of("ID", vc.getContig()), contigs.size() - 1));
            }
        }
        return declared;
    }

    // Normalisations of a VCF record line, each undoing one way htsjdk's output differs from its input

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?");
    private static final Pattern DECIMAL_IN_TEXT =
            Pattern.compile("(?<![\\w.])-?\\d+\\.\\d+(?:[eE][-+]?\\d+)?(?![\\w.])");

    private static List<String> normalised(final List<String> lines, final KnownDifference difference) {
        return lines.stream()
                .map(line -> line.startsWith("#") ? line : difference.normalisation.apply(line))
                .collect(Collectors.toList());
    }

    private static List<String> withHtsjdkConventions(final List<String> recordLines) {
        return recordLines.stream()
                .map(line -> withGlReadAsPl(withInfoSorted(line)))
                .collect(Collectors.toList());
    }

    private static String withColumn(final String line, final int column, final UnaryOperator<String> change) {
        final String[] columns = line.split("\t", -1);
        columns[column] = change.apply(columns[column]);
        return String.join("\t", columns);
    }

    private static String withFiltersSorted(final String line) {
        return withColumn(line, FILTER_COLUMN, filters -> Arrays.stream(filters.split(";"))
                .sorted()
                .collect(Collectors.joining(";")));
    }

    private static String withInfoSorted(final String line) {
        return withColumn(line, INFO_COLUMN, info -> Arrays.stream(info.split(";"))
                .sorted()
                .collect(Collectors.joining(";")));
    }

    /** Drops the named keys from a record line's INFO, leaving the rest of the line to be compared. */
    private static UnaryOperator<String> withoutInfoKeys(final String... keys) {
        final Set<String> dropped = Set.of(keys);
        return line -> withColumn(line, INFO_COLUMN, info -> {
            final String kept = Arrays.stream(info.split(";"))
                    .filter(entry -> !dropped.contains(entry.split("=", 2)[0]))
                    .collect(Collectors.joining(";"));
            return kept.isEmpty() ? VCFConstants.EMPTY_INFO_FIELD : kept;
        });
    }

    /** Rounds a numeric QUAL to two decimal places, leaving the missing value, inf and nan as they are. */
    private static String withQualRounded(final String line) {
        return withColumn(
                line,
                QUAL_COLUMN,
                qual -> NUMBER.matcher(qual).matches()
                        ? String.format(Locale.US, "%.2f", Double.parseDouble(qual))
                        : qual);
    }

    /** Formats every decimal number in the INFO and sample columns as the VCF writer formats a Float. */
    private static String withDecimalsFormattedAsFloats(final String line) {
        final String[] columns = line.split("\t", -1);
        for (int i = INFO_COLUMN; i < columns.length; i++) {
            columns[i] = DECIMAL_IN_TEXT
                    .matcher(columns[i])
                    .replaceAll(decimal ->
                            Matcher.quoteReplacement(VCFEncoder.formatVCFDouble(Double.parseDouble(decimal.group()))));
        }
        return String.join("\t", columns);
    }

    /**
     * Replaces a GL without a PL by the PL the VCF reader makes of it, keeping its place in FORMAT and each sample. A
     * record with both is left alone; the corpus has none.
     */
    private static String withGlReadAsPl(final String line) {
        final String[] columns = line.split("\t", -1);
        if (columns.length <= FORMAT_COLUMN) {
            return line;
        }
        final List<String> keys = Arrays.asList(columns[FORMAT_COLUMN].split(":"));
        final int glIndex = keys.indexOf(VCFConstants.FORMAT.GENOTYPE_LIKELIHOODS);
        if (glIndex == -1 || keys.contains(VCFConstants.FORMAT.PHRED_SCALED_GENOTYPE_LIKELIHOODS)) {
            return line;
        }
        keys.set(glIndex, VCFConstants.FORMAT.PHRED_SCALED_GENOTYPE_LIKELIHOODS);
        columns[FORMAT_COLUMN] = String.join(":", keys);
        for (int i = FORMAT_COLUMN + 1; i < columns.length; i++) {
            final String[] values = columns[i].split(":", -1);
            if (glIndex < values.length && !values[glIndex].equals(VCFConstants.MISSING_VALUE_v4)) {
                values[glIndex] = Arrays.stream(
                                GenotypeLikelihoods.fromGLField(values[glIndex]).getAsPLs())
                        .mapToObj(Integer::toString)
                        .collect(Collectors.joining(","));
            }
            columns[i] = String.join(":", values);
        }
        return String.join("\t", columns);
    }
}
