package htsjdk.beta.plugin.interval;

import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.Locatable;
import htsjdk.utils.ValidationUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Methods for interconverting between HtsQueryInterval and existing htsjdk types such as Locatable/QueryInterval
 */
public class HtsIntervalUtils {

    /**
     * Convert an HtsInterval to a QueryInterval
     *
     * @param interval {@link HtsInterval} to convert
     * @param dictionary sequence dictionary to use to convert string query names to contig index
     * @return a QueryInterval equivalent to {@code interval}
     */
    public static QueryInterval toQueryInterval(final HtsInterval interval, final SAMSequenceDictionary dictionary) {
        return new QueryInterval(
                dictionary.getSequenceIndex(interval.getQueryName()),
                toIntegerSafe(interval.getStart()),
                toIntegerSafe(interval.getEnd()));
    }

    /**
     * Convert an HtsInterval to a {@link Locatable}
     *
     * @param interval {@link HtsInterval} to convert
     * @return a Locatable equivalent to {@code interval}
     */
    public static Locatable toLocatable(final HtsInterval interval) {
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

    /**
     * Convert a List of {@link HtsInterval} to a list of {@link Locatable}.
     *
     * @param intervals list of {@link HtsInterval}s to convert
     * @return a list of {@link Locatable}
     */
    public static List<Locatable> toLocatableList(final List<HtsInterval> intervals) {
        ValidationUtils.nonNull(intervals, "interval list");
        return intervals
                .stream()
                .map(si -> toLocatable(si))
                .collect(Collectors.toList());
    }

    /**
     * Convert a list of {@link HtsInterval} to an array of {@link QueryInterval}
     *
     * @param intervals list of {@link HtsInterval}s to convert
     * @param dictionary sequence dictionary to use
     * @return array of {@link QueryInterval}
     */
    public static QueryInterval[] toQueryIntervalArray(
            final List<HtsInterval> intervals,
            final SAMSequenceDictionary dictionary) {
        ValidationUtils.nonNull(intervals, "interval list");
        ValidationUtils.nonNull(dictionary, "SAMSequenceDictionary");
        return intervals
                .stream()
                .map(si -> toQueryInterval(si, dictionary))
                .collect(Collectors.toList()).toArray(new QueryInterval[intervals.size()]);
    }

    /**
     *
     * @param queryIntervals list of {@link QueryInterval} to convert
     * @param dictionary seuence dictionary to use for the conversion
     * @return list of {@link HtsInterval}
     */
    public static List<HtsInterval> fromQueryIntervalArray(
            final QueryInterval[] queryIntervals,
            final SAMSequenceDictionary dictionary) {
        return Arrays.stream(queryIntervals)
                .map(si -> new HtsQueryInterval(si, dictionary))
                .collect(Collectors.toList());
    }

    /**
     * Convert a long coordinate to an integer, for use with interconverting between old style integer
     * coordinates and new style long coordinates. Throws on overflow.
     *
     * @param coord long coordinate to convert
     * @return an integer representation of {@code coord} if the conversion is safe
     * @throws IllegalArgumentException if converting results in overflow
     */
    public static int toIntegerSafe(final long coord) {
        try {
            return Math.toIntExact(coord);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(String.format("long to int conversion of %ld results in integer overflow", coord), e);
        }
    }
}
