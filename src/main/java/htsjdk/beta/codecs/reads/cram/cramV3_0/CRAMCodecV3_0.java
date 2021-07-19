package htsjdk.beta.codecs.reads.cram.cramV3_0;

import htsjdk.beta.codecs.reads.cram.CRAMCodec;
import htsjdk.beta.codecs.reads.cram.CRAMDecoder;
import htsjdk.beta.codecs.reads.cram.CRAMEncoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.SignatureStream;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.samtools.cram.structure.CramHeader;

import java.io.IOException;
import java.util.Arrays;

/**
 * CRAM v3.0 codec
 */
public class CRAMCodecV3_0 extends CRAMCodec {
    public static final HtsVersion VERSION_3_0 = new HtsVersion(3, 0, 0);
    private static final String CRAM_MAGIC_3_0 = new String(CramHeader.MAGIC) + "\3\0";

    @Override
    public HtsVersion getVersion() {
        return VERSION_3_0;
    }

    @Override
    public int getSignatureLength() {
        return CRAM_MAGIC_3_0.length();
    }

    @Override
    public boolean canDecodeSignature(final SignatureStream signatureStream, final String sourceName) {
        try {
            final byte[] signatureBytes = new byte[getSignatureLength()];
            final int numRead = signatureStream.read(signatureBytes);
            if (numRead < getSignatureLength()) {
                throw new HtsjdkIOException(String.format("Failure reading content from stream for %s", sourceName));
            }
            return Arrays.equals(signatureBytes, getSignatureString().getBytes());
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Failure reading content from stream for %s", sourceName));
        }
    }

    @Override
    public CRAMDecoder getDecoder(final Bundle inputBundle, final ReadsDecoderOptions readsDecoderOptions) {
        return new CRAMDecoderV3_0(inputBundle, readsDecoderOptions);
    }

    @Override
    public CRAMEncoder getEncoder(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        return new CRAMEncoderV3_0(outputBundle, readsEncoderOptions);
    }

    @Override
    protected String getSignatureString() { return CRAM_MAGIC_3_0; }

}
