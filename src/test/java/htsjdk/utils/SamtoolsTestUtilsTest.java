package htsjdk.utils;

import htsjdk.HtsjdkTest;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.ProcessExecutor;
import java.io.File;
import java.io.IOException;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

public class SamtoolsTestUtilsTest extends HtsjdkTest {

    @Test
    public void testSamtoolsIsAvailable() {
        Assert.assertTrue(SamtoolsTestUtils.isSamtoolsAvailable());
    }

    @Test
    public void testSamtoolsVersion() {
        if (!SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("Samtools not available on local device");
        }
        final ProcessExecutor.ExitStatusAndOutput processStatus = SamtoolsTestUtils.executeSamToolsCommand("--version");
        final String localVersion = SamtoolsTestUtils.parseSamtoolsVersion(processStatus.stdout);
        Assert.assertNotNull(
                localVersion,
                "Could not parse samtools version from `samtools --version` output: " + processStatus.stdout);
        Assert.assertTrue(
                SamtoolsTestUtils.compareVersions(localVersion, SamtoolsTestUtils.minimumSamtoolsVersion) >= 0,
                "Local samtools version " + localVersion + " is older than the minimum required by htsjdk tests ("
                        + SamtoolsTestUtils.minimumSamtoolsVersion + ")");
    }

    @Test
    public void testParseSamtoolsVersionFromTypicalOutput() {
        final String stdout = "samtools 1.23.1\nUsing htslib 1.23.1\nCopyright (C) 2024 Genome Research Ltd.\n";
        Assert.assertEquals(SamtoolsTestUtils.parseSamtoolsVersion(stdout), "1.23.1");
    }

    @Test
    public void testParseSamtoolsVersionTwoComponent() {
        Assert.assertEquals(SamtoolsTestUtils.parseSamtoolsVersion("samtools 1.21\n"), "1.21");
    }

    @Test
    public void testParseSamtoolsVersionReturnsNullWhenAbsent() {
        Assert.assertNull(SamtoolsTestUtils.parseSamtoolsVersion("nothing recognizable here\n"));
    }

    @Test
    public void testCompareVersionsEqualWithImplicitZero() {
        Assert.assertEquals(SamtoolsTestUtils.compareVersions("1.23", "1.23.0"), 0);
    }

    @Test
    public void testCompareVersionsPatchGreater() {
        Assert.assertTrue(SamtoolsTestUtils.compareVersions("1.23.1", "1.23") > 0);
    }

    @Test
    public void testCompareVersionsMajorLess() {
        Assert.assertTrue(SamtoolsTestUtils.compareVersions("1.21", "1.23.1") < 0);
    }

    @Test
    public void testCompareVersionsNumericNotLexical() {
        // 1.10 is greater than 1.9 numerically, even though "1.10" sorts before "1.9" lexically.
        Assert.assertTrue(SamtoolsTestUtils.compareVersions("1.10", "1.9") > 0);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testSamtoolsPresentButCommandFails() {
        if (!SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("Samtools not available on local device");
        }
        SamtoolsTestUtils.executeSamToolsCommand("--notASamtoolsCommand");
    }

    @Test
    public void testCRAMConversion() throws IOException {
        if (!SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("Samtools not available on local device");
        }

        // Validates CRAM conversion.
        final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram");
        final File sourceFile = new File(TEST_DATA_DIR, "cramQueryWithBAI.cram");
        final File cramReference = new File(TEST_DATA_DIR, "human_g1k_v37.20.21.10M-10M200k.fasta");
        // This also validates that any extra command line arguments are passed through to samtools by requesting
        // that NM/MD values are synthesized in the output file (which is required for the output records to match).
        final IOPath tempSamtoolsPath = SamtoolsTestUtils.convertToCRAM(
                new HtsPath(sourceFile.getAbsolutePath()),
                new HtsPath(cramReference.getAbsolutePath()),
                "--input-fmt-option decode_md=1 --output-fmt-option store_md=1 --output-fmt-option store_nm=1");
        final SamReaderFactory factory = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.LENIENT)
                .referenceSequence(cramReference);
        try (final SamReader originalReader = factory.open(sourceFile);
                final SamReader samtoolsCopyReader = factory.open(tempSamtoolsPath.toPath());
                final CloseableIterator<SAMRecord> originalIt = originalReader.iterator();
                final CloseableIterator<SAMRecord> samtoolsIt = samtoolsCopyReader.iterator()) {
            while (originalIt.hasNext() && samtoolsIt.hasNext()) {
                Assert.assertEquals(originalIt.next(), samtoolsIt.next());
            }
            Assert.assertEquals(samtoolsIt.hasNext(), originalIt.hasNext());
        }
    }
}
