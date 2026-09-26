package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsRecord;
import htsjdk.samtools.util.CloseableIterator;
import java.util.Optional;

/**
 * Query methods specific to {@link ReadsDecoder}s.
 */
public interface ReadsQuery<R extends HtsRecord> {

    /**
     * Get an iterator of the unplaced reads, those with no reference sequence, which a coordinate-sorted file keeps
     * after every placed read. An unmapped read placed at its mate's position is not included; to see every read with
     * the unmapped flag, iterate over the whole file.
     */
    CloseableIterator<R> queryUnmapped();

    /**
     * Fetch the mate for the given read.
     *
     * @param record the source record
     * @return the source record's mate, or Optional.empty() if the source record has no mate
     */
    Optional<R> queryMate(R record);
}
