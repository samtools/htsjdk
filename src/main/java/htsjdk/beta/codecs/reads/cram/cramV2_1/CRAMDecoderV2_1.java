package htsjdk.beta.codecs.reads.cram.cramV2_1;

import htsjdk.beta.codecs.reads.cram.CRAMDecoder;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;

/**
 * CRAM v2.1decoder.
 */
public class CRAMDecoderV2_1 extends CRAMDecoder {

    /**
     * Create a new V2.1 CRAM decoder. The primary resource in the input
     * bundle must have content type {@link BundleResourceType#ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param bundle the input {@link Bundle} to decode
     * @param readsDecoderOptions the {@link ReadsDecoderOptions} to use
     */
    public CRAMDecoderV2_1(final Bundle bundle, final ReadsDecoderOptions readsDecoderOptions) {
        super(bundle, readsDecoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return CRAMCodecV2_1.VERSION_2_1;
    }

}
