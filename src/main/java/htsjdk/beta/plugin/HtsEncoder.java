package htsjdk.beta.plugin;

import htsjdk.beta.io.bundle.BundleResource;
import htsjdk.beta.io.bundle.BundleResourceType;

import java.io.Closeable;

/**
 * Base interface for encoders.
 *
 * @param <H> type param for the header for this encoder's format (i.e. SAMFileHeader)
 * @param <R> type param for the record for this encoder's format (i.e. SAMRecord)
 */
public interface HtsEncoder<H extends HtsHeader, R extends HtsRecord> extends Closeable {

    /**
     * Get the name of the file format supported by this encoder. The format name defines the underlying
     * format handled by this encoder, and also corresponds to the format of the primary bundle
     * resource that is required when encoding (see {@link BundleResourceType}
     * and {@link BundleResource#getFileFormat()}).
     *
     * @return the name of the underlying file format handled by this encoder
     */
    String getFileFormat();

    /**
     * Get the version of the file format supported by this encoder.
     */
    HtsVersion getVersion();

    /**
     * Get a user-friendly display name for this encoder.
     *
     * @return a user-friendly display name for this encoder for use in error and warning messages
     */
    String getDisplayName();

    /**
     * Set the file format header for this decoder, of type {@link H}. {@link #setHeader(HtsHeader)} must be
     * called before {@link #write(HtsRecord)} can be called.
     *
     * @param header to use
     */
    void setHeader(H header);

    /**
     * Write a single record to the underlying output. {@link #write(HtsRecord)} may only called after
     * {@link #setHeader(HtsHeader)} has been called.
     *
     * @param record record to write
     */
    void write(R record);

    /**
     * Close any resources associated with this decoder.
     */
    @Override
    void close();

}
