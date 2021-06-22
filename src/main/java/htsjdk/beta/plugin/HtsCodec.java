package htsjdk.beta.plugin;

import htsjdk.beta.plugin.bundle.SignatureProbingInputStream;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.bundle.Bundle;

/**
 * Base interface implemented by all HtsCodec implementations.
 *
 * A codec is a handler for a specific combination of file-format and version, possibly specific
 * to some URI protocol. For example, a BAM codec handles the BAM format for the "file://" protocol (or
 * other nio.Path file system providers such as gs:// or hdfs://, and an HtsGet codec handles BAM via
 * "http://" or "htsget://" protocol.
 *
 * Codecs may delegate to/reuse shared encoder/decoder implementations.
 *
 * @param <F> an enum representing the various serialized formats handled by this codec
 * @param <D> decoder options for this codec
 * @param <E> encoder options for this codec
 */
public interface HtsCodec<
        F extends Enum<F>,
        D extends HtsDecoderOptions,
        E extends HtsEncoderOptions>
        extends Upgradeable {

    /**
     * @return the {@link HtsCodecType} for this codec. The {@link HtsCodecType} determines the HEADER and
     * RECORD interfaces exposed by the encoder and decoder for this codec. The underlying file format may
     * vary amongst different codecs that have the same codec type, since the serialized file format may be
     * different (i.e., both the BAM and HTSGET_BAM codecs have codec type "ALIGNED_READS" because they
     * both render records as SAMRecord, even though underlying file formats are different).
     */
    HtsCodecType getCodecType();

    /**
     * @return a value representing the underlying file format handled by this codec, taken from the format
     * enum for this codec ({@link F} (i.e., for codec type ALIGNED_READS, the values may be from {BAM, CRAM,
     * SAM}).
     */
    F getFileFormat();

    /**
     * @return the file format version ({@link HtsVersion}) supported by this codec
     */
    HtsVersion getVersion();

    /**
     * @return a user-friendly informational display name for a given instance of this codec
     */
    default String getDisplayName() {
        return String.format("%s/%s/%s", getFileFormat(), getVersion(), getClass().getName());
    }

    /**
     * Return true if this codec claims to "own" this URI. The implementation should verify that it
     * recognizes and handles the protocol scheme in the URI, as well as the file extension, if one is
     * present, and any query parameters, and that does not depend an NIO file system provider in order
     * to decode or encode from or to that URI. If the implementation does not recognize the protocol
     * scheme, it should never return true.
     *
     * During codec resolution, if any registered codec returns true for this URI, the signature probing
     * protocol will:
     *
     *  1) immediately prune the list of candidate codecs to only those that return true for this method
     *  2) not attempt to obtain an "InputStream" on the IOPath, on the assumption that custom protocol
     *     handlers must perform special handling to access the underlying scheme (i.e., htsget codec would
     *     claim an "http://" uri if the rest of the URI conforms to the expected format for that codec's
     *     protocol.
     *
     * Any codec that returns true for a given IOPath must also return true from {@link #canDecodeURI}
     * when given the same IOPath.
     *
     * @param resource the resource to inspect
     * @return true if the URI represents a custom URI that this codec handles
     */
    default boolean ownsURI(final IOPath resource) { return false; }

    /**
     * Most implementations only look at the extension. Implementations should not inspect the
     * protocol scheme unless the codec requires handles a specific protocol scheme.
     *
     * TODO: describe the two-phase opt-in protocol
     * first, ownsURI,
     * then "canDecodeURI" or signature probe
     * If the URI is ambiguous, and
     *
     * @param resource to be decoded
     * @return true if the codec can provide a decoder to provide this URL
     */
    //TODO: we need to specify how to handle the case where the URI is ambiguous (ie., no
    // custom protocol, no recognizable file extension, etc. - do you return true or false) to
    // support the case where the codec needs to see the actual stream. i.e., .tsv codecs can't
    // tell from extension and MUST resolve to a stream to determine if it can decode
    boolean canDecodeURI(final IOPath resource);

    /**
     * Determine if this codec can decode this input stream by inspecting the signature embedded
     * within the stream. The probingInputStream stream is guaranteed to contain the lesser of
     * of: 1) the number bytes {@link #getSignatureProbeSize} returns for this codec or 2) the
     * entire input stream.
     *
     * Codecs that are custom URI handlers (return true for {@link #ownsURI}), should always return false from this method.
     *
     * @param probingInputStream
     * @param sourceName
     * @return true if this codec recognizes the stream signature, and can provide a decoder to decode the stream
     */
    boolean canDecodeStreamSignature(final SignatureProbingInputStream probingInputStream, final String sourceName);

    /**
     * @return the size of the signature/version for this file format.
     */
    int getSignatureActualSize();

    /**
     * @return the number of bytes this codec must consume from a stream in order to determine whether
     * it can decode that stream. This number may differ from the size returned by {@link #getSignatureActualSize}
     * for compressed/encrypted streams, which may require a larger and more semantically meaningful input fragment
     * (such as an entire encrypted or compressed block) in order to inspect the plaintext signature. The
     * signatureProbeSize should be expressed in "compressed/encrypted" space rather than "plaintext" space. For
     * example, a raw signature may be N bytes of decompressed ASCII, but the codec may need to consume an entire
     * encrypted GZIP block in order to inspect those N bytes, so the signaturePrefixSize should be specified based
     * on the compressed block size.
     */
    default int getSignatureProbeSize() { return getSignatureActualSize(); }

    /**
     * Return an {@link HtsDecoder}-derived object that can decode the provided inputs
     * @param inputBundle input to be decoded
     * @param decoderOptions options for the decoder to use
     * @return an {@link HtsDecoder}-dervied object that can decode the provided inputs
     */
    HtsDecoder<F, ?, ? extends HtsRecord> getDecoder(final Bundle inputBundle, final D decoderOptions);

    /**
     * Return an {@link HtsEncoder}-derived object suitable for writing to the provided outputs
     * @param outputBundle target output for the encoder
     * @param encoderOptions encoder options to use
     * @return an {@link HtsEncoder}-derived object suitable for writing to the provided outputs
     */
    HtsEncoder<F, ?, ? extends HtsRecord> getEncoder(final Bundle outputBundle, final E encoderOptions);

}
