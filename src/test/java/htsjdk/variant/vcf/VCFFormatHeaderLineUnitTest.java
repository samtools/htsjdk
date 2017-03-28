package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import org.testng.annotations.Test;

/**
 * Test conditions that are unique to FORMAT lines (not covered by VCFCompoundHeaderLineUnitTest).
 */
public class VCFFormatHeaderLineUnitTest {

    // FORMAT lines aren't allowed to have type==Flag
    @Test(expectedExceptions=TribbleException.class)
    public void testRejectInfoLineWithFlagField() {
        new VCFFormatHeaderLine("<ID=FOO,Number=0,Type=Flag,Description=\"foo\">", VCFHeader.DEFAULT_VCF_VERSION);
    }

}
