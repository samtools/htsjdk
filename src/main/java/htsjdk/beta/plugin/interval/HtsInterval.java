package htsjdk.beta.plugin.interval;

/**
 * A common interface for 1-based, closed genomic intervals.
 *
 * This is an evolution of {@link htsjdk.samtools.util.Locatable} that can be used to support long genomic coordinates.
 */
public interface HtsInterval {
    /**
     * Get the name part of this interval query.
     *
     * @return the name part of this interval query
     */
    String getQueryName();

    /**
     * Get the 1-based inclusive start coordinate for this interval.
     *
     * @return the 1-based inclusive start coordinate for this interval
     */
    long getStart();

    /**
     * Get the inclusive end coordinate for this interval.
     *
     * @return the inclusive end coordinate for this interval
     */
    long getEnd();
}
