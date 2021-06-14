package htsjdk.beta.plugin.interval;

import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.Locatable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// Interop methods for interconverting to/from existing htsjdk types such as Locatable/QueryInterval

public class HtsIntervalUtils {

    public static QueryInterval toQueryInterval(final HtsInterval interval, final SAMSequenceDictionary dictionary) {
        return new QueryInterval(
                dictionary.getSequenceIndex(interval.getQueryName()),
                toIntegerSafe(interval.getStart()),
                toIntegerSafe(interval.getEnd()));
    }

    public static Locatable toLocatable(final HtsInterval interval, final SAMSequenceDictionary dictionary) {
        return new Locatable() {
            @Override
            public String getContig() {
                return interval.getQueryName();
            }

            @Override
            public int getStart() {
                return toIntegerSafe(interval.getStart());
            }

            @Override
            public int getEnd() {
                return toIntegerSafe(interval.getEnd());
            }

            @Override
            public String toString() {

                return String.format("%s:%s-%s",
                        interval.getQueryName(),
                        interval.getStart(), interval.getEnd());
            }
        };
    }

    public static List<Locatable> toLocatableList(
            final List<HtsInterval> intervals,
            final SAMSequenceDictionary dictionary) {
        return intervals
                .stream()
                .map(si -> toLocatable(si, dictionary))
                .collect(Collectors.toList());
    }

    public static QueryInterval[] toQueryIntervalArray(
            final List<HtsInterval> intervals,
            final SAMSequenceDictionary dictionary) {
        return intervals
                .stream()
                .map(si -> toQueryInterval(si, dictionary))
                .collect(Collectors.toList()).toArray(new QueryInterval[intervals.size()]);
    }

    public static List<HtsInterval> fromQueryIntervalArray(
            final QueryInterval[] queryIntervals,
            final SAMSequenceDictionary dictionary) {
        return Arrays.stream(queryIntervals)
                .map(si -> new HtsQueryInterval(si, dictionary))
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
