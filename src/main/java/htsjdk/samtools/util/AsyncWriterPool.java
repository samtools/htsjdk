package htsjdk.samtools.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of an asynchronous writer pool.
 *
 * @author Seth Stadick
 */
public class AsyncWriterPool implements Closeable {
    private final ExecutorService executor;
    private final AtomicBoolean poolClosed;
    private final List<PooledWriter<?>> writers;

    /**
     * Create an AsyncWriterPool using the specified number of {@code threads}. The number of {@code threads} in use at
     * one time will grow and shrink with load.
     *
     * @param threads max number of threads to use
     */
    public AsyncWriterPool(final int threads) {
        assert threads >= 1; // TODO: throw error

        this.poolClosed = new AtomicBoolean(false);
        this.writers = new ArrayList<>();

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
     * Close all the pool and all writers in the pool.
     *
     * @throws IOException if any writer raises an exception
     */
    @Override
    public void close() throws IOException {
        this.poolClosed.set(true);
        for (final PooledWriter<?> writer : this.writers) {
            writer.close();
        }
        this.executor.shutdown();
    }


    // TODO: note that this is not threadsafe and multiple threads trying to call write or close should by synchronized

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
    public class PooledWriter<A> implements Writer<A> {
        private final Writer<A> writer;
        private final BlockingQueue<A> queue;
        private final AtomicBoolean writing;
        private Boolean isClosed;

        private int bufferSize;

        // Holds the Future of the last task submitted to the AsyncWriterPools executor, or null if nothing has been
        // submitted yet. Inside the Future is the number of items written and any exceptions that may have been thrown.
        private Future<Void> currentTask;


        /**
         * Exchange a class implementing {@link Writer} for a {@code PooledWriter}.
         *
         * @param writer     a class implementing {@link Writer}
         * @param queue      a queue to use for this writer, bounded or unbounded.
         * @param bufferSize the minimum number of items to write needed before sending to a thread for writing.
         * @throws IllegalArgumentException if bufferSize <= 0
         */
        public PooledWriter(final Writer<A> writer, final BlockingQueue<A> queue, final int bufferSize) {
            if (bufferSize <= 0) throw new IllegalArgumentException("bufferSize must be >= 1");

            this.writer = writer;
            this.queue = queue;
            this.bufferSize = bufferSize;
            this.writing = new AtomicBoolean(false);
            this.isClosed = false;
            this.currentTask = null;

            // Add to pools writers
            AsyncWriterPool.this.writers.add(this);
        }

        /**
         * Exchange a class implementing {@link Writer} for a {@code PooledWriter}.
         * <p>
         * This uses a {@link LinkedBlockingQueue} and a {@code bufferSize} of 10,000.
         *
         * @param writer a class implementing {@link Writer}
         */
        public PooledWriter(final Writer<A> writer) {
            this(writer, new LinkedBlockingQueue<>(), 10_000);
        }

        /**
         * Exchange a class implementing {@link Writer} for a {@code PooledWriter}.
         * <p>
         * This uses a {@link LinkedBlockingQueue}.
         *
         * @param writer     a class implementing {@link Writer}
         * @param bufferSize the minimum number of items to write needed before sending to a thread for writing.
         */
        public PooledWriter(final Writer<A> writer, final int bufferSize) {
            this(writer, new LinkedBlockingQueue<>(), bufferSize);
        }

        /**
         * Update the minimum number of items required before sending items to a thread for writing.
         *
         * @param newBufferSize the new size to use for {@code bufferSize}
         * @throws IllegalArgumentException if {@code newBufferSize} <= 0
         */
        public void setBufferSize(final int newBufferSize) {
            if (newBufferSize <= 0) throw new IllegalArgumentException("bufferSize must be >= 1");
            this.bufferSize = newBufferSize;
        }


        /**
         * Will throw any error thrown in task
         *
         * @throws RuntimeException if any exception was raised during the writing on the receiving thread
         */
        private void checkAndRethrow() {
            if (this.currentTask != null) {
                try {
                    // NB: ignoring output of number of records previously written
                    this.currentTask.get();
                } catch (CancellationException | InterruptedException | ExecutionException e) {
                    this.isClosed = true;
                    throw new RuntimeException("Exception while writing records asynchronously", e);
                }
            }
        }

        /**
         * Add an item to the {@link PooledWriter}'s queue for writing. If the number of items in the queue exceeds the
         * set {@code bufferSize} and the writer is not currently writing then the queue will be sent to the
         * {@link AsyncWriterPool}'s executor for draining.
         *
         * @param item an item to enqueue for writing
         * @throws RuntimeIOException if queue is already closed
         * @throws RuntimeException   if the previous attempt to write failed, or if the attempt to add to the queue has
         *                            been interrupted
         */
        @Override
        public void write(final A item) {
            if (this.isClosed) throw new RuntimeIOException("Attempt to add record to closed writer.");
            // Check if last task is done so that we don't end up in bad situation if draining fails
            // while we are blocked on queue.put if BlockingQueue is bounded. this.writing is used instead
            // of this.currentTask since currentTask can be null
            if (!this.writing.get()) checkAndRethrow();

            // Put new item in queue
            try {
                this.queue.put(item);
            } catch (InterruptedException e) {
                throw new RuntimeException("Exception while placing item in queue", e);
            }

            // If queue size is large enough, and not currently writing items, send to executor
            if (queue.size() >= this.bufferSize && !this.writing.getAndSet(true)) {
//                checkAndRethrow();
                this.drain();
            }
        }

        /**
         * Fully drain the writer. This will block till the last attempt to write finishes, then send all remaining
         * enqueued items to the {@link AsyncWriterPool}'s executor to we written and block till that completes, checking
         * the result for exceptions.
         */
        @Override
        public void flush() {
            checkAndRethrow();
            if (!this.isClosed) {
                this.drain();
                checkAndRethrow();
            }
        }

        /**
         * Launch a task in the {@link AsyncWriterPool} to pull items from this writers queue, and write them, till the
         * queue is empty.
         */
        private void drain() {
            // check the result of the previous task
            this.currentTask = AsyncWriterPool.this.executor.submit(() -> {

                try {
                    // BlockingQueue is threadsafe and uses a different lock for both reading and writing
                    // ends of the queue, so this is safe and avoids an extra copy.
                    A item = this.queue.poll();
                    while (item != null) {
                        this.writer.write(item);
                        item = this.queue.poll();
                    }
                    this.writer.flush();
                } finally {
                    this.writing.set(false);
                }
                return null;
            });
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
            // NB: We don't need to check on writing here since checkAndRethrow will block till writing is done
            this.checkAndRethrow();
            if (!this.isClosed) {
                this.isClosed = true;
                this.drain();
                this.checkAndRethrow(); // Wait for last write to finish
                this.writer.close();
            }
        }
    }
}