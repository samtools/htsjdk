package htsjdk.beta.codecs.variants.vcf.vcfv4_0;

import htsjdk.beta.codecs.variants.vcf.VCFEncoder;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;

/**
 * VCF V4.0 encoder.
 */
public class VCFEncoderV4_0 extends VCFEncoder {

    /**
     * Create a new VCF V4.0 encoder.
     *
     * @param outputBundle the output {@link Bundle} to encoder
     * @param variantsEncoderOptions the {@link VariantsEncoderOptions} to use
     */
    public VCFEncoderV4_0(final Bundle outputBundle, final VariantsEncoderOptions variantsEncoderOptions) {
        super(outputBundle,variantsEncoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return VCFCodecV4_0.VCF_V40_VERSION;
    }

}
