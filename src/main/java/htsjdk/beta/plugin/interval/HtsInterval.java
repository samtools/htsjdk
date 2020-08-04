package htsjdk.beta.plugin.interval;

// TODO:
// - Wild cards, i.e., end of reference/contig
// Should this have a separate interface ?

import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.Locatable;
import htsjdk.utils.ValidationUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HtsInterval {

    private final String queryName;
    private final long start;
    private final long end;

    public HtsInterval(final String queryName, final long start, final long end){
        //validatePositions(contig, start, end);
        this.queryName = queryName;
        this.start = start;
        this.end = end;
    }

    public HtsInterval(final QueryInterval queryInterval, final SAMSequenceDictionary dictionary) {
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

    // Interop methods for interconverting to/from existing htsjdk types such as Locatable/QueryInterval

    public QueryInterval toQueryInterval(final SAMSequenceDictionary dictionary) {
        return new QueryInterval(
                dictionary.getSequenceIndex(getQueryName()),
                toIntegerSafe(getStart()),
                toIntegerSafe(getEnd()));
    }

    public Locatable toLocatable(final SAMSequenceDictionary dictionary) {
        return new Locatable() {
            @Override
            public String getContig() {
                return getQueryName();
            }

            @Override
            public int getStart() {
                return toIntegerSafe(start);
            }

            @Override
            public int getEnd() {
                return toIntegerSafe(end);
            }

            @Override
            public String toString() {
                return String.format("%s:%s-%s", getQueryName(), start, end);
            }
        };
    }

    public static List<Locatable> toLocatableList(
            final List<HtsInterval> intervals,
            final SAMSequenceDictionary dictionary) {
        return intervals
                .stream()
                .map(si -> si.toLocatable(dictionary))
                .collect(Collectors.toList());
    }

    public static QueryInterval[] toQueryIntervalArray(
            final List<HtsInterval> intervals,
            final SAMSequenceDictionary dictionary) {
        return intervals
                .stream()
                .map(si -> si.toQueryInterval(dictionary))
                .collect(Collectors.toList()).toArray(new QueryInterval[intervals.size()]);
    }

    public static List<HtsInterval> fromQueryIntervalArray(
            final QueryInterval[] queryIntervals,
            final SAMSequenceDictionary dictionary) {
        return Arrays.stream(queryIntervals)
                .map(si -> new HtsInterval(si, dictionary))
                .collect(Collectors.toList());
    }

    public static int toIntegerSafe(final long coord) {
        try {
            return Math.toIntExact(coord);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(String.format("long to int conversion of %d results in integer overflow", coord), e);
        }
    }
}
