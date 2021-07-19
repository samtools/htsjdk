package htsjdk.beta.codecs.reads.cram.cramV3_0;

import htsjdk.beta.codecs.reads.cram.CRAMDecoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;

/**
 * CRAM v3.0 decoder.
 */
public class CRAMDecoderV3_0 extends CRAMDecoder {

    /**
     * Create a new CRAM V3.0 decoder. The primary resource in the input
     * bundle must have content type {@link BundleResourceType#ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param bundle input {@link Bundle} to decode
     * @param readsDecoderOptions {@link ReadsDecoderOptions} to use
     */
    public CRAMDecoderV3_0(final Bundle bundle, final ReadsDecoderOptions readsDecoderOptions) {
        super(bundle, readsDecoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return CRAMCodecV3_0.VERSION_3_0;
    }

}
