package htsjdk.samtools;

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

    @DataProvider(name = "files")
    public static Object[][] params() {
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

    @Test(dataProvider = "files")
    public void test(String dataFileSuffix, String indexFileSuffix, String expectIndexSuffix) throws IOException {
        File dataFile = File.createTempFile("test", dataFileSuffix);
        dataFile.deleteOnExit();
        Assert.assertNull(SamFiles.findIndex(dataFile));

        File indexFile = null;
        if (indexFileSuffix != null) {
            indexFile = new File(dataFile.getAbsolutePath().replaceFirst("\\.\\S+$", indexFileSuffix));
            indexFile.createNewFile();
            indexFile.deleteOnExit();
        }

        File foundIndexFile = SamFiles.findIndex(dataFile);
        if (expectIndexSuffix == null) {
            Assert.assertNull(foundIndexFile);
            return;
        }

        Assert.assertNotNull(foundIndexFile);
        Assert.assertTrue(foundIndexFile.getName().endsWith(expectIndexSuffix));
    }
}
