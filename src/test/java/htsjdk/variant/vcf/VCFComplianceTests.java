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
import java.util.HashSet;
import java.util.Set;

public class VCFComplianceTests extends HtsjdkTest {
    //NOTE: to run these tests you need to clone https://github.com/samtools/hts-specs and initialize
    // this string to the location of the local repo
    private static final String HTS_SPECS_REPO_LOCATION = "/Users/cnorman/projects/hts-specs/";

    @DataProvider(name = "42Passed")
    private Object[] getVCF42Passed() {
        final Set<String> excludeList = new HashSet<String>() {{
            add("passed_meta_pedigree.vcf");    // blank line the end
        }};
        return Arrays.stream(getFilesInDir(HTS_SPECS_REPO_LOCATION, "test/vcf/4.2/passed/"))
                .filter(file -> !excludeList.contains(file.getName()))
                .toArray();
    }

    @DataProvider(name = "42Failed")
    private Object[] getVCF42Failed() {
        return getFilesInDir(HTS_SPECS_REPO_LOCATION, "test/vcf/4.2/failed/");
    }

    @DataProvider(name = "43Passed")
    private Object[] getVCF43Passed() {
        final Set<String> excludeList = new HashSet<String>() {{
            add("passed_meta_pedigree.vcf");    // blank line the end
            add("passed_meta_alt.vcf");         // unclosed quote in header line <ID=complexcustomcontig!"#$%&'()*+-./;=?@[\]^_`{|}~,
        }};
        return Arrays.stream(getFilesInDir(HTS_SPECS_REPO_LOCATION, "test/vcf/4.3/passed/"))
                .filter(file -> !excludeList.contains(file.getName()))
                .toArray();
    }

    @DataProvider(name = "43Failed")
    private Object[] getVCF43Failed() {
        return getFilesInDir(HTS_SPECS_REPO_LOCATION, "test/vcf/4.3/failed/");
    }

    @Test(dataProvider = "42Passed")
    public void testVCF42ReadCompliancePassed(final File vcfFileName) {
        doReadTest(vcfFileName);
    }

    @Test(dataProvider = "42Failed", expectedExceptions = TribbleException.class)
    public void testVCF42ReadComplianceFailed(final File vcfFileName) {
        doReadTest(vcfFileName);
    }

    @Test(dataProvider = "43Passed")
    public void testVCF43ReadCompliancePassed(final File vcfFileName) {
        doReadTest(vcfFileName);
    }

    @Test(dataProvider = "43Failed", expectedExceptions = TribbleException.class)
    public void testVCF43ReadComplianceFailed(final File vcfFileName) {
        doReadTest(vcfFileName);
    }

    private void doReadTest(final File vcfFileName) {
        final IOPath inputVCFPath = new HtsPath(vcfFileName.getAbsolutePath());
        try (final VariantsDecoder variantsDecoder = HtsDefaultRegistry.getVariantsResolver().getVariantsDecoder(inputVCFPath)) {
            for (final VariantContext vc : variantsDecoder) {
                //System.out.println(vc);
            }
        }
    }

    private File[] getFilesInDir(final String dir, final String subdir) {
        return Arrays.stream(new File(dir,subdir).list())
                .map(fn -> new File(dir + subdir + fn))
                .toArray(File[]::new);
    }

}
