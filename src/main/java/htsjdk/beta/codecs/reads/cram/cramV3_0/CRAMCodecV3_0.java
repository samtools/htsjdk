package htsjdk.beta.codecs.reads.cram.cramV3_0;

import htsjdk.beta.codecs.reads.cram.CRAMCodec;
import htsjdk.beta.codecs.reads.cram.CRAMDecoder;
import htsjdk.beta.codecs.reads.cram.CRAMEncoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.SignatureProbingInputStream;
import htsjdk.exception.HtsjdkIOException;
import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.samtools.cram.structure.CramHeader;

import java.io.IOException;
import java.util.Arrays;

/**
 * CRAM v3.0 codec
 */
public class CRAMCodecV3_0 extends CRAMCodec {
    public static final HtsCodecVersion VERSION_3_0 = new HtsCodecVersion(3, 0, 0);
    protected static final String CRAM_MAGIC = new String(CramHeader.MAGIC) + "\3\0";

    @Override
    public HtsCodecVersion getVersion() {
        return VERSION_3_0;
    }

    @Override
    public int getSignatureSize() {
        return CRAM_MAGIC.length();
    }

    //TODO uses a byte array rather than a stream to reduce the need to repeatedly mark/reset the
    // stream for each codec
    @Override
    public boolean canDecodeSignature(final SignatureProbingInputStream probingInputStream, final String sourceName) {
        try {
            final byte[] signatureBytes = new byte[getSignatureSize()];
            final int numRead = probingInputStream.read(signatureBytes);
            if (numRead < getSignatureSize()) {
                throw new HtsjdkIOException(String.format("Failure reading content from stream for %s", sourceName));
            }
            return Arrays.equals(signatureBytes, CRAM_MAGIC.getBytes());
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
    public boolean runVersionUpgrade(final HtsCodecVersion sourceCodecVersion, final HtsCodecVersion targetCodecVersion) {
        throw new IllegalStateException("Not implemented");
    }

}
