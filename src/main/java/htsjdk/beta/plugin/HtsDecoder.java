package htsjdk.beta.plugin;

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
     * Return a string for the file format supported by this decoder
     *
     * @return the file format supported by this decoder
     */
    String getFormat();

    /**
     * Return the version of the file format supported by this decoder.
     */
    HtsVersion getVersion();

    /**
     * return a user-friendly display name for this decoder
     *
     * @return a user-friendly display name for this decoder for use in error and warning messages
     */
    String getDisplayName();

    /**
     * return the file header for this decoder
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
