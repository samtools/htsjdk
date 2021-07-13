package htsjdk.beta.plugin;

import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.interval.HtsQuery;
import java.io.Closeable;

/**
 * Base interface for decoders.
 * <p>
 *      Implementations should not attempt to automatically resolve a companion index, and instead should
 *      only satisfy index queries when the provided input bundle explicitly specifies an index resource.
 * </p>
 *
 * @param <H> type param for the header for this format (i.e. SAMFileHeader)
 * @param <R> type param for the record for this format (i.e. SAMRecord)
 */
public interface HtsDecoder<H extends HtsHeader, R extends HtsRecord>
        extends HtsQuery<R>, Closeable {

    /**
     * Get the name of the file format supported by this decoder.The format name defines the underlying
     * format handled by this decoder, and also corresponds to the format of the primary bundle
     * resource that is required when decoding (see {@link htsjdk.beta.plugin.bundle.BundleResourceType}
     * and {@link BundleResource#getFileFormat()}).
     *
     * @return the name of the underlying file format handled by this decoder
     */
    String getFileFormat();

    /**
     * Get the version of the file format supported by this decoder.
     */
    HtsVersion getVersion();

    /**
     * Get a user-friendly display name for this decoder.
     *
     * @return a user-friendly display name for this decoder for use in error and warning messages
     */
    String getDisplayName();

    /**
     * Get the file header for this decoder.
     *
     * @return the file header for this decoder, of type {@code H}
     */
    H getHeader();

    /**
     * Close any resources associated with this decoder.
     */
    @Override
    void close();

}
