package htsjdk.samtools;


/**
 * Interface implemented by filetering iterators used for BAM/CRAM readers.
 */
interface BAMIteratorFilter {
    public enum IntervalComparison {
        BEFORE, AFTER, OVERLAPPING, CONTAINED
    }

    /**
     * Type returned by BAMIteratorFilter that tell iterators implementing this interface
     * how to handle each SAMRecord.
     */
    public enum FilteringIteratorState {
        MATCHES_FILTER, STOP_ITERATION, CONTINUE_ITERATION
    }

    /**
     * Determine if given record passes the filter, and if it does not, whether iteration
     * should continue or if this record is beyond the region(s) of interest.
     */
    FilteringIteratorState compareToFilter(final SAMRecord record);
}

