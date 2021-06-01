package htsjdk.beta.plugin.bundle;

import java.io.ByteArrayInputStream;

/**
 * An input stream over the first N bytes of another input stream, used to allow multiple
 * codecs to probe those bytes for a file format/version signature.
 */
public final class SignatureProbingInputStream extends ByteArrayInputStream {
    final int signaturePrefixSize;

    //TODO: Note that signaturePrefixSize is given in "plaintext" space. For encrypted streams,
    // the number of bytes required to probe the input may be greater than this number.
    public SignatureProbingInputStream(final byte[] signaturePrefix, final int signaturePrefixSize) {
        super(signaturePrefix);
        this.signaturePrefixSize = signaturePrefixSize;
    }

    /**
     * @return the maximum number of bytes that can be consumed from this stream.
     */
    final int getMaximumReadLength() { return signaturePrefixSize;}

}
