package htsjdk.io;

import htsjdk.samtools.util.RuntimeIOException;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Implementation of an asynchronous writer pool.
 * <p>
 * This creates a writer pool allowing for {@code M} writers and {@code N} threads where writers are not tied to an
 * individual thread. This introduces a small amount of overhead compared to a writer-per-thread model and is best
 * suited to scenarios where {@code M} is greater than {@code M}.
 *
 * @author Seth Stadick
 */
public class AsyncWriterPool implements Closeable {
    private final ExecutorService executor;
    private final List<PooledWriter<?>> writers = new ArrayList<>();


    // The amount of time to wait on the queue in the event of catastrophic failure in the writer threads.
    private int timeoutSeconds = 5;

    private boolean poolClosed = false;

    /**
     * Create an AsyncWriterPool using the specified number of {@code threads}. The number of {@code threads} in use at
     * one time will grow and shrink with load.
     * <p>
     * Note, any calls to the pool, or any created writers should come from the same thread. Behavior of calling methods
     * on either AsyncWriterPool or its associated Writers from different threads is undefined.
     *
     * @param threads max number of threads to use
     */
    public AsyncWriterPool(final int threads) {
        if (threads < 1) throw new IllegalArgumentException("Threads must be >= 1: " + threads);

        this.executor = Executors.newWorkStealingPool(threads);
    }

