package htsjdk.beta.plugin.interval;

import htsjdk.exception.HtsjdkPluginException;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.utils.ValidationUtils;

import java.util.Collections;
import java.util.List;

//TODO: wild cards, i.e., end of reference/contig
//TODO: why does SamReader have query(..., contained) AND queryContained ?
//TODO: remove the default implementations once all the decoders implement these
//        switch (queryRule) {
//            case CONTAINED: return queryContained(queryName, start, end);
//            case OVERLAPPING: return queryOverlapping(queryName, start, end);
//            default: throw new IllegalArgumentException(String.format("Unknown query rule: %s", queryRule));
//        }

/**
 * Common query interface for decoders
 *
 * @param <RECORD> they type of records/iterators returned by queries
 */
public interface HtsQuery<RECORD> extends Iterable<RECORD> {

    @Override
    CloseableIterator<RECORD> iterator();

    //*******************************************
    // Start temporary common query interface default implementations.

    boolean isQueryable();

    boolean hasIndex();

    // retained for usage such as rsID for variants, readname for reads,...
    default CloseableIterator<RECORD> query(final String queryString) {
        ValidationUtils.validateArg(isQueryable(), "Reader is not queryable");
        throw new HtsjdkPluginException("query not implemented for this decoder");
    }

    default CloseableIterator<RECORD> query(final String queryName, final long start, final long end, final HtsQueryRule queryRule) {
        return query(new HtsQueryInterval(queryName, start, end), queryRule);
    }

    default CloseableIterator<RECORD> queryOverlapping(final String queryName, final long start, final long end) {
        return queryOverlapping(new HtsQueryInterval(queryName, start, end));
    }

    default CloseableIterator<RECORD> queryContained(final String queryName, final long start, final long end) {
        return queryContained(new HtsQueryInterval(queryName, start, end));
    }

    default CloseableIterator<RECORD> query(final HtsInterval interval, final HtsQueryRule queryRule) {
        return query(Collections.singletonList(interval), queryRule);
    }

    default CloseableIterator<RECORD> queryOverlapping(final HtsInterval interval) {
        return query(interval, HtsQueryRule.OVERLAPPING);
    }

    default CloseableIterator<RECORD> queryContained(final HtsInterval interval) {
        return queryContained(Collections.singletonList(interval));
    }

    default CloseableIterator<RECORD> query(final List<HtsInterval> intervals, final HtsQueryRule queryRule) {
        ValidationUtils.validateArg(isQueryable(), "Reader is not queryable");
        throw new HtsjdkPluginException("query not implemented for this decoder"); }

    default CloseableIterator<RECORD> queryOverlapping(final List<HtsInterval> intervals) {
        return query(intervals, HtsQueryRule.OVERLAPPING);
    }

    default CloseableIterator<RECORD> queryContained(final List<HtsInterval> intervals) {
        return query(intervals, HtsQueryRule.CONTAINED);
    }

    //TODO: match reads that have this start; we could just use an HtsInterval with span==1
    default CloseableIterator<RECORD> queryStart(final String queryName, final long start) {
        ValidationUtils.validateArg(isQueryable(), "Reader is not queryable");
        throw new HtsjdkPluginException("queryStart not implemented for this decoder");
    }

}
