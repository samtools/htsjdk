package htsjdk.beta.codecs.variants.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.beta.codecs.variants.vcf.vcfv4_2.VCFCodecV4_2;
import htsjdk.beta.plugin.registry.HtsVariantsCodecs;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.variants.VariantsDecoder;
import htsjdk.beta.plugin.variants.VariantsEncoder;
import htsjdk.beta.plugin.variants.VariantsFormat;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class HtsVCFCodecTest extends HtsjdkTest {
    final IOPath VARIANTS_TEST_DIR = new HtsPath("src/test/resources/htsjdk/variant/");

    @DataProvider(name="vcfTests")
    private Object[][] vcfTests() {
        return new Object[][] {
                // a .vcf, .vcf.gz, .vcf with UTF8 chars, and .vcf.gz with UTF8 chars
                { new HtsPath(VARIANTS_TEST_DIR + "vcf42HeaderLines.vcf") },
                { new HtsPath(VARIANTS_TEST_DIR + "NA12891.vcf.gz") },
        };
    }

    @Test(dataProvider = "vcfTests")
    public void testRoundTripVCF(final IOPath inputPath) {
        final IOPath outputPath = new HtsPath("pluginVariants.vcf");

        try (final VariantsDecoder variantsDecoder = HtsVariantsCodecs.getVariantsDecoder(inputPath);
             final VariantsEncoder variantsEncoder = HtsVariantsCodecs.getVariantsEncoder(outputPath)) {

            Assert.assertNotNull(variantsDecoder);
            Assert.assertEquals(variantsDecoder.getFormat(), VariantsFormat.VCF);
            Assert.assertEquals(variantsDecoder.getVersion(), VCFCodecV4_2.VCF_V42_VERSION);

            Assert.assertNotNull(variantsEncoder);
            Assert.assertEquals(variantsEncoder.getFormat(), VariantsFormat.VCF);
            Assert.assertEquals(variantsEncoder.getVersion(), VCFCodecV4_2.VCF_V42_VERSION);

            final VCFHeader vcfHeader = variantsDecoder.getHeader();
            Assert.assertNotNull(vcfHeader);

            variantsEncoder.setHeader(vcfHeader);
            for (final VariantContext vc : variantsDecoder) {
                variantsEncoder.write(vc);
            }
        }
    }
}
