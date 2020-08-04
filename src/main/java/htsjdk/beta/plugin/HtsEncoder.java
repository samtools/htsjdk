package htsjdk.beta.plugin;

import java.io.Closeable;

/**
 * Base interface for encoders.
 *
 * @param <F> enum representing the formats for this codec category
 *               (i.e., ReadsFormat defining SAM/BAM/CRAM constants)
 * @param <H> type param for the header for this format (i.e. SAMFileHeader)
 * @param <R> type param for the record for this format (i.e. SAMRecord)
 */
public interface HtsEncoder<F, H extends HtsHeader, R extends HtsRecord> extends Closeable {

    F getFormat();

    HtsCodecVersion getVersion();

    String getDisplayName();

    void setHeader(H header);

    void write(R record);

    void close();

}
