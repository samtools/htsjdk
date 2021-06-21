package htsjdk.beta.plugin.bundle;

import java.io.ByteArrayInputStream;

/**
 * An input stream over the first N bytes of another input stream, used to allow multiple
 * codecs to probe those bytes for a file format/version signature.
 */
public final class SignatureProbingInputStream extends ByteArrayInputStream {
    final int signaturePrefixSize;

    /**
     * @param signaturePrefix the bytes containing the signature, over which the probing stream will be created
     * @param signaturePrefixSize signaturePrefixSize should be expressed in "compressed(/encrypted)" space
     *                           rather than "plaintext" space. For example, a raw signature may be n bytes
     *                           of decompressed ASCII, but the codec may need to consume an entire encrypted
     *                           GZIP block in order to inspect those n bytes. signaturePrefixSize should be
     *                           specified based on the block size, in order to ensure that the signature probing
     *                           stream contains a semantically meaningful fragment of the underlying input.
     */
    public SignatureProbingInputStream(final byte[] signaturePrefix, final int signaturePrefixSize) {
        super(signaturePrefix);
        this.signaturePrefixSize = signaturePrefixSize;
    }

    /**
     * @return the maximum number of bytes that can be consumed from this stream.
     */
    final int getMaximumReadLength() { return signaturePrefixSize;}

}
