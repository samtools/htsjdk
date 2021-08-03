package htsjdk.beta.codecs.variants.vcf.vcfv3_3;

import htsjdk.beta.codecs.variants.vcf.VCFDecoder;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;
import htsjdk.variant.vcf.VCF3Codec;

/**
 * VCF V3.3 decoder.
 */
public class VCFDecoderV3_3 extends VCFDecoder {

    /**
     * Create a new VCF V3.3 decoder.
     *
     * @param inputBundle the input {@link Bundle} to decode
     * @param variantsDecoderOptions the {@link VariantsDecoderOptions} for this decoder
     */
    public VCFDecoderV3_3(final Bundle inputBundle, final VariantsDecoderOptions variantsDecoderOptions) {
        super(inputBundle, new VCF3Codec(), variantsDecoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return VCFCodecV3_3.VCF_V33_VERSION;
    }

}
