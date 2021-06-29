package htsjdk.beta.plugin;

import java.io.Closeable;

/**
 * Base interface for encoders.
 *
 * @param <F> an {@link HtsFormat} enum representing the formats for this codec category
 *               (i.e., ReadsFormat defining SAM/BAM/CRAM constants)
 * @param <H> type param for the header for this format (i.e. SAMFileHeader)
 * @param <R> type param for the record for this format (i.e. SAMRecord)
 */
public interface HtsEncoder<F extends Enum<F> & HtsFormat<F>, H extends HtsHeader, R extends HtsRecord> extends Closeable {

    /**
     * Return the file format supported by this encoder, from the enum {@code F}
     * @return the file format supported by the encoder, from {@code F}
     */
    F getFormat();

    /**
     * Return the version of the file format supported by this encoder.
     */
    HtsVersion getVersion();

    /**
     * @return a user-friendly display name for this encoder for use in error and warning messages
     */
    String getDisplayName();

    /**
     */
    /**
     * set the file format header for this decoder, of type {@link H}
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
