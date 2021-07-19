package htsjdk.beta.codecs.reads.cram.cramV2_1;

import htsjdk.beta.codecs.reads.cram.CRAMDecoder;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;

/**
 * CRAM v2.1decoder.
 */
public class CRAMDecoderV2_1 extends CRAMDecoder {

    public CRAMDecoderV2_1(final Bundle bundle, final ReadsDecoderOptions readsDecoderOptions) {
        super(bundle, readsDecoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return CRAMCodecV2_1.VERSION_2_1;
    }

}