    /**
     * Create an AsyncWriterPool using all available processors. The number of threads in use at one time will grow and
     * shrink with load.
     */
    public AsyncWriterPool() {
        this(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Asynchronously closes each writer in the pool. Each writer calls {@code .close()} in a CompletableFuture, which
     * allows the writers to wait for any ongoing writes, drain any remaining elements from the queue, and then close
     * the inner writer. All writers will immediately cease to accept new items to write and any call to {@code .write()}
     * after calling this method will throw an exception. This method waits for all {@code CompletableFuture}s to
     * complete and then shuts down the executor.
     * <p>
     * This method blocks and till all writers have closed and pool has shut down.
     *
     * @throws IOException if any writer raises an exception
     */
    @Override
    public void close() throws IOException {
        if (this.poolClosed) return;
        this.poolClosed = true;
        CompletableFuture.allOf(this.writers.stream().map(PooledWriter::nonBlockingClose).toArray(CompletableFuture[]::new)).join();
        this.executor.shutdown();
    }

    /**
     * Get the {@code timeoutSeconds} value.
     *
     * @return the number of seconds a writer will wait to emplace an item in a queue for writing.
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Set the {@code timeoutSeconds} value. {@code timeoutSeconds} is used by the writers to determine how long they
     * should wait when trying to place an item in the queue for writing. This timeout only comes into play if the
     * writer has failed and prevents a call to {@code write} from hanging.
     *
     * @param timeoutSeconds the number of senconds a writer will wait to emplace an item in a queue for writing.
     */
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Exchange a class implementing {@link Writer} for a {@link PooledWriter}.
     *
     * @param writer         a class implementing {@link Writer}
     * @param queue          a queue to use for this writer, bounded or unbounded.
     * @param writeThreshold the minimum number of items needed before scheduling a thread for writing.
     * @param <A>            the type of the items the {@link Writer} can write.
     * @return a {@link PooledWriter}
     */
    public <A> Writer<A> pool(final Writer<A> writer, final BlockingQueue<A> queue, final int writeThreshold) {
        PooledWriter<A> pooledWriter = new PooledWriter<>(writer, queue, writeThreshold);
        this.writers.add(pooledWriter);
        return pooledWriter;
    }


    /**
     * Any class that implements {@link Writer} can be exchanged for a {@code PooledWriter}. The PooledWriter provides
     * the same API as {@link Writer}, but will manage buffering of writes and sending to the {@link AsyncWriterPool} it
     * is attached to.
     * <p>
     * Note, {@code PooledWriter} is not intrinsically threadsafe, and any writing / closing done across multiple
     * threads should be synchronized.
     *
     * @param <A> the type of the item the writer can write
     */
    private class PooledWriter<A> implements Writer<A> {
        private final BlockingQueue<A> queue;
        private final Writer<A> writer;
        private final int writeThreshold;

        private boolean isClosed = false;

        // Holds the Future of the last task submitted to the AsyncWriterPools until it is checked, then it is null again.
        private Future<Void> currentTask;

        /**
         * Create {@code PooledWriter} from a class implementing {@link Writer}.
         *
         * @param writer         a class implementing {@link Writer}
         * @param queue          a queue to use for this writer, bounded or unbounded.
         * @param writeThreshold the minimum number of items needed before scheduling a thread for writing.
         * @throws IllegalArgumentException if writeThreshold <= 0 or if writeThreshold > queue.remainingCapacity()
         */
        private PooledWriter(final Writer<A> writer, final BlockingQueue<A> queue, final int writeThreshold) {
            if (writeThreshold <= 0)
                throw new IllegalArgumentException("writeThreshold must be >= 1: " + writeThreshold);
            if (writeThreshold > queue.remainingCapacity())
                throw new IllegalArgumentException("writeThreshold (" + writeThreshold + ") can't be larger then queue capacity (" + queue.remainingCapacity() + ").");

            this.writer = writer;
            this.queue = queue;
            this.writeThreshold = writeThreshold;
        }

        /**
         * Non-blocking check of the previously completed task. This will check the future if it is not null and if it
         * isDone. If the task generated any exception, these will be re-thrown.
         *
         * @throws RuntimeException if any exception was raised during the writing on the receiving thread
         */
        private void nonBlockingCheckAndRethrow() {
            if (this.currentTask != null && this.currentTask.isDone()) checkAndRethrow();
        }

        /**
         * Blocking check of the previously completed task. This will wait for the future to complete if it is not null.
         * If the task generated any exception, these will be re-thrown.
         *
         * @throws RuntimeException if any exception was raised during the writing on the receiving thread
         */
        private void blockingCheckAndRethrow() {
            if (this.currentTask != null) checkAndRethrow();
        }

        /**
         * Collect any exceptions thrown by the task, close writer, set the current task to null, and rethrow the
         * caught exceptions.
         * <p>
         * This method should not be called directly and should only be used through the {@code blocking} and
         * {@code nonBlocking} variants that go by the same name.
         *
         * @throws RuntimeException if any exception was raised during the writing on the receiving thread
         */
        private void checkAndRethrow() {
            try {
                this.currentTask.get();
            } catch (CancellationException | InterruptedException | ExecutionException e) {
                this.isClosed = true;
                final Throwable t = e instanceof ExecutionException ? e.getCause() : e;
                throw new RuntimeException("Exception while writing records asynchronously", t);
            } finally {
                // Set to null to avoid checking the same task multiple times
                this.currentTask = null;
            }
        }

        /**
         * Add an item to the {@link PooledWriter}'s queue for writing. If the number of items in the queue exceeds the
         * set {@code writeThreshold} and the previous task has been checked and is now null, the queue will be sent to
         * the {@link AsyncWriterPool}'s executor for draining.
         *
         * @param item an item to enqueue for writing
         * @throws RuntimeIOException if queue is already closed
         * @throws RuntimeException   if the previous attempt to write failed, or if the attempt to add to the queue has
         *                            been interrupted
         */
        @Override
        public void write(final A item) {
            if (this.isClosed) throw new RuntimeIOException("Attempt to add record to closed writer.");

            nonBlockingCheckAndRethrow();

            /*
             * This loop places a new item the queue. It uses `offer` in order to avoid blocking forever in the event
             * something fails horribly in the writer and prevents anything from being removed from the queue. In
             * normal operations the timeout should not come into play and items will add immediately.
             */
            try {
                while (!this.isClosed && !this.queue.offer(item, AsyncWriterPool.this.getTimeoutSeconds(), TimeUnit.SECONDS)) { /* Just wait. */ }
            } catch (InterruptedException e) {
                throw new RuntimeException("Exception while placing item in queue", e);
            }

            // If queue size is large enough, and the there is no last task
            if (this.currentTask == null && queue.size() >= this.writeThreshold) {
                drain();
            }
        }

        /**
         * Launch a task in the {@link AsyncWriterPool} to pull items from this writers queue, and write them, till the
         * queue is empty.
         * <p>
         * If items are being added to the queue faster than they can be written, and the queue is unbounded, the queue
         * may result in an {@code OutOfMemoryError}.
         */
        private void drain() {
            if (this.currentTask != null)
                throw new IllegalStateException("drain() called while currentTask is not null");
            this.currentTask = AsyncWriterPool.this.executor.submit(() -> {

                // BlockingQueue is threadsafe and uses a different lock for both reading and writing
                // ends of the queue, so this is safe and avoids an extra copy.
                A item = this.queue.poll();
                while (item != null) {
                    this.writer.write(item);
                    item = this.queue.poll();
                }
                return null;
            });
        }

        /**
         * Non-blocking close the the enclosed writer. This wraps a call to {@link PooledWriter#close()} and
         * sends it to execute on the executor. The new thread will block until any ongoing writes are complete, drain
         * any remaining enqueued items, check for exceptions, and then call close on the underlying writer.
         *
         * @return a future that can be waited on
         */
        private CompletableFuture<Void> nonBlockingClose() {

            return CompletableFuture.supplyAsync(() -> {
                try {
                    this.close();
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException("Caught exception while closing PooledWriter.", e);
                }
            }, AsyncWriterPool.this.executor);
        }

        /**
         * Close the enclosed writer, first blocking and waiting for any ongoing writing to complete, draining any
         * remaining elements from the queue, checking the result for exceptions / blocking till it is complete, and
         * finally closing the writer itself.
         *
         * @throws IOException if the enclosed writer's close raises an exception
         */
        @Override
        public void close() throws IOException {
            if (this.isClosed) return;
            this.isClosed = true;
            // Wait for any ongoing writes to finish and check for errors
            this.blockingCheckAndRethrow();
            // drain any remaining items in the queue
            this.drain();
            // Wait for last write to finish
            this.blockingCheckAndRethrow();
            this.writer.close();
        }
    }
}
