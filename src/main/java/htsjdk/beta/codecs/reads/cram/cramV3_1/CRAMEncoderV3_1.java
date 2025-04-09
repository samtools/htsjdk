package htsjdk.beta.codecs.reads.cram.cramV3_1;

import htsjdk.beta.codecs.reads.cram.CRAMEncoder;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.samtools.cram.CRAMException;

/**
 * CRAM v3.1 encoder.
 */
public class CRAMEncoderV3_1 extends CRAMEncoder {

    /**
     * Create a new CRAM v3.1 encoder for the given output bundle. The primary resource in the
     * bundle must have content type {@link BundleResourceType#CT_ALIGNED_READS} (to find an encoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param outputBundle output {@link Bundle} to encode
     * @param readsEncoderOptions {@link ReadsEncoderOptions} to use
     */
    public CRAMEncoderV3_1(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        super(outputBundle, readsEncoderOptions);
        throw new CRAMException("CRAM v3.1 encoding is not yet supported");
    }

    @Override
    public HtsVersion getVersion() {
        return CRAMCodecV3_1.VERSION_3_1;
    }

}
