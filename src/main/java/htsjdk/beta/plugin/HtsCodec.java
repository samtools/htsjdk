package htsjdk.beta.plugin;

import htsjdk.beta.plugin.bundle.SignatureProbingInputStream;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.bundle.Bundle;

/**
 * Base interface implemented by all HtsCodec implementations.
 *
 * A codec is a handler for a specific combination of file-format, version, possibly specific
 * to some URI protocol. For example, a BAM codec handles the BAM format for the "file://" protocol, or
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
     * Return true if this codec claims to handle the URI protocol scheme (and file extension) for this
     * IOPath. Codecs should only return true if the supported protocol scheme requires special handling
     * that precludes use of an NIO file system provider.
     *
     * During codec resolution, ff any registered codec returns true for this URI, the signature probing
     * protocol will:
     *
     *  1) immediately prune the list of candidate codecs to only those that return true for this method
     *  2) not attempt to obtain an "InputStream" on the IOPath, on the assumption that custom protocol
     *     schemes must perform special handling to access the underlying scheme (i.e., htsget codec would
     *     claim an "http:" uri if the rest of the URI conforms to the expected format for that codec's
     *     protocol.
     *
     * @param resource the resource to inspect
     * @return true if the URI represents a custom URI that this codec handles
     */
    //TODO: rename this to "ownsURI" or "handlesURI" or "isHandlerFor" ?
    default boolean claimURI(final IOPath resource) { return false; }

    /**
     * Most implementations only look at the extension. Protocol scheme is excluded because
     * codecs don't opt-out based on protocol scheme, only opt-in (via claimCustomURI).
     * @param resource to be decoded
     * @return true if the codec can provide a decoder to provide this URL
     */
    //TODO: we need to specify how to handle the case where the URI is ambiguous (ie., no
    // custom protocol, no recognizable file extension, etc. - do you return true or false) to
    // support the case where the codec needs to see the actual stream. i.e., .tsv codecs can't
    // tell from extension and MUST resolve to a stream to determine if it can decode
    boolean canDecodeURI(final IOPath resource);

    /**
     * Decode the input stream. Read as many bytes as {@link #getSignatureProbeStreamSize} returns for
     * this codec.
     *
     * Codecs that can't consume a stream directly, such as codecs that handle a custom URI protocol
     * for which there is no installed NIO provider, should return false;
     *
     * @param probingInputStream
     * @param sourceName
     * @return true if this codec recognizes the stream signature, and can provide a decoder to decode the stream
     */
    //TODO: rename this to canDecodeStreamSignature ?
    boolean canDecodeSignature(final SignatureProbingInputStream probingInputStream, final String sourceName);

    /**
     * @return the number of bytes this codec requires to determine whether a stream contains the
     * correct signature/version.
     */
    //TODO: I don't think this actually needs to be part of the public API anymore, since only
    // getSignatureProbeStreamSize needs to be called from outside the codecs.
    // Or alternatively rename getSignatureProbeStreamSize to getSignatureSize ?
    int getSignatureSize();

    /**
     * @return the number of bytes this codec requires to determine whether it can decode a stream.
     * This number may differ from {@link #getSignatureSize()} if, for example, the file format
     * uses a block compressed format. BAM for example has a small {@link #getSignatureSize()}, but
     * requires a larger {@link #getSignatureProbeStreamSize()} size since it needs to decompress
     * a full compression block in order to examine the first few signature bytes signature.
     */
    //TODO: getSignatureProbeStreamSize should be expressed in "encrypted" space rather than "plaintext"
    // space. For example, a raw signature may be N bytes of ASCII, but the codec may need to
    // consume an entire encrypted GZIP block in order to inspect those N byes. getSignatureProbeStreamSize
    // should return the encryption block size.
    default int getSignatureProbeStreamSize() { return getSignatureSize(); }

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
