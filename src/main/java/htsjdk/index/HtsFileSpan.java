package htsjdk.index;

/**
 * An ordered set of byte ranges in a data file, as returned by an index query. The addressing
 * scheme is the data format's (BGZF virtual offsets for BAM, container offsets for CRAM); pass the
 * span back to the reader that produced it.
 *
 * @see HtsQueryIndex
 */
public interface HtsFileSpan {

    /**
     * @return true if the span covers no data
     */
    boolean isEmpty();
}
