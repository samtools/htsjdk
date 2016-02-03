package htsjdk.samtools;

import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

/**
 * Test valid combinations of bam/cram vs bai/crai files.
 * Created by vadim on 10/08/2015.
 */
public class SamFilesTest {
    private static final String TEST_DATA = "src/test/resources/htsjdk/samtools/BAMFileIndexTest/";
    private static final File BAM_FILE = new File(TEST_DATA + "index_test.bam");

    @DataProvider(name = "FindIndexParams")
    public static Object[][] paramsFindIndexForSuffixes() {
        return new Object[][]{
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
    public void testFindIndexForSuffixes(final String dataFileSuffix, final String indexFileSuffix, final String expectIndexSuffix) throws IOException {
        final File dataFile = File.createTempFile("test", dataFileSuffix);
        dataFile.deleteOnExit();
        Assert.assertNull(SamFiles.findIndex(dataFile));
        Assert.assertNull(SamFiles.findIndex(dataFile.toPath()));

        File indexFile = null;
        if (indexFileSuffix != null) {
            indexFile = new File(dataFile.getAbsolutePath().replaceFirst("\\.\\S+$", indexFileSuffix));
            indexFile.createNewFile();
            indexFile.deleteOnExit();
        }

        final File foundIndexFile = SamFiles.findIndex(dataFile);
        if (expectIndexSuffix == null) {
            Assert.assertNull(foundIndexFile);
        } else {
            Assert.assertNotNull(foundIndexFile);
            Assert.assertTrue(foundIndexFile.getName().endsWith(expectIndexSuffix));
        }

        final Path foundIndexPath = SamFiles.findIndex(dataFile.toPath());
        if (expectIndexSuffix == null) {
            Assert.assertNull(foundIndexPath);
        } else {
            Assert.assertNotNull(foundIndexPath);
            Assert.assertTrue(foundIndexPath.getFileName().toString().endsWith(expectIndexSuffix));
        }
    }

    @DataProvider(name = "filesAndIndicies")
    public Object[][] getFilesAndIndicies() throws IOException {

        final File REAL_INDEX_FILE = new File(BAM_FILE + ".bai"); //test regular file
        final File SYMLINKED_BAM_WITH_SYMLINKED_INDEX = new File(TEST_DATA, "symlink_with_index.bam");

        return new Object[][]{
                {BAM_FILE, REAL_INDEX_FILE},
                {SYMLINKED_BAM_WITH_SYMLINKED_INDEX, new File(SYMLINKED_BAM_WITH_SYMLINKED_INDEX + ".bai")},
                {new File(TEST_DATA, "symlink_without_linked_index.bam"), REAL_INDEX_FILE.getCanonicalFile()},
                {new File(TEST_DATA, "FileThatDoesntExist"), null}
        };
    }

    @Test(dataProvider ="filesAndIndicies")
    public void testIndexSymlinking(File bam, File expected_index) {
        Assert.assertEquals(SamFiles.findIndex(bam), expected_index);
        Assert.assertEquals(SamFiles.findIndex(bam.toPath()), expected_index == null ? null : expected_index.toPath());
    }
}
