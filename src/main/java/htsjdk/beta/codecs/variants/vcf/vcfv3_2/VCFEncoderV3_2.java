package htsjdk.beta.codecs.variants.vcf.vcfv3_2;

import htsjdk.beta.codecs.variants.vcf.VCFEncoder;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;

/**
 * VCF V3.2 encoder.
 */
public class VCFEncoderV3_2 extends VCFEncoder {

    /**
     * Create a new VCF V3.2 encoder.
     *
     * @param outputBundle the output {@link Bundle} to encoder
     * @param variantsEncoderOptions the {@link VariantsEncoderOptions} to use
     */
    public VCFEncoderV3_2(final Bundle outputBundle, final VariantsEncoderOptions variantsEncoderOptions) {
        super(outputBundle,variantsEncoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return VCFCodecV3_2.VCF_V32_VERSION;
    }

}
