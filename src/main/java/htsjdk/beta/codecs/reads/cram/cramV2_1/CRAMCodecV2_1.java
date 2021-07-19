package htsjdk.beta.codecs.reads.cram.cramV2_1;

import htsjdk.beta.codecs.reads.cram.CRAMCodec;
import htsjdk.beta.codecs.reads.cram.CRAMDecoder;
import htsjdk.beta.codecs.reads.cram.CRAMEncoder;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.samtools.cram.structure.CramHeader;

/**
 * CRAM v2.1 codec
 */
public class CRAMCodecV2_1 extends CRAMCodec {
    public static final HtsVersion VERSION_2_1 = new HtsVersion(2, 1, 0);
    private static final String CRAM_MAGIC_2_1 = new String(CramHeader.MAGIC) + "\2\1";

    @Override
    public HtsVersion getVersion() {
        return VERSION_2_1;
    }

    @Override
    public int getSignatureLength() {
        return CRAM_MAGIC_2_1.length();
    }

    @Override
    public CRAMDecoder getDecoder(final Bundle inputBundle, final ReadsDecoderOptions readsDecoderOptions) {
        return new CRAMDecoderV2_1(inputBundle, readsDecoderOptions);
    }

    @Override
    public CRAMEncoder getEncoder(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        return new CRAMEncoderV2_1(outputBundle, readsEncoderOptions);
    }

    @Override
    protected String getSignatureString() { return CRAM_MAGIC_2_1; }

}
