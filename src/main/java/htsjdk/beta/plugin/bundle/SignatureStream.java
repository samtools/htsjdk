package htsjdk.beta.plugin.bundle;

import java.io.ByteArrayInputStream;

/**
 * An input stream over the first {@code signaturePrefixLength} bytes of another input stream, used to
 * allow multiple codecs to probe those bytes for a file format/version signature.
 */
public final class SignatureStream extends ByteArrayInputStream {
    final int signaturePrefixLength;

    /**
     * Create a signature probe stream containing the first signaturePrefixLength bytes of an input
     * stream that can be probed for a signature.
     *
     * @param signaturePrefixLength signaturePrefixLength should be expressed in "compressed(/encrypted)" space
     *                              rather than "plaintext" space. For example, a raw signature may be {@code n}
     *                              bytes of decompressed ASCII, but the codec may need to consume an entire
     *                              encrypted GZIP block in order to inspect those {@code n} bytes.
     *                              signaturePrefixLength should be specified based on the block size, in order
     *                              to ensure that the signature probe stream contains a semantically meaningful
     *                              fragment of the underlying input.
     * @param signaturePrefix the bytes containing the signature, over which the probe stream will be created
     */
    public SignatureStream(final int signaturePrefixLength, final byte[] signaturePrefix) {
        super(signaturePrefix);
        this.signaturePrefixLength = signaturePrefixLength;
    }

    /**
     * Get the maximum number of bytes that can be consumed from this stream.
     *
     * @return the maximum number of bytes that can be consumed from this stream.
     */
    public final int getSignaturePrefixLength() { return signaturePrefixLength;}

}
