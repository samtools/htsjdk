package htsjdk.beta.codecs.reads.cram.cramV3_1;

import htsjdk.beta.codecs.reads.cram.CRAMDecoder;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;

/**
 * CRAM v3.1 decoder.
 */
public class CRAMDecoderV3_1 extends CRAMDecoder {

    /**
     * Create a new CRAM v3.1 decoder. The primary resource in the input
     * bundle must have content type {@link BundleResourceType#CT_ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param bundle input {@link Bundle} to decode
     * @param readsDecoderOptions {@link ReadsDecoderOptions} to use
     */
    public CRAMDecoderV3_1(final Bundle bundle, final ReadsDecoderOptions readsDecoderOptions) {
        super(bundle, readsDecoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return CRAMCodecV3_1.VERSION_3_1;
    }

}
