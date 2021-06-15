package htsjdk.beta.plugin;

import htsjdk.beta.plugin.interval.HtsQuery;

import java.io.Closeable;

/**
 * Base interface for decoders.
 *
 * @param <F> enum representing the formats for this codec category
 *               (i.e., ReadsFormat defining SAM/BAM/CRAM constants)
 * @param <H> type param for the header for this format (i.e. SAMFileHeader)
 * @param <R> type param for the record for this format (i.e. SAMRecord)
 */
public interface HtsDecoder<F extends Enum<F>, H extends HtsHeader, R extends HtsRecord>
        extends HtsQuery<R>, Closeable {

    F getFormat();

    HtsVersion getVersion();

    String getDisplayName();

    H getHeader();

    void close();
}
