package htsjdk.beta.plugin.interval;

import htsjdk.utils.ValidationUtils;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

// TODO:
// - wild cards, i.e., end of reference/contig
// - why does SamReader have query(..., contained) AND queryContained ?
// - remove the default implementations once all the decoders implement these
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

    Iterator<RECORD> iterator();

    //*******************************************
    // Start temporary common query interface default implementations.

    default boolean isQueryable() { throw new IllegalStateException("Not implemented"); }

    default boolean hasIndex() { throw new IllegalStateException("Not implemented"); }

    // TODO: include this, for rsID for variants ? readname for reads?
    default Iterator<RECORD> query(final String queryString) {
        ValidationUtils.validateArg(isQueryable(), "Reader is not queryable");
        throw new IllegalStateException("Not implemented");
    }

    default Iterator<RECORD> query(final String queryName, final long start, final long end, final HtsQueryRule queryRule) {
        return query(new HtsInterval(queryName, start, end), queryRule);
    }

    default Iterator<RECORD> queryOverlapping(final String queryName, final long start, final long end) {
        return queryOverlapping(new HtsInterval(queryName, start, end));
    }

    default Iterator<RECORD> queryContained(final String queryName, final long start, final long end) {
        return queryContained(new HtsInterval(queryName, start, end));
    }

    default Iterator<RECORD> query(final HtsInterval interval, final HtsQueryRule queryRule) {
        return query(Collections.singletonList(interval), queryRule);
    }

    default Iterator<RECORD> queryOverlapping(final HtsInterval interval) {
        return query(interval, HtsQueryRule.OVERLAPPING);
    }

    default Iterator<RECORD> queryContained(final HtsInterval interval) {
        return queryContained(Collections.singletonList(interval));
    }

    default Iterator<RECORD> query(final List<HtsInterval> intervals, final HtsQueryRule queryRule) {
        ValidationUtils.validateArg(isQueryable(), "Reader is not queryable");
        throw new IllegalStateException("Not implemented"); }

    default Iterator<RECORD> queryOverlapping(final List<HtsInterval> intervals) {
        return query(intervals, HtsQueryRule.OVERLAPPING);
    }

    default Iterator<RECORD> queryContained(final List<HtsInterval> intervals) {
        return query(intervals, HtsQueryRule.CONTAINED);
    }

    //TODO: match reads that have this start; we could just use an HtsInterval with span==1
    default Iterator<RECORD> queryStart(final String queryName, final long start) {
        ValidationUtils.validateArg(isQueryable(), "Reader is not queryable");
        throw new IllegalStateException("Not implemented");
    }

}
