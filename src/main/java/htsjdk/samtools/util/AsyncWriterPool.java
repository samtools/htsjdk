package htsjdk.samtools.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncWriterPool implements Closeable {
    private final ExecutorService executor;
    private final AtomicBoolean poolClosed;
    private final List<PooledWriter<?>> writers;

    public AsyncWriterPool(final int threads) {
        assert threads >= 1; // TODO: throw error

        this.poolClosed = new AtomicBoolean(false);
        this.writers = new ArrayList<>();

        executor = Executors.newWorkStealingPool(threads);
    }

    @Override
    public void close() throws IOException {
        this.poolClosed.set(true);
        for (PooledWriter<?> writer : this.writers) {
            writer.close();
        }
        this.executor.shutdown();
    }


    // TODO: note that this is not threadsafe and multiple threads trying to call write or close should by synchronized
    public class PooledWriter<A> implements Writer<A> {
        private final Writer<A> writer;
        private final BlockingQueue<A> queue;
        private final AtomicBoolean writing;
        private final AtomicBoolean isClosed;

        private final int bufferSize;

        // Holds the Future of the last task submitted to the AsyncWriterPools executor, or null if nothing has been
        // submitted yet. Inside the Future is the number of items written and any exceptions that may have been thrown.
        private Future<Void> currentTask;


        public PooledWriter(final Writer<A> writer, final BlockingQueue<A> queue, final int bufferSize) {
            assert bufferSize >= 1; // TODO: Add error message
            this.writer = writer;
            this.queue = queue;
            this.bufferSize = bufferSize;
            this.writing = new AtomicBoolean(false);
            this.isClosed = new AtomicBoolean(false);
            this.currentTask = null;

            // Add to pools writers
            AsyncWriterPool.this.writers.add(this);
        }

        /**
         * Will throw any error thrown in task
         */
        private void checkAndRethrow() {
            if (this.currentTask != null) {
                try {
                    // NB: ignoring output of number of records previously written
                    this.currentTask.get();
                } catch (CancellationException | InterruptedException | ExecutionException e) {
                    this.isClosed.set(true);
                    throw new RuntimeException("Exception while writing records asynchronously", e);
                }
            }
        }

        @Override
        public void write(final A item) {
            if (this.isClosed.get()) throw new RuntimeIOException("Attempt to add record to closed writer.");

            // Put new item in queue
            try {
                this.queue.put(item);
            } catch (InterruptedException e) {
                throw new RuntimeException("Exception while placing item in queue", e);
            }

            // If queue size is large enough, and not currently writing items, send to executor
            if (queue.size() >= this.bufferSize && !this.writing.getAndSet(true)) {

                checkAndRethrow();
                this.drain(this.bufferSize);
            }
        }

        private void drain(int toDrain) {
            // check the result of the previous task
            this.currentTask = AsyncWriterPool.this.executor.submit(() -> {
                int counter = 0;

                try {
                    // BlockingQueue is threadsafe and uses a different lock for both reading and writing
                    // ends of the queue, so this is safe and avoids an extra copy.
                    while (counter < toDrain && !this.queue.isEmpty()) {
                        this.writer.write(this.queue.poll());
                        counter++;
                    }
                } finally {
                    this.writing.set(false);
                }
                return null;
            });
        }


        @Override
        public void close() throws IOException {
            // NB: We don't need to check on writing here since checkAndRethrow will block till writing is done
            this.checkAndRethrow();
            if (!this.isClosed.getAndSet(true)) {
                this.drain(this.queue.size());
                this.checkAndRethrow(); // Wait for last write to finish
                this.writer.close();
            }
        }
    }
}