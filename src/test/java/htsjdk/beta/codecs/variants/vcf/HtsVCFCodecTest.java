package htsjdk.beta.codecs.variants.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.beta.codecs.variants.vcf.vcfv3_2.VCFCodecV3_2;
import htsjdk.beta.codecs.variants.vcf.vcfv3_3.VCFCodecV3_3;
import htsjdk.beta.codecs.variants.vcf.vcfv4_0.VCFCodecV4_0;
import htsjdk.beta.codecs.variants.vcf.vcfv4_1.VCFCodecV4_1;
import htsjdk.beta.codecs.variants.vcf.vcfv4_2.VCFCodecV4_2;
import htsjdk.beta.codecs.variants.vcf.vcfv4_3.VCFCodecV4_3;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.variants.VariantsDecoder;
import htsjdk.beta.plugin.variants.VariantsEncoder;
import htsjdk.beta.plugin.variants.VariantsFormats;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class HtsVCFCodecTest extends HtsjdkTest {
    final IOPath VARIANTS_TEST_DIR = new HtsPath("src/test/resources/htsjdk/");

    @DataProvider(name="vcfReadWriteTests")
    private Object[][] vcfReadWriteTests() {
        return new Object[][] {
                // one test case for each supported VCF version
                { new HtsPath(VARIANTS_TEST_DIR + "variant/vcfexampleV3.2.vcf"), VCFCodecV3_2.VCF_V32_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "tribble/tabix/trioDup.vcf"), VCFCodecV3_3.VCF_V33_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "variant/HiSeq.10000.vcf"), VCFCodecV4_0.VCF_V40_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "variant/dbsnp_135.b37.1000.vcf"), VCFCodecV4_1.VCF_V41_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "variant/vcf42HeaderLines.vcf"), VCFCodecV4_2.VCF_V42_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "variant/NA12891.vcf.gz"), VCFCodecV4_2.VCF_V42_VERSION },
                // v4.3 is left out since these tests write to the newest writeable VCF version (4.2), but we can't
                // write a header from a v4.3 source since it will (correctly) be rejected by the v4.2 writer
        };
    }

    @Test(dataProvider = "vcfReadWriteTests")
    public void testRoundTripVCF(final IOPath inputPath, final HtsVersion expectedCodecVersion) {
        readWriteVCF(inputPath, expectedCodecVersion);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectWritingV43HeaderAsV42()  {
        // read vcf v4.3 and try to write it to a vcf v4.2 (header is rejected)
        readWriteVCF(new HtsPath(VARIANTS_TEST_DIR + "variant/vcf43/all43Features.vcf"), VCFCodecV4_3.VCF_V43_VERSION);
    }

    private void readWriteVCF(final IOPath inputPath, final HtsVersion expectedCodecVersion) {
        final IOPath outputPath = IOUtils.createTempPath("pluginVariants", ".vcf");

        // some test files require "AllowMissingFields" options for writing
        final VariantsEncoderOptions variantsEncoderOptions = new VariantsEncoderOptions().setAllowFieldsMissingFromHeader(true);
        try (final VariantsDecoder variantsDecoder = HtsDefaultRegistry.getVariantsResolver().getVariantsDecoder(inputPath);
             final VariantsEncoder variantsEncoder = HtsDefaultRegistry.getVariantsResolver().getVariantsEncoder(
                     outputPath,
                     variantsEncoderOptions)) {

            Assert.assertNotNull(variantsDecoder);
            Assert.assertEquals(variantsDecoder.getFileFormat(), VariantsFormats.VCF);
            Assert.assertEquals(variantsDecoder.getVersion(), expectedCodecVersion);

            Assert.assertNotNull(variantsEncoder);
            Assert.assertEquals(variantsEncoder.getFileFormat(), VariantsFormats.VCF);
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
