package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsRecord;
import htsjdk.samtools.util.CloseableIterator;

import java.util.Optional;

/**
 * Query methods specific to {@link ReadsDecoder}s.
 */
public interface ReadsQuery<R extends HtsRecord> {

    /**
     * Get an iterator of unmapped reads.
     */
    CloseableIterator<R> queryUnmapped();

    /**
     * Fetch the mate for the given read.
     */
    Optional<R> queryMate(R rec);
}
