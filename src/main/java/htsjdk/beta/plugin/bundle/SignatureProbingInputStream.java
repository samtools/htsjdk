package htsjdk.beta.plugin.bundle;

import java.io.ByteArrayInputStream;

/**
 * An input stream over the first N bytes of another input stream, used to allow multiple
 * codecs to probe those bytes for a file format/version signature.
 */
public final class SignatureProbingInputStream extends ByteArrayInputStream {
    final int signaturePrefixSize;

    //TODO: signaturePrefixSize should be expressed in "encrypted" space rather than "plaintext"
    // space. For example, a raw signature may be N bytes of ASCII, but the codec may need to
    // consume an entire encrypted GZIP block in order to inspect those N byes.
    public SignatureProbingInputStream(final byte[] signaturePrefix, final int signaturePrefixSize) {
        super(signaturePrefix);
        this.signaturePrefixSize = signaturePrefixSize;
    }

    /**
     * @return the maximum number of bytes that can be consumed from this stream.
     */
    final int getMaximumReadLength() { return signaturePrefixSize;}

}
