package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.beta.plugin.variants.VariantsDecoder;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.variantcontext.VariantContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Arrays;

public class VCFComplianceTests extends HtsjdkTest {
    //NOTE: to run these tests you need to clone https://github.com/samtools/hts-specs and initialize
    // this string to the location of the local repo
    private static final String HTS_SPECS_REPO_LOCATION = "/Users/cnorman/projects/hts-specs/";

    @DataProvider(name = "42Passed")
    private Object[] getVCF42Passed() {
        return getAbsoluteFileNamesIn(HTS_SPECS_REPO_LOCATION, "test/vcf/4.2/passed/");
    }

    @DataProvider(name = "42Failed")
    private Object[] getVCF42Failed() {
        return getAbsoluteFileNamesIn(HTS_SPECS_REPO_LOCATION, "test/vcf/4.2/failed/");
    }

    @DataProvider(name = "43Passed")
    private Object[] getVCF43Passed() {
        return getAbsoluteFileNamesIn(HTS_SPECS_REPO_LOCATION, "test/vcf/4.3/passed/");
    }

    @DataProvider(name = "43Failed")
    private Object[] getVCF43Failed() {
        return getAbsoluteFileNamesIn(HTS_SPECS_REPO_LOCATION, "test/vcf/4.3/failed/");
    }

    @Test(dataProvider = "42Passed")
    public void testVCF42ReadCompliancePassed(final String vcfFileName) {
        doReadTest(vcfFileName);
    }

    @Test(dataProvider = "42Failed", expectedExceptions = TribbleException.class)
    public void testVCF42ReadComplianceFailed(final String vcfFileName) {
        doReadTest(vcfFileName);
    }

    @Test(dataProvider = "43Passed")
    public void testVCF43ReadCompliancePassed(final String vcfFileName) {
        doReadTest(vcfFileName);
    }

    @Test(dataProvider = "43Failed", expectedExceptions = TribbleException.class)
    public void testVCF43ReadComplianceFailed(final String vcfFileName) {
        doReadTest(vcfFileName);
    }

    private void doReadTest(final String vcfFileName) {
        final IOPath inputVCFPath = new HtsPath(vcfFileName);
        try (final VariantsDecoder variantsDecoder = HtsDefaultRegistry.getVariantsResolver().getVariantsDecoder(inputVCFPath)) {
            for (final VariantContext vc : variantsDecoder) {
                //System.out.println(vc);
            }
        }
    }

    private Object[] getAbsoluteFileNamesIn(final String dir, final String subdir) {
        return Arrays.asList(new File(dir,subdir)
                .list())
                .stream()
                .map(t -> dir + subdir + t)
                .toArray();
    }

}
