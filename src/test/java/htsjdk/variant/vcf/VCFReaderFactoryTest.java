package htsjdk.variant.vcf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.RuntimeIOException;


/**
 * Created by Pierre Lindenbaum
 */
public class VCFReaderFactoryTest extends HtsjdkTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/variant/");

    @DataProvider(name = "queryableData")
    public Iterator<Object[]> queryableData() throws IOException {
        List<Object[]> tests = new ArrayList<>();
        tests.add(new Object[] { new File(TEST_DATA_DIR, "NA12891.fp.vcf"), false });
        tests.add(new Object[] { new File(TEST_DATA_DIR, "NA12891.vcf"), false });
        tests.add(new Object[] { VCFUtils.createTemporaryIndexedVcfFromInput(
                new File(TEST_DATA_DIR, "NA12891.vcf"),
                "fingerprintcheckertest.tmp."), true });
        tests.add(new Object[] { VCFUtils.createTemporaryIndexedVcfFromInput(
                new File(TEST_DATA_DIR, "NA12891.vcf.gz"),
                "fingerprintcheckertest.tmp."), true });
        return tests.iterator();
    }

    @Test(dataProvider = "queryableData")
    public void testFileIsQueriable(final File vcf, final boolean expectedQueryable)
            throws Exception {
        try (VCFReader reader = VCFReaderFactory.getInstance().open(vcf, false)) {
            Assert.assertEquals(reader.isQueryable(), expectedQueryable);
        }
    }

    @Test(dataProvider = "queryableData")
    public void testPathIsQueriable(final File vcf, final boolean expectedQueryable)
            throws Exception {
        try (VCFReader reader = VCFReaderFactory.getInstance().open(vcf.toPath(), false)) {
            Assert.assertEquals(reader.isQueryable(), expectedQueryable);
        }
    }

    @Test(dataProvider = "queryableData")
    public void testUriIsQueriable(final File vcf, final boolean expectedQueryable)
            throws Exception {
        try (VCFReader reader = VCFReaderFactory.getInstance().open(vcf.toString(), false)) {
            Assert.assertEquals(reader.isQueryable(), expectedQueryable);
        }
    }

    @Test
    public void testInstances() {
        Assert.assertNotNull(VCFReaderFactory.getInstance());
        Assert.assertNotNull(VCFReaderFactory.getDefaultInstance());
        final VCFReaderFactory custom = new VCFReaderFactory() {
            public VCFReader open(Path vcfPath, boolean requireIndex) {
                throw new RuntimeIOException("cannot open anything");
            }
        };
        VCFReaderFactory.setInstance(custom);
        Assert.assertTrue(custom == VCFReaderFactory.getInstance());
        VCFReaderFactory.setInstance(VCFReaderFactory.getDefaultInstance());
        Assert.assertFalse(custom == VCFReaderFactory.getInstance());
    }

}
