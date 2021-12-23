package htsjdk.utils;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.ProcessExecutor;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

public class BCFToolsTestUtilsTest extends HtsjdkTest {

    @Test
    public void testBCFToolsIsAvailable() {
        Assert.assertTrue(BCFToolsTestUtils.isBCFToolsAvailable());
    }

    @Test
    public void testBCFToolsVersion() {
        if (!BCFToolsTestUtils.isBCFToolsAvailable()) {
            throw new SkipException("bcftools not available on local device");
        }
        // If this test runs, but fails because version validation fails, then the local bcftools version is
        // not the one expected by the htsjdk tests
        final ProcessExecutor.ExitStatusAndOutput processStatus = BCFToolsTestUtils.executeBCFToolsCommand("--version");
        Assert.assertTrue(processStatus.stdout.contains(BCFToolsTestUtils.expectedBCFtoolsVersion));
    }


    @Test(expectedExceptions = RuntimeException.class)
    public void testBCFToolsPresentButCommandFails() {
        if (!BCFToolsTestUtils.isBCFToolsAvailable()) {
            throw new SkipException("bcftools not available on local device");
        }
        BCFToolsTestUtils.executeBCFToolsCommand("--notABcftoolsCommand");
    }
}
