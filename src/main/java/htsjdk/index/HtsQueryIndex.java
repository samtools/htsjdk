package htsjdk.index;

import java.io.Closeable;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * An index that maps genomic regions to candidate byte ranges. Applicable to all index formats.
 *
 * <p>References are addressed by ordinal, since a BAI carries no sequence names. For BAI, CSI and
 * CRAI the ordinal is the sequence dictionary index.
 */
public interface HtsQueryIndex extends Closeable {

    /**
     * Byte ranges that may hold records overlapping the region.
     *
     * @param referenceIndex ordinal of the reference in the index's reference ordering
     * @param start 1-based inclusive start
     * @param end 1-based inclusive end
     * @return the byte ranges; empty if nothing can match
     */
    HtsFileSpan getSpanOverlapping(int referenceIndex, int start, int end);

    /**
     * Byte ranges holding records with no position (unmapped and unplaced), which sort to the end
     * of an alignment file.
     *
     * @return the byte ranges; empty if the format has no such concept or the index has none to
     *     point at
     */
    default Optional<HtsFileSpan> getSpanOfUnplaced() {
        return Optional.empty();
    }

    /**
     * The number of records with no position (unmapped and unplaced), which {@link #getSpanOfUnplaced()} covers.
     *
     * @return the count; empty if the format or this index does not record it
     */
    default OptionalLong getUnplacedRecordCount() {
        return OptionalLong.empty();
    }

    /** Closes the index. */
    @Override
    void close();
}
