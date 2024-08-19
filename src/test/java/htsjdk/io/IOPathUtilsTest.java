package htsjdk.io;

import htsjdk.HtsjdkTest;
import htsjdk.beta.io.IOPathUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class IOPathUtilsTest extends HtsjdkTest {

    @DataProvider(name = "replaceExtensionTestData")
    public Object[][] getReplaceExtensionTestData() {
        return new Object[][]{
                {"file:///somepath/a.vcf", ".idx", "file:///somepath/a.idx"},
                {"file:///somepath/a.vcf", "idx", "file:///somepath/a.idx"},
                {"file:///a.vcf/a.vcf", ".idx", "file:///a.vcf/a.idx"},
                {"file:///a.vcf/a.vcf", "idx", "file:///a.vcf/a.idx"},
                {"file:///somepath/a.vcf.gz", ".tbi", "file:///somepath/a.vcf.tbi"},
                {"file:///somepath/a.vcf.gz", "tbi", "file:///somepath/a.vcf.tbi"},
        };
    }

    @Test(dataProvider = "replaceExtensionTestData")
    public void testReplaceExtension(
            final String basePath,
            final String extension,
            final String resolvedPath) {
        Assert.assertEquals(
                IOPathUtils.replaceExtension(new HtsPath(basePath), extension, HtsPath::new),
                new HtsPath(resolvedPath));
    }

    @Test(expectedExceptions = {RuntimeException.class})
    public void testThrowOnMissingExtension() {
        try {
            IOPathUtils.replaceExtension(new HtsPath("file:///somepath/a"), "idx", HtsPath::new);
            Assert.fail("Expected exception");
        } catch (final RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("The original path has no extension to replace"));
            throw e;
        }
    }

    @DataProvider(name = "appendExtensionTestData")
    public Object[][] getAppendExtensionTestData() {
        return new Object[][]{
                {"file:///somepath/a.vcf", ".idx", "file:///somepath/a.vcf.idx"},
                {"file:///somepath/a.vcf", "idx", "file:///somepath/a.vcf.idx"},
                {"file:///a.vcf/a.vcf", ".idx", "file:///a.vcf/a.vcf.idx"},
                {"file:///a.vcf/a.vcf", "idx", "file:///a.vcf/a.vcf.idx"},
                {"file:///somepath/a.vcf.gz", ".tbi", "file:///somepath/a.vcf.gz.tbi"},
                {"file:///somepath/a.vcf.gz", "tbi", "file:///somepath/a.vcf.gz.tbi"},
        };
    }

    @Test(dataProvider = "appendExtensionTestData")
    public void testAppendExtension(
            final String basePath,
            final String extension,
            final String resolvedPath) {
        Assert.assertEquals(
                IOPathUtils.appendExtension(new HtsPath(basePath), extension, HtsPath::new),
                new HtsPath(resolvedPath));
    }

}
