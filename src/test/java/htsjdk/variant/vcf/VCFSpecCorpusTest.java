package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.testng.Assert;
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
 */
public class VCFSpecCorpusTest extends HtsjdkTest {
    private static final Path CORPUS = Paths.get("src/test/resources/htsjdk/hts-specs/test/vcf");
    private static final List<String> VERSIONS = List.of("4.1", "4.2", "4.3", "4.5");

    private static final String GT_NAMES_UNDEFINED_ALLELE =
            "the second sample at 1:1900 has GT 0|1 on a record whose ALT is '.': the genotype names an allele the"
                    + " record does not define (htslib accepts it only because it stores GT as bare integers)";
    private static final String END_MINUS_ONE = "END=-1 trips the assert in VariantContext.validateStop";

    /** {@code passed/} files the reader cannot fully decode today, keyed by path relative to the corpus root. */
    private static final Map<String, String> PASSED_NOT_YET_DECODABLE =
            Map.ofEntries(Map.entry("4.5/passed/zero_length_LAA.vcf", "VCF 4.5 is rejected outright"));

    /**
     * {@code passed/} files that are in fact invalid and that the reader is right to reject. Unlike the list above,
     * these are not expected to change.
     */
    private static final Map<String, String> PASSED_BUT_INVALID = Map.ofEntries(
            Map.entry("4.1/passed/passed_body_alt.vcf", GT_NAMES_UNDEFINED_ALLELE),
            Map.entry("4.2/passed/passed_body_alt.vcf", GT_NAMES_UNDEFINED_ALLELE),
            Map.entry("4.3/passed/passed_body_alt.vcf", GT_NAMES_UNDEFINED_ALLELE));

    /** {@code failed/} files where the reader throws something other than a TribbleException today. */
    private static final Map<String, String> FAILED_WITH_WRONG_EXCEPTION = Map.ofEntries(
            Map.entry("4.1/failed/failed_body_info_016.vcf", END_MINUS_ONE),
            Map.entry("4.2/failed/failed_body_info_016.vcf", END_MINUS_ONE),
            Map.entry("4.3/failed/failed_body_info_016.vcf", END_MINUS_ONE));

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
                    vc.getAttributes();
                    for (final Genotype genotype : vc.getGenotypes()) {
                        genotype.getAlleles();
                        genotype.getExtendedAttributes();
                    }
                }
            } catch (final Throwable t) {
                return new DecodeFailure(where, t);
            }
        } catch (final Throwable t) {
            return new DecodeFailure("opening the file or reading its header", t);
        }
        return null;
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
}
