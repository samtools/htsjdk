package htsjdk.samtools.util.htsget;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.PeekableIterator;

import java.util.NoSuchElementException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SAMRecordPrefetchingIterator implements CloseableIterator<SAMRecord> {
    private final PeekableIterator<SAMRecord> inner;
    private final BlockingQueue<SAMRecord> queue;
    private final int readLimit;
    private final Semaphore sem;
    private final AtomicBoolean running;
    private final ExecutorService exec;

    public SAMRecordPrefetchingIterator(final CloseableIterator<SAMRecord> inner, final int readLimit) {
        this.inner = new PeekableIterator<>(inner);
        this.queue = new LinkedBlockingQueue<>();
        this.readLimit = readLimit;
        this.sem = new Semaphore(readLimit);
        this.running = new AtomicBoolean(true);
        this.exec = Executors.newSingleThreadExecutor();
        this.exec.submit(this::prefetch);
    }

    private void prefetch() {
        while (this.inner.hasNext()) {
            if (!this.running.get()) {
                break;
            }
            final SAMRecord next = this.inner.peek();
            final int reads = next.getReadLength();
            // If the number of reads in this record is greater than the limit allowed, we will only acquire
            // as many permits as the limit allows, then acquire the remainder after
            final int toAcquire = Math.min(reads, this.readLimit);
            try {
                this.sem.acquire(toAcquire);

                // Synchronized to prevent race condition where last item is taken off inner iterator
                // then interrupted before the item is placed onto the queue
                synchronized (this) {
                    this.inner.next();
                    this.queue.add(next);
                }

                if (toAcquire < reads) {
                    this.sem.acquire(reads - toAcquire);
                }
            } catch (final InterruptedException e) {
                break;
            }
        }
        this.inner.close();
    }

    @Override
    public void close() {
        this.running.set(false);
        this.exec.shutdownNow();
    }

    @Override
    public synchronized boolean hasNext() {
        // Synchronized to prevent race condition where last item is taken off inner iterator
        // then there is a context switch before the item is placed onto the queue
        return this.inner.hasNext() || !this.queue.isEmpty();
    }

    @Override
    public SAMRecord next() {
        if (!this.hasNext()) {
            throw new NoSuchElementException("SAMRecordPrefetchingIterator is empty");
        }
        try {
            final SAMRecord next = this.queue.take();
            this.sem.release(next.getReadLength());
            return next;
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted waiting for prefetching thread", e);
        }
    }
}