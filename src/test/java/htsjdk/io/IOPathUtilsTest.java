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
                {"file:///somepath/a.vcf", ".idx", true, "file:///somepath/a.vcf.idx"},
                {"file:///somepath/a.vcf", ".idx", false, "file:///somepath/a.idx"},
                {"file:///a.vcf/a.vcf", ".idx", true, "file:///a.vcf/a.vcf.idx"},
                {"file:///a.vcf/a.vcf", ".idx", false, "file:///a.vcf/a.idx"},
                {"file:///somepath/a.vcf.gz", ".tbi", true, "file:///somepath/a.vcf.gz.tbi"},
                {"file:///somepath/a.vcf.gz", ".tbi", false, "file:///somepath/a.vcf.tbi"},
        };
    }

    @Test(dataProvider = "replaceExtensionTestData")
    public void testReplaceExtension(
            final String basePath,
            final String extension,
            final boolean append,
            final String resolvedPath) {
        Assert.assertEquals(
                IOPathUtils.replaceExtension(new HtsPath(basePath), extension, append, HtsPath::new),
                new HtsPath(resolvedPath));
    }

}
