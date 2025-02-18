package htsjdk.beta.codecs.reads.cram.cramV3_1;

import htsjdk.beta.codecs.reads.cram.CRAMCodec;
import htsjdk.beta.codecs.reads.cram.CRAMDecoder;
import htsjdk.beta.codecs.reads.cram.CRAMEncoder;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.SignatureStream;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.samtools.cram.structure.CramHeader;

import java.io.IOException;
import java.util.Arrays;

/**
 * CRAM v3.1 codec
 */
public class CRAMCodecV3_1 extends CRAMCodec {
    public static final HtsVersion VERSION_3_1 = new HtsVersion(3, 1, 0);
    private static final String CRAM_MAGIC_3_1 = new String(CramHeader.MAGIC) + "\3\1";

    @Override
    public HtsVersion getVersion() {
        return VERSION_3_1;
    }

    @Override
    public int getSignatureLength() {
        return CRAM_MAGIC_3_1.length();
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
        return new CRAMDecoderV3_1(inputBundle, readsDecoderOptions);
    }

    @Override
    public CRAMEncoder getEncoder(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        return new CRAMEncoderV3_1(outputBundle, readsEncoderOptions);
    }

    @Override
    protected String getSignatureString() { return CRAM_MAGIC_3_1; }

}
