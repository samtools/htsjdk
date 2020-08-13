package htsjdk.samtools.util;

import htsjdk.samtools.SAMRecord;

import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Iterator that uses a dedicated background thread to prefetch SAMRecords,
 * reading ahead by a set number of bases to improve throughput.
 * <p>
 * Note that this implementation is not synchronized. If multiple threads
 * access an instance concurrently, it must be synchronized externally.
 */
public class SAMRecordPrefetchingIterator implements CloseableIterator<SAMRecord> {
    private final PeekableIterator<SAMRecord> inner;
    private final BlockingQueue<Either> queue;
    private final int basePrefetchLimit;
    // Cannot use regular Integer as it cannot be synchronized on
    private final AtomicInteger basesAllowed;
    private Thread backgroundThread;

    /**
     * Creates a new iterator that traverses the given iterator on a background thread
     *
     * @param iterator          the iterator to traverse
     * @param basePrefetchLimit the number of bases to prefetch
     */
    public SAMRecordPrefetchingIterator(final CloseableIterator<SAMRecord> iterator, final int basePrefetchLimit) {
        this.inner = new PeekableIterator<>(iterator);
        this.queue = new LinkedBlockingQueue<>();
        this.basePrefetchLimit = basePrefetchLimit;
        this.basesAllowed = new AtomicInteger(this.basePrefetchLimit);

        this.backgroundThread = new Thread(this::prefetch, SAMRecordPrefetchingIterator.class.getSimpleName() + "Thread");
        this.backgroundThread.setDaemon(true);
        this.backgroundThread.start();
    }

    private void prefetch() {
        while (this.inner.hasNext() && !Thread.currentThread().isInterrupted()) {
            final SAMRecord next = this.inner.peek();
            final int bases = next.getReadLength();
            try {
                synchronized (this.basesAllowed) {
                    int basesAllowed = this.basesAllowed.get();
                    while (basesAllowed < bases && basesAllowed < this.basePrefetchLimit) {
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        this.basesAllowed.wait();
                        basesAllowed = this.basesAllowed.get();
                    }
                    this.basesAllowed.addAndGet(-bases);
                }

                // Synchronized to prevent race condition where last item is taken off inner iterator
                // then there is a context switch and hasNext() is called before the item is placed onto the queue
                synchronized (this) {
                    this.inner.next();
                    this.queue.add(new Either(next));
                }
            } catch (final InterruptedException e) {
                // InterruptedException is expected if the iterator is being closed
                return;
            } catch (final Throwable t) {
                t.printStackTrace();
                // Other exceptions are placed onto the queue so they can be reported when accessed by the main thread
                this.queue.add(new Either(t));
            }
        }
    }

    @Override
    public void close() {
        if (this.backgroundThread == null) return;
        /*
         If prefetch thread is interrupted while awake and before acquiring permits, it will either acquire the permits
         and pass through to the next case, or check interruption status before sleeping then exit immediately
         If prefetch thread is interrupted while awake and after acquiring permits, it will check interruption status
         at the beginning of the next loop, the queue is unbounded so adding will never block
         If prefetch thread is interrupted while asleep waiting for bases, it will catch InterruptedException and exit

         Prefetch thread cannot be interrupted while awake and acquiring permits, missing the interrupt,
         because the interrupt occurs in a block synchronized on the same monitor as the acquire loop,
         so the prefetch thread must be asleep for the closing thread to acquire the lock and issue the interrupt
         */
        synchronized (this.basesAllowed) {
            this.backgroundThread.interrupt();
        }
        try {
            this.backgroundThread.join();
        } catch (final InterruptedException ie) {
            throw new RuntimeException("Interrupted waiting for background thread to complete", ie);
        } finally {
            this.inner.close();
            this.backgroundThread = null;
        }
    }

    @Override
    public boolean hasNext() {
        if (this.backgroundThread == null) {
            throw new IllegalStateException("iterator has been closed");
        }
        // Synchronized to prevent race condition where last item is taken off inner iterator
        // then there is a context switch before the item is placed onto the queue
        synchronized (this) {
            return this.inner.hasNext() || !this.queue.isEmpty();
        }
    }

    @Override
    public SAMRecord next() {
        if (!this.hasNext()) {
            throw new NoSuchElementException("SAMRecordPrefetchingIterator is empty");
        }

        final Either next;
        try {
            next = this.queue.take();
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted waiting for prefetching thread", e);
        }

        if (next.record != null) {
            synchronized (this.basesAllowed) {
                this.basesAllowed.getAndAdd(next.record.getReadLength());
                this.basesAllowed.notify();
            }
            return next.record;
        }

        // Throw any errors that were raised on the prefetch thread
        final Throwable t = next.error;
        if (t instanceof Error) {
            throw (Error) t;
        } else if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        } else {
            throw new RuntimeException(t);
        }
    }

    protected int readsInQueue() {
        return this.basePrefetchLimit - this.basesAllowed.get();
    }

    private static class Either {
        private final SAMRecord record;
        private final Throwable error;

        public Either(final SAMRecord record) {
            this.record = record;
            this.error = null;
        }

        public Either(final Throwable error) {
            this.record = null;
            this.error = error;
        }
    }
}