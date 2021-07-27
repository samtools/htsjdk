package htsjdk.beta.codecs.variants.vcf.vcfv3_2;

import htsjdk.beta.codecs.variants.vcf.VCFCodec;
import htsjdk.beta.codecs.variants.vcf.VCFDecoder;
import htsjdk.beta.codecs.variants.vcf.VCFEncoder;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.utils.ValidationUtils;

/**
 * VCF V3.2 codec.
 */
public class VCFCodecV3_2 extends VCFCodec {
    public static final HtsVersion VCF_V32_VERSION = new HtsVersion(3,2,0);

    private static final String VCF_V32_MAGIC = "##format=VCRv3.2";

    @Override
    public HtsVersion getVersion() { return VCF_V32_VERSION; }

    @Override
    public VCFDecoder getDecoder(final Bundle inputBundle, final VariantsDecoderOptions decoderOptions) {
        ValidationUtils.nonNull(inputBundle, "inputBundle");
        ValidationUtils.nonNull(decoderOptions, "decoderOptions");

        return new VCFDecoderV3_2(inputBundle, decoderOptions);
    }

    @Override
    public VCFEncoder getEncoder(final Bundle outputBundle, final VariantsEncoderOptions encoderOptions) {
        ValidationUtils.nonNull(outputBundle, "outputBundle");
        ValidationUtils.nonNull(encoderOptions, "encoderOptions");

        return new VCFEncoderV3_2(outputBundle, encoderOptions);
    }

    @Override
    public boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion) {
        throw new HtsjdkPluginException("Not yet implemented");
    }

    @Override
    protected String getSignatureString() { return VCF_V32_MAGIC; }

}
