package htsjdk.beta.codecs.variants.vcf.vcfv4_1;

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
 * VCF V4.1 codec.
 */
public class VCFCodecV4_1 extends VCFCodec {
    public static final HtsVersion VCF_V41_VERSION = new HtsVersion(4,1,0);

    private static final String VCF_V41_MAGIC = "##fileformat=VCFv4.1";

    @Override
    public HtsVersion getVersion() { return VCF_V41_VERSION; }

    @Override
    public VCFDecoder getDecoder(final Bundle inputBundle, final VariantsDecoderOptions decoderOptions) {
        ValidationUtils.nonNull(inputBundle, "inputBundle");
        ValidationUtils.nonNull(decoderOptions, "decoderOptions");

        return new VCFDecoderV4_1(inputBundle, decoderOptions);
    }

    @Override
    public VCFEncoder getEncoder(final Bundle outputBundle, final VariantsEncoderOptions encoderOptions) {
        ValidationUtils.nonNull(outputBundle, "outputBundle");
        ValidationUtils.nonNull(encoderOptions, "encoderOptions");

        return new VCFEncoderV4_1(outputBundle, encoderOptions);
    }

    @Override
    public boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion) {
        throw new HtsjdkUnsupportedOperationException("Version upgrade not yet implemented");
    }

    @Override
    protected String getSignatureString() { return VCF_V41_MAGIC; }

}
