package htsjdk.variant.vcf;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Test conditions that are unique to INFO lines (not covered by VCFCompoundHeaderLineUnitTest).
 */
public class VCFInfoHeaderLineUnitTest {

    @Test
    public void testRepairInfoLineFlagTypeWithNonzeroCount() {
        VCFInfoHeaderLine infoLine = new VCFInfoHeaderLine("<ID=FOO,Number=27,Type=Flag,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION);
        Assert.assertEquals(0, infoLine.getCount());
    }

}
