package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.IOUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test valid combinations of bam/cram vs bai/crai files.
 * Created by vadim on 10/08/2015.
 */
public class SamFilesTest extends HtsjdkTest {
    private static final String TEST_DATA = "src/test/resources/htsjdk/samtools/BAMFileIndexTest/";
    private static final Path BAM_FILE = Path.of(TEST_DATA + "index_test.bam");

    @DataProvider(name = "FindIndexParams")
    public static Object[][] paramsFindIndexForSuffixes() {
        return new Object[][] {
            // no index available sanity checks:
            {".tmp", null, null},
            {".bam", null, null},
            {".cram", null, null},

            // legit cases for BAM files:
            {".bam", ".bai", ".bai"},
            {".bam", ".bam.bai", ".bam.bai"},

            // legit cases for CRAM files:
            {".cram", ".cram.bai", ".cram.bai"},
            {".cram", ".cram.crai", ".cram.crai"},

            // special prohibited cases:
            {".bam", ".crai", null},
            {".tmp", ".crai", null},
        };
    }

    @Test(dataProvider = "FindIndexParams")
    public void testFindIndexForSuffixes(
            final String dataFileSuffix, final String indexFileSuffix, final String expectIndexSuffix)
            throws IOException {
        final Path dataFile = Files.createTempFile("test", dataFileSuffix);
        Path indexFile = null;
        try {
            Assert.assertNull(SamFiles.findIndex(dataFile));

            if (indexFileSuffix != null) {
                indexFile = Path.of(dataFile.toAbsolutePath().toString().replaceFirst("\\.\\S+$", indexFileSuffix));
                Files.createFile(indexFile);
            }

            final Path foundIndexPath = SamFiles.findIndex(dataFile);
            if (expectIndexSuffix == null) {
                Assert.assertNull(foundIndexPath);
            } else {
                Assert.assertNotNull(foundIndexPath);
                Assert.assertTrue(foundIndexPath.getFileName().toString().endsWith(expectIndexSuffix));
            }
        } finally {
            Files.deleteIfExists(dataFile);
            if (indexFile != null) {
                Files.deleteIfExists(indexFile);
            }
        }
    }

    @DataProvider(name = "filesAndIndicies")
    public Object[][] getFilesAndIndicies() throws IOException {

        final Path REAL_INDEX_FILE = Path.of(BAM_FILE + ".bai"); // test regular file
        final Path SYMLINKED_BAM_WITH_SYMLINKED_INDEX = Path.of(TEST_DATA, "symlink_with_index.bam");

        return new Object[][] {
            {BAM_FILE, REAL_INDEX_FILE},
            {SYMLINKED_BAM_WITH_SYMLINKED_INDEX, Path.of(SYMLINKED_BAM_WITH_SYMLINKED_INDEX + ".bai")},
            {Path.of(TEST_DATA, "symlink_without_linked_index.bam"), REAL_INDEX_FILE.toRealPath()},
            {Path.of(TEST_DATA, "FileThatDoesntExist"), null}
        };
    }

    @Test(dataProvider = "filesAndIndicies")
    public void testIndexSymlinking(Path bam, Path expected_index) {
        Assert.assertEquals(SamFiles.findIndex(bam), expected_index);
    }

    @Test
    public void testFindIndexForUpperCaseCramWithAppendedCrai() throws IOException {
        final Path directory = Files.createTempDirectory("testFindIndexForUpperCaseCram");
        final Path cram = Files.createFile(directory.resolve("reads.CRAM"));
        final Path crai = Files.createFile(directory.resolve("reads.CRAM.crai"));

        Assert.assertEquals(SamFiles.findIndex(cram).toRealPath(), crai.toRealPath());
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testFindIndexForUpperCaseBamWithReplacedExtension() throws IOException {
        final Path directory = Files.createTempDirectory("testFindIndexForUpperCaseBam");
        final Path bam = Files.createFile(directory.resolve("reads.BAM"));
        final Path bai = Files.createFile(directory.resolve("reads.bai"));

        Assert.assertEquals(SamFiles.findIndex(bam).toRealPath(), bai.toRealPath());
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testFindIndexFindsATabixIndexBesideBlockCompressedSam() throws IOException {
        final Path directory = Files.createTempDirectory("testFindIndexTbiForSam");
        final Path samGz = Files.createFile(directory.resolve("reads.SAM.gz"));
        final Path tbi = Files.createFile(directory.resolve("reads.SAM.gz.tbi"));
        IOUtil.deleteOnExit(directory);
        IOUtil.deleteOnExit(samGz);
        IOUtil.deleteOnExit(tbi);
        Assert.assertEquals(SamFiles.findIndex(samGz), tbi);
    }

    @Test
    public void testFindIndexDoesNotTakeTheTabixIndexOfAnotherFormat() throws IOException {
        final Path directory = Files.createTempDirectory("testFindIndexTbiForVcf");
        final Path vcfGz = Files.createFile(directory.resolve("calls.vcf.gz"));
        final Path tbi = Files.createFile(directory.resolve("calls.vcf.gz.tbi"));
        IOUtil.deleteOnExit(directory);
        IOUtil.deleteOnExit(vcfGz);
        IOUtil.deleteOnExit(tbi);
        Assert.assertNull(SamFiles.findIndex(vcfGz));
    }

    @Test
    public void testFileTypeChecksIgnoreCase() {
        Assert.assertTrue(SamFiles.isBAMFile(Path.of("reads.BAM")));
        Assert.assertTrue(SamFiles.isCRAMFile(Path.of("reads.Cram")));
        Assert.assertTrue(SamFiles.isSAMFile(Path.of("reads.SAM")));
        Assert.assertFalse(SamFiles.isBAMFile(Path.of("reads.bam.txt")));
    }
}
