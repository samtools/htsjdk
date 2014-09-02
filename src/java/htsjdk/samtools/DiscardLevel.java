package htsjdk.samtools;

/**
 * Which non-primary reads to discard when reading a SAM or BAM.
 */
public enum DiscardLevel {
    /**
     * Do not remove any non-primary reads.
     */
    NONE,
    /**
     * Remove reads marked as secondary.
     */
    SECONDARY,
    /**
     * Remove all non-primary reads, both secondary and supplementary.
     */
    ALL;
}
