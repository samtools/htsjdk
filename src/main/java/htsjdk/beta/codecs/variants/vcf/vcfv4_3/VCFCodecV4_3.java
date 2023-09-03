package htsjdk.beta.codecs.variants.vcf.vcfv4_3;

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
 * VCF V4.3 codec.
 */
public class VCFCodecV4_3 extends VCFCodec {
    public static final HtsVersion VCF_V43_VERSION = new HtsVersion(4,3,0);

    private static final String VCF_V43_MAGIC = "##fileformat=VCFv4.3";

    @Override
    public HtsVersion getVersion() { return VCF_V43_VERSION; }

    @Override
    public VCFDecoder getDecoder(final Bundle inputBundle, final VariantsDecoderOptions decoderOptions) {
        ValidationUtils.nonNull(inputBundle, "inputBundle");
        ValidationUtils.nonNull(decoderOptions, "decoderOptions");

        return new VCFDecoderV4_3(inputBundle, decoderOptions);
    }

    @Override
    public VCFEncoder getEncoder(final Bundle outputBundle, final VariantsEncoderOptions encoderOptions) {
        ValidationUtils.nonNull(outputBundle, "outputBundle");
        ValidationUtils.nonNull(encoderOptions, "encoderOptions");

        return new VCFEncoderV4_3(outputBundle, encoderOptions);
    }

    @Override
    public boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion) {
        throw new HtsjdkUnsupportedOperationException("Version upgrade not yet implemented");
    }

    @Override
    protected String getSignatureString() { return VCF_V43_MAGIC; }

}
