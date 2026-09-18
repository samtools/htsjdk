package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.BAMIteratorFilter.FilteringIteratorState;
import htsjdk.samtools.util.CloseableIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class QueryFilteringIteratorTest extends HtsjdkTest {
    /** Records starting at 100, 200, ... 500. */
    private static List<SAMRecord> records() {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        for (int start = 100; start <= 500; start += 100) {
            builder.addFrag("read" + start, 0, start, false);
        }
        final List<SAMRecord> records = new ArrayList<>();
        builder.iterator().forEachRemaining(records::add);
        return records;
    }

    private static final class ListIterator implements CloseableIterator<SAMRecord> {
        private final Iterator<SAMRecord> records;
        int served;
        boolean closed;

        ListIterator(final List<SAMRecord> records) {
            this.records = records.iterator();
        }

        @Override
        public boolean hasNext() {
            return records.hasNext();
        }

        @Override
        public SAMRecord next() {
            served++;
            return records.next();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static List<Integer> starts(final QueryFilteringIterator records) {
        final List<Integer> starts = new ArrayList<>();
        records.forEachRemaining(record -> starts.add(record.getAlignmentStart()));
        return starts;
    }

    @Test
    public void testRecordsTheFilterPassesOverAreLeftOut() {
        final QueryFilteringIterator matching = new QueryFilteringIterator(
                new ListIterator(records()),
                record -> record.getAlignmentStart() % 200 == 0
                        ? FilteringIteratorState.MATCHES_FILTER
                        : FilteringIteratorState.CONTINUE_ITERATION);
        Assert.assertEquals(starts(matching), List.of(200, 400));
    }

    @Test
    public void testNothingIsReadPastTheRecordTheFilterStopsAt() {
        final ListIterator all = new ListIterator(records());
        final QueryFilteringIterator matching = new QueryFilteringIterator(
                all,
                record -> record.getAlignmentStart() < 300
                        ? FilteringIteratorState.MATCHES_FILTER
                        : FilteringIteratorState.STOP_ITERATION);
        Assert.assertEquals(starts(matching), List.of(100, 200));
        Assert.assertEquals(all.served, 3);
        Assert.assertFalse(matching.hasNext());
    }

    @Test
    public void testClosingClosesTheRecordsBeneath() {
        final ListIterator all = new ListIterator(records());
        new QueryFilteringIterator(all, record -> FilteringIteratorState.MATCHES_FILTER).close();
        Assert.assertTrue(all.closed);
    }

    @Test(expectedExceptions = java.util.NoSuchElementException.class)
    public void testNextPastTheLastMatchIsRefused() {
        final QueryFilteringIterator none = new QueryFilteringIterator(
                new ListIterator(records()), record -> FilteringIteratorState.STOP_ITERATION);
        none.next();
    }

    @Test
    public void testRecordsBeneathAreClosedWhenTheFirstMatchCannotBeFound() {
        final ListIterator all = new ListIterator(records());
        Assert.assertThrows(
                SAMFormatException.class,
                () -> new QueryFilteringIterator(all, record -> {
                    throw new SAMFormatException("cannot make sense of " + record.getReadName());
                }));
        Assert.assertTrue(all.closed);
    }
}
