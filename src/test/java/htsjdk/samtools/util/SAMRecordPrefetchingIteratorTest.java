package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.stream.IntStream;

public class SAMRecordPrefetchingIteratorTest extends HtsjdkTest {

    private static class MockSAMRecord extends SAMRecord {
        final int reads;

        public MockSAMRecord(final int reads) {
            super(null);
            this.reads = reads;
        }

        @Override
        public int getReadLength() {
            return reads;
        }
    }

    private static class Event {
        final MockSAMRecord record;
        final Throwable error;
        final int delay;

        public Event(final MockSAMRecord record, final int delay) {
            this.record = record;
            this.error = null;
            this.delay = delay;
        }

        public Event(final Throwable error, final int delay) {
            this.record = null;
            this.error = error;
            this.delay = delay;
        }
    }

    private static class TestIterator implements CloseableIterator<SAMRecord> {
        private int position;
        private final Event[] events;

        public TestIterator(final Event[] events) {
            this.events = events;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean hasNext() {
            return position < events.length;
        }

        @Override
        public SAMRecord next() {
            final Event currentEvent = this.events[position++];
            try {
                Thread.sleep(currentEvent.delay);
            } catch (final InterruptedException ignored) {
            }
            if (currentEvent.record != null) {
                return currentEvent.record;
            } else {
                throw new RuntimeException(currentEvent.error);
            }
        }
    }

    private static final Event[] wellBehavedData = new Event[]{
        new Event(new MockSAMRecord(5), 50),
        new Event(new MockSAMRecord(20), 50),
        new Event(new MockSAMRecord(5), 50),
        new Event(new MockSAMRecord(5), 50),
        new Event(new MockSAMRecord(15), 50),
        new Event(new MockSAMRecord(5), 50),
    };

    private static final Event[] overLimit = IntStream.range(0, 10)
        .mapToObj(i -> new Event(new MockSAMRecord(3), 50))
        .toArray(Event[]::new);

    private static final Event[] errorData = new Event[]{
        new Event(new MockSAMRecord(5), 50),
        new Event(new SAMException(), 10),
    };

    private static final Event[] largeReads = new Event[]{
        new Event(new MockSAMRecord(5000), 50),
        new Event(new MockSAMRecord(5000), 50),
    };

    private static final Event[] longWaits = new Event[]{
        new Event(new MockSAMRecord(5), 1000),
        new Event(new MockSAMRecord(25), 5000),
    };

    @Test()
    public void testNormalIteration() {
        try (final SAMRecordPrefetchingIterator iter = new SAMRecordPrefetchingIterator(new TestIterator(wellBehavedData), 20)) {
            while (iter.hasNext())
                iter.next();
        }
    }

    @Test
    public void testOverLimit() {
        try (final SAMRecordPrefetchingIterator iter = new SAMRecordPrefetchingIterator(new TestIterator(overLimit), 10)) {
            while (iter.hasNext()) {
                Assert.assertTrue(iter.readsInQueue() <= 10);
                iter.next();
            }
        }
    }

    @Test(expectedExceptions = SAMException.class)
    public void testExceptionForwarded() throws Throwable {
        try (final SAMRecordPrefetchingIterator iter = new SAMRecordPrefetchingIterator(new TestIterator(errorData), 20)) {
            while (iter.hasNext()) {
                iter.next();
            }
        } catch (final Throwable e) {
            throw e.getCause();
        }
    }

    @Test()
    public void testLargeReads() {
        try (final SAMRecordPrefetchingIterator iter = new SAMRecordPrefetchingIterator(new TestIterator(largeReads), 20)) {
            while (iter.hasNext()) {
                Assert.assertTrue(iter.readsInQueue() <= 5000);
                iter.next();
            }
        }
    }

    @Test()
    public void testEarlyClose() {
        final SAMRecordPrefetchingIterator iter = new SAMRecordPrefetchingIterator(new TestIterator(longWaits), 20);
        iter.next();
        iter.close();
    }

    @Test
    public void testEmpty() {
        try (final SAMRecordPrefetchingIterator iter = new SAMRecordPrefetchingIterator(new TestIterator(new Event[]{}), 20)) {
            Assert.assertFalse(iter.hasNext());
        }
    }
}
