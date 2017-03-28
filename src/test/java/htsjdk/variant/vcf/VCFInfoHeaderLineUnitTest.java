package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Test conditions that are unique to INFO lines (not covered by VCFCompoundHeaderLineUnitTest).
 */
public class VCFInfoHeaderLineUnitTest extends HtsjdkTest {

    @Test
    public void testRepairInfoLineFlagTypeWithNonzeroCount() {
        final VCFInfoHeaderLine infoLine = new VCFInfoHeaderLine("<ID=FOO,Number=27,Type=Flag,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION);
        Assert.assertEquals(0, infoLine.getCount());
    }

}
