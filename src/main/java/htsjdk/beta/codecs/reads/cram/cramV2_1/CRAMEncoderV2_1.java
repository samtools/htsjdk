package htsjdk.beta.codecs.reads.cram.cramV2_1;

import htsjdk.beta.codecs.reads.cram.CRAMEncoder;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;

/**
 * CRAM v2.1 encoder.
 */
public class CRAMEncoderV2_1 extends CRAMEncoder {

    /**
     * Create a CRAM encoder for the given output bundle. The primary resource in the bundle must
     * have content type {@link BundleResourceType#CT_ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param outputBundle bundle to encode
     * @param readsEncoderOptions options to use
     */
    public CRAMEncoderV2_1(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        super(outputBundle, readsEncoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return CRAMCodecV2_1.VERSION_2_1;
    }

}
