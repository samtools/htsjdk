package htsjdk.index;

/**
 * An ordered, possibly empty set of byte ranges in a data file, as returned by an index query.
 *
 * <p>How the ranges are addressed is defined by the data format, not by this interface: a BAM index
 * yields BGZF virtual offsets, a CRAM index container offsets. Callers pass the span back to the
 * reader that produced it rather than interpreting it themselves.
 *
 * @see HtsQueryIndex
 */
public interface HtsFileSpan {

    /**
     * Whether this span points at any data at all.
     *
     * @return true if there is nothing to read
     */
    boolean isEmpty();
}
