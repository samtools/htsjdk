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
     * This value defines the set of interfaces exposed by this codec. The underlying file format
     * may vary amongst different codecs that have the same codec type, since the serialized file
     * format may be different (i.e., both the BAM and HTSGET_BAM codecs have codec type "ALIGNED_READS"
     * because they both render records as SAMRecord, even though underlying file formats are different.
     * @return
     */
    HtsCodecType getCodecType();

    /**
     * @return a value representing the underlying file format handled by this codec (i.e., for codec type
     * ALIGNED_READS, the values may be from BAM, CRAM, SAM).
     */
    F getFileFormat();

    /**
     * @return the file format version ({@link HtsCodecVersion}) supported by this codec
     */
    HtsCodecVersion getVersion();

    /**
     * @return a user-friendly informational display name for a given instance of this codec
     */
    default String getDisplayName() {
        return String.format("Codec %s for %s version %s", getFileFormat(), getVersion(), getClass().getName());
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
    default boolean claimURI(final IOPath resource) { return false; }

    /**
     * Most implementations only look at the extension. Protocol scheme is excluded because
     * codecs don't opt-out based on protocol scheme, only opt-in (via claimCustomURI).
     * @param resource to be decoded
     * @return true if the codec can provide a decoder to provide this URL
     */
    //TODO: we need to specify how to handle the case where the URI is ambiguous (ie., no
    // custom protocol, no recognizable file extension, etc. - do you return true or false) to
    // support the case where thecodec needs to see the actual stream. i.e., .tsv codecs can't
    // tell from extension and MUST resolve to a stream to determine if it can decode
    boolean canDecodeURI(final IOPath resource);

    /**
     * Decode the input stream. Read as many bytes as {@link #getSignatureProbeStreamSize} returns for
     * this codec.
     *
     * @param probingInputStream
     * @param sourceName
     * @return
     */
    boolean canDecodeSignature(final SignatureProbingInputStream probingInputStream, final String sourceName);

    /**
     * @return the number of bytes this codec requires to determine whether a stream contains the
     * correct signature/version.
     */
    int getSignatureSize();

    /**
     * @return the number of bytes this codec requires to determine whether it can decode a stream.
     * This number may differ from {@link #getSignatureSize()} if, for example, the file format
     * uses a block compressed format. BAM for example has a small {@link #getSignatureSize()}, but
     * requires a larger {@link #getSignatureProbeStreamSize()} size since it needs to decompress
     * a full compression block in order to examine the first few signature bytes signature.
     */
    //TODO: Note that this value is in "plaintext" space. If the input were encrypted, the number of
    // bytes required to probe the input may be greater than this number.
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
