package htsjdk.beta.codecs.variants.vcf.vcfv4_1;

import htsjdk.beta.codecs.variants.vcf.VCFDecoder;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;

/**
 * VCF V4.1 decoder.
 */
public class VCFDecoderV4_1 extends VCFDecoder {

    /**
     * Create a new VCF V4.1 decoder.
     *
     * @param inputBundle the input {@link Bundle} to decode
     * @param variantsDecoderOptions the {@link VariantsDecoderOptions} for this decoder
     */
    public VCFDecoderV4_1(final Bundle inputBundle, final VariantsDecoderOptions variantsDecoderOptions) {
        super(inputBundle, new htsjdk.variant.vcf.VCFCodec(), variantsDecoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return VCFCodecV4_1.VCF_V41_VERSION;
    }

}
