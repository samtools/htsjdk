package htsjdk.variant.vcf;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.tribble.TestUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.Path;

/**
 * Created by farjoun on 10/12/17.
 */
public class VCFPathReaderTest extends HtsjdkTest {

    @DataProvider(name = "pathsData")
    Object[][] pathsData() {

        final String TEST_DATA_DIR = "src/test/resources/htsjdk/variant/";
        return new Object[][]{
                // various ways to refer to a local file
                {TEST_DATA_DIR + "VCF4HeaderTest.vcf", null, false, true},

                //testing GCS files:

                // this is almost a vcf, but not quite it's missing the #CHROM line and it has no content...
                {TEST_DATA_DIR + "Homo_sapiens_assembly38.tile_db_header.vcf", null, false, false},

                // test that have indexes
                {TEST_DATA_DIR + "test.vcf.bgz", TEST_DATA_DIR + "test.vcf.bgz.tbi", true, true},
                {TEST_DATA_DIR + "serialization_test.bcf", TEST_DATA_DIR + "serialization_test.bcf.idx", true, true},
                {TEST_DATA_DIR + "test1.vcf", TEST_DATA_DIR + "test1.vcf.idx", true, true},

                // test that lack indexes (should succeed)
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.gz", null, false, true},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf", null, false, true},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.bcf", null, false, true},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.bgz", null, false, true},

                // test that lack indexes should fail)
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.gz", null, true, false},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf", null, true, false},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.bcf", null, true, false},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.bgz", null, true, false},

        };
    }

    @Test(dataProvider = "pathsData", timeOut = 60_000)
    public void testCanOpenVCFPathReader(final String file, final String index, final boolean requiresIndex, final boolean shouldSucceed) throws Exception {
        try (FileSystem fs = Jimfs.newFileSystem("test", Configuration.unix())) {
            final Path tribbleFileInJimfs = TestUtils.getTribbleFileInJimfs(new File(file).getAbsolutePath(), index == null ? null : new File(index).getAbsolutePath(), fs);
            try (final VCFPathReader reader = new VCFPathReader(tribbleFileInJimfs, requiresIndex)) {
                final VCFHeader header = reader.getFileHeader();
            } catch (Exception e) {
                if (shouldSucceed) throw e;
            }
        }
    }
}