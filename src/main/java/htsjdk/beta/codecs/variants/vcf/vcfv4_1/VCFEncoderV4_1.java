package htsjdk.beta.codecs.variants.vcf.vcfv4_1;

import htsjdk.beta.codecs.variants.vcf.VCFEncoder;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;

/**
 * VCF V4.1 encoder.
 */
public class VCFEncoderV4_1 extends VCFEncoder {

    /**
     * Create a new VCF V4.1 encoder.
     *
     * @param outputBundle the output {@link Bundle} to encoder
     * @param variantsEncoderOptions the {@link VariantsEncoderOptions} to use
     */
    public VCFEncoderV4_1(final Bundle outputBundle, final VariantsEncoderOptions variantsEncoderOptions) {
        super(outputBundle,variantsEncoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return VCFCodecV4_1.VCF_V41_VERSION;
    }

}
