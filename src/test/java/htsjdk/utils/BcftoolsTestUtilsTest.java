package htsjdk.utils;

import htsjdk.HtsjdkTest;
import java.nio.file.Paths;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BcftoolsTestUtilsTest extends HtsjdkTest {

    @Test
    public void testBcftoolsIsAvailable() {
        Assert.assertTrue(BcftoolsTestUtils.isBcftoolsAvailable());
    }

    @Test
    public void testExecuteBcftoolsReturnsItsOutput() {
        final List<String> output = BcftoolsTestUtils.executeBcftools("--version");
        Assert.assertFalse(output.isEmpty());
        Assert.assertTrue(output.get(0).startsWith("bcftools "), output.get(0));
    }

    @Test
    public void testViewAsVcfReturnsOnlyTheVcf() {
        // this file makes bcftools warn about undeclared contigs and INFO keys on stderr
        final List<String> lines = BcftoolsTestUtils.viewAsVcf(
                Paths.get("src/test/resources/htsjdk/hts-specs/test/vcf/4.3/passed/passed_body_id.vcf"));
        Assert.assertEquals(lines.get(0), "##fileformat=VCFv4.3");
        Assert.assertTrue(lines.stream().noneMatch(line -> line.startsWith("[W::")), lines.toString());
        Assert.assertTrue(lines.stream().noneMatch(line -> line.startsWith("##bcftools_view")), lines.toString());
        Assert.assertEquals(lines.stream().filter(line -> !line.startsWith("#")).count(), 4);
    }

    @Test
    public void testFailureIncludesStderr() {
        try {
            BcftoolsTestUtils.executeBcftoolsForStdout("view", "/no/such/file.vcf");
            Assert.fail("expected bcftools to fail");
        } catch (final RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("bcftools failed"), e.getMessage());
            Assert.assertTrue(e.getMessage().contains("/no/such/file.vcf"), e.getMessage());
        }
    }
}
