package htsjdk.variant.vcf;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.seekablestream.SeekableStreamFactory;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.TestUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by farjoun on 10/12/17.
 */
public class VCFFileReaderTest extends HtsjdkTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/variant/");

    @DataProvider(name = "queryableData")
    public Iterator<Object[]> queryableData() throws IOException {
        List<Object[]> tests = new ArrayList<>();
        tests.add(new Object[]{new File(TEST_DATA_DIR, "NA12891.fp.vcf"), false});
        tests.add(new Object[]{new File(TEST_DATA_DIR, "NA12891.vcf"), false});
        tests.add(new Object[]{VCFUtils.createTemporaryIndexedVcfFromInput(new File(TEST_DATA_DIR, "NA12891.vcf"), "fingerprintcheckertest.tmp."), true});
        tests.add(new Object[]{VCFUtils.createTemporaryIndexedVcfFromInput(new File(TEST_DATA_DIR, "NA12891.vcf.gz"), "fingerprintcheckertest.tmp."), true});

        return tests.iterator();
    }

    @Test(dataProvider = "queryableData")
    public void testIsQueriable(final File vcf, final boolean expectedQueryable) throws Exception {
        Assert.assertEquals(new VCFFileReader(vcf, false).isQueryable(), expectedQueryable);
    }

    @DataProvider(name = "pathsData")
    Object[][] pathsData() {

        final String TEST_DATA_DIR = "src/test/resources/htsjdk/variant/";
        return new Object[][]{
                // various ways to refer to a local file
                {TEST_DATA_DIR + "VCF4HeaderTest.vcf", null, false, true},

//                // this is almost a vcf, but not quite it's missing the #CHROM line and it has no content...
                {TEST_DATA_DIR + "Homo_sapiens_assembly38.tile_db_header.vcf", null, false, false},

//                // test that have indexes
                {TEST_DATA_DIR + "test.vcf.bgz", TEST_DATA_DIR + "test.vcf.bgz.tbi", true, true},
                {TEST_DATA_DIR + "serialization_test.bcf", TEST_DATA_DIR + "serialization_test.bcf.idx", true, true},
                {TEST_DATA_DIR + "test1.vcf", TEST_DATA_DIR + "test1.vcf.idx", true, true},
//
//                // test that lack indexes (should succeed)
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.gz", null, false, true},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf", null, false, true},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex but has a space.vcf", null, false, true},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.bcf", null, false, true},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.bgz", null, false, true},
//
//                // test that lack indexes should fail)
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.gz", null, true, false},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf", null, true, false},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.bcf", null, true, false},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.bgz", null, true, false},
//
//                // testing that v4.2 parses Source/Version fields, see issue #517
                {TEST_DATA_DIR + "Vcf4.2WithSourceVersionInfoFields.vcf", null, false, true},
//
//                // should reject bcf v2.2 on read, see issue https://github.com/samtools/htsjdk/issues/1323
                {TEST_DATA_DIR + "BCFVersion22Uncompressed.bcf", null, false, false}
        };
    }

    @Test(dataProvider = "pathsData", timeOut = 60_000)
    public void testCanOpenVCFPathReader(final String file, final String index, final boolean requiresIndex, final boolean shouldSucceed) throws Exception {
        try (FileSystem fs = Jimfs.newFileSystem("test", Configuration.unix())) {
            final Path tribbleFileInJimfs = TestUtils.getTribbleFileInJimfs(file, index, fs);
            try (final VCFFileReader reader = new VCFFileReader(tribbleFileInJimfs, requiresIndex)) {

                final VCFHeader header = reader.getFileHeader();
                reader.iterator().stream().forEach(
                        v->v.getGenotypes()
                                .stream()
                                .count());
            } catch (Exception e) {
                if (shouldSucceed) {
                    throw e;
                } else {
                    return;
                }
            }
        }
        // fail if a test that should have thrown didn't
        Assert.assertTrue(shouldSucceed, "Test should have failed but succeeded");
    }

    @Test
    public void testTabixFileWithEmbeddedSpaces() throws IOException {
        final File testVCF =  new File(TEST_DATA_DIR, "HiSeq.10000.vcf.bgz");
        final File testTBI =  new File(TEST_DATA_DIR, "HiSeq.10000.vcf.bgz.tbi");

        // Copy the input files into a temporary directory with embedded spaces in the name.
        // This test needs to include the associated .tbi file because we want to force execution
        // of the tabix code path.
        final File tempDir = IOUtil.createTempDir("test spaces", "");
        Assert.assertTrue(tempDir.getAbsolutePath().contains(" "));
        tempDir.deleteOnExit();
        final File inputVCF = new File(tempDir, "HiSeq.10000.vcf.bgz");
        inputVCF.deleteOnExit();
        final File inputTBI = new File(tempDir, "HiSeq.10000.vcf.bgz.tbi");
        inputTBI.deleteOnExit();
        IOUtil.copyFile(testVCF, inputVCF);
        IOUtil.copyFile(testTBI, inputTBI);

        try (final VCFFileReader vcfFileReader = new VCFFileReader(inputVCF)) {
            Assert.assertNotNull(vcfFileReader.getFileHeader());
        }

    }

}
