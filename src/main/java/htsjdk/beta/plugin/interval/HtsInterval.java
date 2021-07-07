package htsjdk.beta.plugin.interval;

/**
 * A common interface for 1-based closed genomic intervals.
 */
public interface HtsInterval {
    /**
     * @return the name part of this interval query
     */
    String getQueryName();

    /**
     * @return the 1-based inclusive start coordinate for this interval
     */
    long getStart();

    /**
     * @return the inclusive end coordinate for this interval
     */
    long getEnd();
}
