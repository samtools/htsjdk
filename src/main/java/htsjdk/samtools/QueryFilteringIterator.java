package htsjdk.samtools;

import htsjdk.samtools.util.CloseableIterator;
import java.util.NoSuchElementException;

/**
 * Narrows the records an index points a query at, which are all those of the chunks that may hold a match, to the
 * ones that do match: a {@link BAMIteratorFilter} says of each record whether to return it, pass over it, or stop.
 */
final class QueryFilteringIterator implements CloseableIterator<SAMRecord> {
    private final CloseableIterator<SAMRecord> records;
    private final BAMIteratorFilter filter;
    private SAMRecord next;

    /**
     * @param records the records of the chunks to search, in file order; closed along with this iterator
     */
    QueryFilteringIterator(final CloseableIterator<SAMRecord> records, final BAMIteratorFilter filter) {
        this.records = records;
        this.filter = filter;
        next = advance();
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public SAMRecord next() {
        if (next == null) {
            throw new NoSuchElementException();
        }
        final SAMRecord current = next;
        next = advance();
        return current;
    }

    private SAMRecord advance() {
        while (records.hasNext()) {
            final SAMRecord record = records.next();
            switch (filter.compareToFilter(record)) {
                case MATCHES_FILTER:
                    return record;
                case STOP_ITERATION:
                    return null;
                case CONTINUE_ITERATION:
                    break;
                default:
                    throw new SAMException("Unexpected return from compareToFilter");
            }
        }
        return null;
    }

    @Override
    public void close() {
        records.close();
    }
}
