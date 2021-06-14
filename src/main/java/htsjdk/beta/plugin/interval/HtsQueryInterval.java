package htsjdk.beta.plugin.interval;

import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.Locatable;
import htsjdk.utils.ValidationUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

//TODO:
// Wild cards, i.e., end of reference/contig
// Should this have a separate interface ?

public class HtsQueryInterval implements HtsInterval {

    private final String queryName;
    private final long start;
    private final long end;

    public HtsQueryInterval(final String queryName, final long start, final long end){
        //validatePositions(contig, start, end);
        this.queryName = queryName;
        this.start = start;
        this.end = end;
    }

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

    public String getQueryName() { return queryName; }

    public long getStart() { return start; }

    public long getEnd() { return end; }

}
