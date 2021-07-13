package htsjdk.beta.plugin.interval;

import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.utils.ValidationUtils;

//TODO: wild cards 0, +, end of reference/contig

/**
 * An concrete query interval implementation of {@link HtsInterval} used for random access queries on
 * file formats represented by {@link htsjdk.beta.plugin.HtsDecoder}s that support random access.
 */
public class HtsQueryInterval implements HtsInterval {

    private final String queryName;
    private final long start;
    private final long end;

    /**
     * Create an HtsQueryInterval from query components
     * @param queryName the string query nae
     * @param start the integer start position
     * @param end the end position
     */
    public HtsQueryInterval(final String queryName, final long start, final long end){
        //validatePositions(contig, start, end);
        this.queryName = queryName;
        this.start = start;
        this.end = end;
    }

    /**
     * Convenience method for creating a query interval from an old-style {@link QueryInterval}.
     *
     * @param queryInterval the query interval to convert
     * @param dictionary the sequence dictionary to use to do the conversion
     */
    public HtsQueryInterval(final QueryInterval queryInterval, final SAMSequenceDictionary dictionary) {
        ValidationUtils.nonNull(dictionary, "a valid sequence dictionary is required");
        ValidationUtils.nonNull(dictionary.getSequence(queryInterval.referenceIndex),
                String.format("query index %d is not present in the provided dictionary", queryInterval.referenceIndex));
        ValidationUtils.nonNull(dictionary.getSequence(queryInterval.referenceIndex).getContig(),
                String.format("contig name for index %d is not present in the provided dictionary", queryInterval.referenceIndex));
        this.queryName = dictionary.getSequence(queryInterval.referenceIndex).getContig();
        this.start = queryInterval.start;
        this.end = queryInterval.end;
    }

    @Override
    public String getQueryName() { return queryName; }

    @Override
    public long getStart() { return start; }

    @Override
    public long getEnd() { return end; }

}
