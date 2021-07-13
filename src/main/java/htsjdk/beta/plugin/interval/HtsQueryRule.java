package htsjdk.beta.plugin.interval;

/**
 * Query rule values used to determine signal whether a query should match overlapping or contained records.
 * For use with {@link HtsQuery} methods.
 */
public enum HtsQueryRule {
    /**
     * Get all records that overlap the query interval.
     */
    OVERLAPPING,
    /**
     * Only get records that are entirely contained with the query interval.
     */
    CONTAINED;
}
