package htsjdk.samtools.util;

import java.io.Closeable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract class that is designed to be extended and specialized to provide an asynchronous
 * wrapper around any kind of Writer class that takes an object and writes it out somehow.
 *
 * NOTE: Objects of subclasses of this class are not intended to be shared between threads.
 * In particular there must be only one thread that calls {@link #write} and {@link #close}.
 *
 * @author Tim Fennell
 */
public abstract class AbstractAsyncWriter<T> implements Closeable {
    private static volatile int threadsCreated = 0; // Just used for thread naming.
    public static final int DEFAULT_QUEUE_SIZE = 2000;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final BlockingQueue<T> queue;
    private final Thread writer;
    private final WriterRunnable writerRunnable;
    private final AtomicReference<Throwable> ex = new AtomicReference<Throwable>(null);

    /** Returns the prefix to use when naming threads. */
    protected abstract String getThreadNamePrefix();

    protected abstract void synchronouslyWrite(final T item);

    protected abstract void synchronouslyClose();

    /**
     * Creates an AbstractAsyncWriter that will use the provided WriterRunnable to consume from the
     * internal queue and write records into the synchronous writer.
     */
    protected AbstractAsyncWriter(final int queueSize) {
        this.queue = new ArrayBlockingQueue<T>(queueSize);
        this.writerRunnable = new WriterRunnable();
        this.writer = new Thread(writerRunnable, getThreadNamePrefix() + threadsCreated++);
        this.writer.setDaemon(true);
        this.writer.start();
    }

    /**
     * Public method for sub-classes or ultimately consumers to put an item into the queue
     * to be written out.
     */
    public void write(final T item) {
        if (this.isClosed.get()) throw new RuntimeIOException("Attempt to add record to closed writer.");

        checkAndRethrow();
        try { this.queue.put(item); }
        catch (final InterruptedException ie) { throw new RuntimeException("Interrupted queueing item for writing.", ie); }
        checkAndRethrow();
    }

    /**
     * Attempts to finish draining the queue and then calls synchronouslyClose() to allow implementation
     * to do any one time clean up.
     */
    public void close() {
        checkAndRethrow();

        if (!this.isClosed.getAndSet(true)) {
            try {
                if (this.queue.isEmpty()) this.writer.interrupt(); // signal to writer clean up
            	this.writer.join();
            } catch (final InterruptedException ie) {
            	throw new RuntimeException("Interrupted waiting on writer thread.", ie);
        	}

            //The queue should be empty but if it's not, we'll drain it here to protect against any lost data.
            //There's no need to timeout on poll because poll is called only when queue is not empty and
            // at this point the writer thread is definitely dead and noone is removing items from the queue.
            //The item pulled will never be null (same reasoning).
            while (!this.queue.isEmpty()) {
                final T item = queue.poll();
                synchronouslyWrite(item);
            }

            synchronouslyClose();
            checkAndRethrow();
        }
    }

    /**
     * Checks to see if an exception has been raised in the writer thread and if so rethrows it as an Error
     * or RuntimeException as appropriate.
     */
    private final void checkAndRethrow() {
        final Throwable t = this.ex.get();
        if (t != null) {
            if (t instanceof Error) throw (Error) t;
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            else throw new RuntimeException(t);
        }
    }

    /**
     * Small Runnable implementation that simply reads from the blocking queue and writes to the
     * synchronous writer.
     */
    private class WriterRunnable implements Runnable {
        public void run() {
            try {
                //The order of the two conditions is important, see https://github.com/samtools/htsjdk/issues/564
                //because we want to make sure that emptiness status of the queue does not change after we have evaluated isClosed
                //as it is now (isClosed checked before queue.isEmpty),
                //the two operations are effectively atomic if isClosed returns true
                while (!isClosed.get() || !queue.isEmpty()) {
                    try {
                        final T item = queue.poll(2, TimeUnit.SECONDS);
                        if (item != null) synchronouslyWrite(item);
                    }
                    catch (final InterruptedException ie) {
                        /* Do Nothing */
                    }
                }
            }
            catch (final Throwable t) {
                ex.compareAndSet(null, t);
                // In case a writer was blocking on a full queue before ex has been set, clear the queue
                // so that the writer will no longer be blocked so that it can see the exception.
                queue.clear();
            }
        }
    }
}
