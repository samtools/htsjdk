package htsjdk.index;

import java.io.Closeable;
import java.util.Optional;

/**
 * An index that answers one question: given a region, which byte ranges of the data file could hold
 * records overlapping it? This is the part of every coordinate index (BAI, CSI, CRAI, tabix) that
 * does not depend on the index format. Format-specific structure such as BAI's linear bins and
 * per-reference record counts stays on the format's own interface.
 *
 * <p>References are addressed by ordinal into the index's own reference ordering, because a BAI
 * file carries no sequence names at all. For BAI, CSI and CRAI that ordering is the sequence
 * dictionary's.
 */
public interface HtsQueryIndex extends Closeable {

    /**
     * The byte ranges that could hold records overlapping the region.
     *
     * @param referenceIndex ordinal of the reference in the index's own reference ordering
     * @param start 1-based inclusive start of the region
     * @param end 1-based inclusive end of the region
     * @return the byte ranges to read; empty if the region cannot match anything
     */
    HtsFileSpan getSpanOverlapping(int referenceIndex, int start, int end);

    /**
     * The byte ranges holding records that have no position at all. "Unplaced" rather than
     * "unmapped": an unmapped read placed beside its mapped mate is found by a region query, and
     * this method addresses the records that collect at the end of the file instead.
     *
     * @return the byte ranges; empty for a format with no such concept, such as a tabix-indexed
     *     VCF, and when the index has nothing to point at
     */
    default Optional<HtsFileSpan> getSpanOfUnplaced() {
        return Optional.empty();
    }

    /** Close the index and release anything it holds. */
    @Override
    void close();
}
