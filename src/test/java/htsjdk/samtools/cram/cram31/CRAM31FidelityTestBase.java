package htsjdk.samtools.cram.cram31;

import htsjdk.HtsjdkTest;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.*;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.structure.CRAMCompressionProfile;
import htsjdk.utils.SamtoolsTestUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import org.testng.Assert;
import org.testng.annotations.DataProvider;

/**
 * Base class for CRAM 3.1 fidelity tests. Each subclass tests a single compression profile
 * against all input files. Subclasses are in separate files so TestNG parallelizes them
 * as independent classes, spreading the expensive CEUTrio conversions across threads.
 *
 * <p>To add a new profile, create a subclass that implements {@link #getProfile()}.
 * To add a new input file, add it to the {@link #inputs()} data provider.
 */
public abstract class CRAM31FidelityTestBase extends HtsjdkTest {

    private static final String CRAM_TEST_DIR = "src/test/resources/htsjdk/samtools/cram/";
    private static final String REF_TEST_DIR = "src/test/resources/htsjdk/samtools/reference/";

    /** @return the compression profile name for this test class (fast, normal, small, archive) */
    protected abstract String getProfile();

    @DataProvider(name = "inputs")
    public Object[][] inputs() {
        return new Object[][] {
            {
                new HtsPath(CRAM_TEST_DIR + "CEUTrio.HiSeq.WGS.b37.NA12878.20.21.v3.0.samtools.cram"),
                new HtsPath(REF_TEST_DIR + "human_g1k_v37.20.21.fasta.gz"),
            },
            {
                new HtsPath(CRAM_TEST_DIR + "NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"),
                new HtsPath(CRAM_TEST_DIR + "human_g1k_v37.20.21.1-100.fasta"),
            },
            {
                new HtsPath(CRAM_TEST_DIR + "NA12878.unmapped.cram"),
                new HtsPath(CRAM_TEST_DIR + "human_g1k_v37.20.21.1-100.fasta"),
            },
        };
    }

    /**
     * The core fidelity test. Converts a CRAM 3.0 source to 3.1 with both samtools and HTSJDK
     * using this class's profile, then compares all three pairwise.
     */
    public void testCRAM31Fidelity(final IOPath testInput, final IOPath testReference) throws IOException {
        final String profile = getProfile();
        Assert.assertEquals(getCRAMVersion(testInput), CramVersions.CRAM_v3);

        // samtools: convert 3.0 → 3.1 with this profile
        final IOPath cramSamtools31 = IOUtils.createTempPath("cram31Samtools", ".cram");
        convertToCRAM31WithSamtools(testInput, cramSamtools31, testReference, profile);
        Assert.assertEquals(getCRAMVersion(cramSamtools31), CramVersions.CRAM_v3_1);

        // compare original 3.0 vs samtools 3.1
        compareCRAMFiles(testInput.toPath(), cramSamtools31.toPath(), testReference.toPath());

        // HTSJDK: write with this profile
        final IOPath cramHtsjdk31 = IOUtils.createTempPath("cram31Htsjdk", ".cram");
        writeWithHTSJDK(testInput, cramHtsjdk31, testReference, profile);

        // compare original 3.0 vs HTSJDK output
        compareCRAMFiles(testInput.toPath(), cramHtsjdk31.toPath(), testReference.toPath());

        // compare HTSJDK output vs samtools 3.1
        compareCRAMFiles(cramHtsjdk31.toPath(), cramSamtools31.toPath(), testReference.toPath());
    }

    static void convertToCRAM31WithSamtools(
            final IOPath input, final IOPath output, final IOPath reference, final String profile) {
        SamtoolsTestUtils.convertToCRAM(input, output, reference, "--output-fmt cram,version=3.1," + profile);
    }

    static void writeWithHTSJDK(
            final IOPath inputPath, final IOPath outputPath, final IOPath referencePath, final String profile)
            throws IOException {
        final CRAMCompressionProfile cramProfile = CRAMCompressionProfile.valueOfCaseInsensitive(profile);
        try (final SamReader reader = SamReaderFactory.makeDefault()
                        .referenceSequence(referencePath.toPath())
                        .validationStringency(ValidationStringency.LENIENT)
                        .open(inputPath.toPath());
                final SAMFileWriter writer = new SAMFileWriterFactory()
                        .setCRAMEncodingStrategy(cramProfile.toStrategy())
                        .makeWriter(
                                reader.getFileHeader().clone(), true, outputPath.toPath(), referencePath.toPath())) {
            for (final SAMRecord rec : reader) {
                writer.addAlignment(rec);
            }
        }
    }

    static void compareCRAMFiles(final Path path1, final Path path2, final Path referencePath) {
        int diffCount = 0;
        try (final SamReader reader1 = SamReaderFactory.makeDefault()
                        .referenceSequence(referencePath)
                        .validationStringency(ValidationStringency.SILENT)
                        .open(path1);
                final SamReader reader2 = SamReaderFactory.makeDefault()
                        .referenceSequence(referencePath)
                        .validationStringency(ValidationStringency.SILENT)
                        .open(path2); ) {
            final Iterator<SAMRecord> iterator2 = reader2.iterator();
            for (final SAMRecord rec1 : reader1) {
                final SAMRecord rec2 = iterator2.next();
                if (!rec1.equals(rec2)) {
                    System.err.printf("%s%s%n", rec1.getReadUnmappedFlag() ? "unmapped: " : "", rec1.getSAMString());
                    System.err.printf("%s%s%n", rec2.getReadUnmappedFlag() ? "unmapped: " : "", rec2.getSAMString());
                    diffCount++;
                }
            }
            Assert.assertFalse(iterator2.hasNext(), "Second CRAM file has more records than first");
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        Assert.assertEquals(diffCount, 0);
    }

    public static CRAMVersion getCRAMVersion(final IOPath cramPath) {
        try (final InputStream fis = Files.newInputStream(cramPath.toPath())) {
            return CramIO.readCramHeader(fis).getCRAMVersion();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
