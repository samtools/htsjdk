package htsjdk.beta.plugin;

import java.io.Closeable;

/**
 * Base interface for encoders.
 *
 * @param <H> type param for the header for this format (i.e. SAMFileHeader)
 * @param <R> type param for the record for this format (i.e. SAMRecord)
 */
public interface HtsEncoder<H extends HtsHeader, R extends HtsRecord> extends Closeable {

    /**
     * Return the file format supported by this encoder
     *
     * @return the file format supported by the encoder
     */
    String getFormat();

    /**
     * Return the version of the file format supported by this encoder.
     */
    HtsVersion getVersion();

    /**
     * Return a user-friendly display name for this encoder
     *
     * @return a user-friendly display name for this encoder for use in error and warning messages
     */
    String getDisplayName();

    /**
     */
    /**
     * Set the file format header for this decoder, of type {@link H}
     *
     * @param header to use
     */
    void setHeader(H header);

    /**
     * Write a single record to the underlying output.
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
