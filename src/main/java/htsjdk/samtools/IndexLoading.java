package htsjdk.samtools;

/**
 * When a BAI or CSI index is read from its file. Whichever is chosen, what has been read is held only as long as
 * there is memory for it, and read again from the file if it is wanted after the collector has reclaimed it.
 */
public enum IndexLoading {
    /**
     * {@link #EAGER} for an index that is given as a stream, that is not on the default file system, or that is
     * smaller than a megabyte; {@link #LAZY} otherwise.
     */
    AUTO,
    /** Read the whole index in one pass when it is first needed. Suits a file that is slow to seek in. */
    EAGER,
    /** Read each reference sequence's part of the index when a query first needs it. */
    LAZY
}
