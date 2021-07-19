package htsjdk.beta.codecs.reads.cram.cramV3_0;

import htsjdk.beta.codecs.reads.cram.CRAMEncoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;

/**
 * CRAM v3.0 encoder.
 */
public class CRAMEncoderV3_0 extends CRAMEncoder {

    /**
     * Create a new CRAM CRAM v3.0 encoder for the given output bundle. The primary resource in the
     * bundle must have content type {@link BundleResourceType#ALIGNED_READS} (to find an encoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param outputBundle output {@link Bundle} to encode
     * @param readsEncoderOptions {@link ReadsEncoderOptions} to use
     */
    public CRAMEncoderV3_0(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        super(outputBundle, readsEncoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return CRAMCodecV3_0.VERSION_3_0;
    }

}
