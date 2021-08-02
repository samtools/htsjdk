package htsjdk.beta.plugin.interval;

import htsjdk.beta.exception.HtsjdkUnsupportedOperationException;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.utils.ValidationUtils;

import java.util.Collections;
import java.util.List;

/**
 * Common query interface for {@link htsjdk.beta.plugin.HtsDecoder}s
 *
 * @param <RECORD> the type of records/iterators returned by queries
 */
public interface HtsQuery<RECORD> extends Iterable<RECORD> {

    /**
     * Get an iterator of all records in the underlying resource.
     *
     * @return an iterator of all records in the underlying resource
     */
    @Override
    CloseableIterator<RECORD> iterator();

    //*******************************************
    // Start temporary common query interface default implementations.

    /**
     * Gte true if the underlying resource is queryable.
     *
     * @return true if the underlying resource is queryable. this may be true even if the underlying
     * resource returns false for {@link #hasIndex()}
     */
    boolean isQueryable();

    /**
     * Return true if the underlying resource has an index.
     *
     * @return true if the underlying resource has an index
     */
    boolean hasIndex();

    // NOTE: this method is retained for all decoders to support examples such as rsID for variants,
    // readname for reads, etc.
    /**
     * Get an iterator over all records from the underlying resource that match the query string
     *
     * @param queryString decoder specific query string
     * @return an iterator over all records from the underlying resource that match the query string
     */
    default CloseableIterator<RECORD> query(final String queryString) {
        ValidationUtils.validateArg(isQueryable(), "Decoder is not queryable");
        throw new HtsjdkUnsupportedOperationException("query(string) not implemented for this decoder");
    }

    /**
     * Get an iterator over all records from the underlying resource that match the query arguments
     *
     * @param queryName name to match
     * @param start start of query interval
     * @param end end of query interval
     * @param queryRule query rule value to use (from {@link HtsQueryRule}
     * @return an iterator over all records from the underlying resource that match the query arguments
     */
    default CloseableIterator<RECORD> query(
            final String queryName,
            final long start,
            final long end,
            final HtsQueryRule queryRule) {
        return query(new HtsQueryInterval(queryName, start, end), queryRule);
    }

    /**
     * Get an iterator over all records from the underlying resource that match the query arguments
     *
     * @param queryName name to match
     * @param start start of query interval
     * @param end end of query interval
     * @return an iterator over all records from the underlying resource that match the query arguments
     */
    default CloseableIterator<RECORD> queryOverlapping(final String queryName, final long start, final long end) {
        return queryOverlapping(new HtsQueryInterval(queryName, start, end));
    }

    /**
     * Gte an iterator over all records from the underlying resource that match the query arguments
     *
     * @param queryName name to match
     * @param start start of query interval
     * @param end end of query interval
     * @return an iterator over all records from the underlying resource that match the query arguments
     */
    default CloseableIterator<RECORD> queryContained(final String queryName, final long start, final long end) {
        return queryContained(new HtsQueryInterval(queryName, start, end));
    }

    /**
     * Get an iterator over all records from the underlying resource that match the query arguments
     *
     * @param interval interval to match
     * @param queryRule query rule to use, from {@link HtsQueryRule}
     * @return an iterator over all records from the underlying resource that match the query arguments
     */
    default CloseableIterator<RECORD> query(final HtsInterval interval, final HtsQueryRule queryRule) {
        return query(Collections.singletonList(interval), queryRule);
    }

    /**
     * Get an iterator over all records from the underlying resource that overlap the query interval
     *
     * @param interval interval to match
     * @return an iterator over all records from the underlying resource that overlap the query interval
     */
    default CloseableIterator<RECORD> queryOverlapping(final HtsInterval interval) {
        return query(interval, HtsQueryRule.OVERLAPPING);
    }

    /**
     * Get an iterator over all records from the underlying resource that are contained within
     * the query interval
     *
     * @param interval interval to match
     * @return an iterator over all records from the underlying resource that are contained within
     * the query interval
     */
    default CloseableIterator<RECORD> queryContained(final HtsInterval interval) {
        return queryContained(Collections.singletonList(interval));
    }

    /**
     * Get an iterator over all records from the underlying resource that match the query arguments.
     * Callers much ensure that the intervals are in increasing order and do not overlap or abut.
     *
     * @param intervals list of intervals to match
     * @param queryRule query rule to use, from {@link HtsQueryRule}
     * @return an iterator over all records from the underlying resource that match the query arguments
     */
    default CloseableIterator<RECORD> query(final List<HtsInterval> intervals, final HtsQueryRule queryRule) {
        ValidationUtils.validateArg(isQueryable(), "Decoder is not queryable");
        throw new HtsjdkUnsupportedOperationException("query(intervals, queryRule) not implemented for this decoder");
    }

    /**
     * Get an iterator over all records from the underlying resource that overlap the query intervals
     *
     * @param intervals list of intervals to use
     * @return an iterator over all records from the underlying resource that overlap the query intervals
     */
    default CloseableIterator<RECORD> queryOverlapping(final List<HtsInterval> intervals) {
        return query(intervals, HtsQueryRule.OVERLAPPING);
    }

    /**
     * Get an iterator over all records from the underlying resource that are contained within the query intervals
     *
     * @param intervals list of intervals to use
     * @return an iterator over all records from the underlying resource that are contained within the query intervals
     */
    default CloseableIterator<RECORD> queryContained(final List<HtsInterval> intervals) {
        return query(intervals, HtsQueryRule.CONTAINED);
    }

    //TODO: match reads that have this start; we *could* just use an HtsInterval with span==1 ? do we need this ?
    /**
     * Get an iterator over all records from the underlying resource that overlap the start position
     *
     * @param queryName name to match
     * @param start start position to overlap
     * @return an iterator over all records from the underlying resource that overlap the start position
     */
    default CloseableIterator<RECORD> queryStart(final String queryName, final long start) {
        ValidationUtils.validateArg(isQueryable(), "Decoder is not queryable");
        throw new HtsjdkUnsupportedOperationException("queryStart not implemented for this decoder");
    }

}
