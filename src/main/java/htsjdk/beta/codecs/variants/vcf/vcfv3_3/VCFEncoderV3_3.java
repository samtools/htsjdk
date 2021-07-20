package htsjdk.beta.codecs.variants.vcf.vcfv3_3;

import htsjdk.beta.codecs.variants.vcf.VCFEncoder;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;

/**
 * VCF V3.3 encoder.
 */
public class VCFEncoderV3_3 extends VCFEncoder {

    /**
     * Create a new VCF V3.3 encoder.
     *
     * @param outputBundle the output {@link Bundle} to encoder
     * @param variantsEncoderOptions the {@link VariantsEncoderOptions} to use
     */
    public VCFEncoderV3_3(final Bundle outputBundle, final VariantsEncoderOptions variantsEncoderOptions) {
        super(outputBundle,variantsEncoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return VCFCodecV3_3.VCF_V33_VERSION;
    }

}
