package htsjdk.beta.codecs.reads.cram.cramV2_1;

import htsjdk.beta.codecs.reads.cram.CRAMEncoder;
import htsjdk.beta.exception.HtsjdkUnsupportedOperationException;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;

/**
 * CRAM v2.1 encoder. htsjdk reads CRAM 2.1 but does not write it, so this encoder can't be created.
 */
public class CRAMEncoderV2_1 extends CRAMEncoder {

    /**
     * Create a CRAM encoder for the given output bundle. The primary resource in the bundle must
     * have content type {@link BundleResourceType#CT_ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param outputBundle bundle to encode
     * @param readsEncoderOptions options to use
     * @throws HtsjdkUnsupportedOperationException always, since htsjdk does not write CRAM 2.1
     */
    public CRAMEncoderV2_1(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        super(outputBundle, readsEncoderOptions);
        throw new HtsjdkUnsupportedOperationException(
                "Writing CRAM 2.1 is not supported; htsjdk writes CRAM 3.0 and 3.1");
    }

    @Override
    public HtsVersion getVersion() {
        return CRAMCodecV2_1.VERSION_2_1;
    }
}
