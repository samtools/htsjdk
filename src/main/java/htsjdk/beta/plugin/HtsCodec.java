package htsjdk.beta.plugin;

import htsjdk.beta.plugin.registry.SignatureProbingInputStream;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.bundle.Bundle;

/**
 * Base interface implemented by all HtsCodec implementations.
 *
 * A codec is a handler for a specific combination of file-format, version, possibly specific
 * to some URI protocol. For example, a BAM codec handles the BAM format for the "file://" protocol, or other nio.Path
 * file system providers such as gs:// or hdfs://, and an HtsGet codec handles BAM via "http://" or "htsget://" protocol.
 *
 * Codecs may delegate to/reuse shared encoder/decoder implementations.
 *
 * @param <F> an enum representing the various serialized formats handled by this codec
 * @param <D> decoder options for this codec
 * @param <E> encoder options for this codec
 */
// i.e., Htsget codec can't assume it can call toPath on http: paths, .tsv codecs can't tell from extension
// and MUST resolve to a stream
public interface HtsCodec<
        F extends Enum<F>,
        D extends HtsDecoderOptions,
        E extends HtsEncoderOptions>
        extends Upgradeable {

    /**
     * This value represents the set of interfaces exposed by this codec. The underlying file format
     * may vary amongst different codecs that have the same codec type, since the serialized file format
     * may be different (i.e., both the BAM and HTSGET_BAM codecs have "codec type" ALIGNED_READS because
     * they render records as SAMRecord, but underlying file formats are different.
     * @return
     */
    HtsCodecType getCodecType();

    /**
     * Return a value representing the underlying file format handled by this codec (i.e., for codec type
     * ALIGNED_READS, the values may be from BAM, CRAM, SAM).
     * @return
     */
    F getFileFormat();

    HtsCodecVersion getVersion();

    default String getDisplayName() {
        return String.format("Codec %s for %s version %s", getFileFormat(), getVersion(), getClass().getName());
    }

    // Get the number of bytes this codec requires to determine whether it can decode a stream.
    //TODO: Note that this value is in "plaintext" space. If the input were encrypted, the number of bytes
    // required to probe the input may be greater than this number.
    default int getSignatureProbeStreamSize() { return getSignatureSize(); }

    // Get the number of bytes this codec requires to determine whether a stream contains the correct signature/version.
    int getSignatureSize();

    // Return true if this codec claims to handle the URI protocol scheme (and file extension) for this
    // IOPath. Codecs should only return true if the supported protocol scheme requires special handling that
    // precludes use of an NIO file system provider.
    //
    // During codec resolution, ff any registered codec returns true for this URI, the signature probing
    // protocol will:
    //
    // 1) immediately prune the list of candidate codecs to only those that return true for this method
    // 2) not attempt to obtain an "InputStream" on the IOPath, on the assumption that custom protocol
    //    schemes must perform special handling to access the underlying scheme (i.e., htsget codec would
    //    claim an "http:" uri if the rest of the URI conforms to the expected format for that codec's
    //    protocol.
    //
    default boolean claimURI(final IOPath resource) { return false; }

    // Most implementations only look at the extension. Protocol scheme is excluded because
    // codecs don't opt-out based on protocol scheme, only opt-in (via claimCustomURI).
    boolean canDecodeURI(final IOPath resource);

    // Decode the input stream. Read as many bytes as getMinimumStreamDecodeSize() returns for this codec.
    // Never more than that. Don't close it. Don't mark it. Don't reset it. Ever.
    boolean canDecodeSignature(final SignatureProbingInputStream probingInputStream, final String sourceName);

    HtsDecoder<F, ?, ? extends HtsRecord> getDecoder(final Bundle inputBundle, final D decodeOptions);

    HtsEncoder<F, ?, ? extends HtsRecord> getEncoder(final Bundle outputBundle, final E encodeOptions);

}
