package htsjdk.beta.codecs.variants.vcf.vcfv4_0;

import htsjdk.beta.codecs.variants.vcf.VCFCodec;
import htsjdk.beta.codecs.variants.vcf.VCFDecoder;
import htsjdk.beta.codecs.variants.vcf.VCFEncoder;
import htsjdk.beta.exception.HtsjdkUnsupportedOperationException;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.utils.ValidationUtils;

/**
 * VCF V4.0 codec.
 */
public class VCFCodecV4_0 extends VCFCodec {
    public static final HtsVersion VCF_V40_VERSION = new HtsVersion(4,0,0);

    private static final String VCF_V40_MAGIC = "##fileformat=VCFv4.0";

    @Override
    public HtsVersion getVersion() { return VCF_V40_VERSION; }

    @Override
    public VCFDecoder getDecoder(final Bundle inputBundle, final VariantsDecoderOptions decoderOptions) {
        ValidationUtils.nonNull(inputBundle, "inputBundle");
        ValidationUtils.nonNull(decoderOptions, "decoderOptions");

        return new VCFDecoderV4_0(inputBundle, decoderOptions);
    }

    @Override
    public VCFEncoder getEncoder(final Bundle outputBundle, final VariantsEncoderOptions encoderOptions) {
        ValidationUtils.nonNull(outputBundle, "outputBundle");
        ValidationUtils.nonNull(encoderOptions, "encoderOptions");

        return new VCFEncoderV4_0(outputBundle, encoderOptions);
    }

    @Override
    public boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion) {
        throw new HtsjdkUnsupportedOperationException("Version upgrade not yet implemented");
    }

    @Override
    protected String getSignatureString() { return VCF_V40_MAGIC; }

}
