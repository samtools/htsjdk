package htsjdk.beta.plugin.interval;

/**
 * Query rule to determine whether a query should match overlapping or contained records
 */
public enum HtsQueryRule {
    OVERLAPPING,
    CONTAINED;
}
