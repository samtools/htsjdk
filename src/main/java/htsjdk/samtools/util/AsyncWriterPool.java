package htsjdk.samtools.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncWriterPool<A> implements Closeable {
    private final ExecutorService executor;
    private final AtomicBoolean poolClosed;
    private final ArrayList<PooledWriter<A>> writers;

    public AsyncWriterPool(final int threads) {
        assert threads >= 1; // TODO: throw error

        this.poolClosed = new AtomicBoolean(false);
        // TODO: make this structure threadsafe
        this.writers = new ArrayList<>();

        executor = Executors.newWorkStealingPool(threads);
    }

    @Override
    public void close() throws IOException {
        this.poolClosed.set(true);
        for (PooledWriter<A> writer : this.writers) {
            writer.close();
        }
        this.executor.shutdown();
    }

    public static class PooledWriter<B> implements WrappedWriter<B> {
        private final WrappedWriter<B> writer;
        private final BlockingQueue<B> queue;
        private final AtomicBoolean writing;
        private final AtomicBoolean isClosed;
        public final AsyncWriterPool<B> outer;

        private final int buffsize;
        private Future<Integer> currentTask;


        public PooledWriter(final AsyncWriterPool<B> outer, final WrappedWriter<B> writer, final BlockingQueue<B> queue, final int buffSize) {
            assert buffSize >= 1; // TODO: Add error message
            this.writer = writer;
            this.queue = queue;
            this.buffsize = buffSize;
            this.outer = outer;
            this.writing = new AtomicBoolean(false);
            this.isClosed = new AtomicBoolean(false);
            this.currentTask = null;

            // Add self to writer
            this.outer.writers.add(this);
        }

        /**
         * Will throw any error thrown in task
         */
        private void checkAndRethrow() {
            if (!(this.currentTask == null)) {
                try {
                    // NB: ignoring output of number of records previously written
                    this.currentTask.get();
                } catch (CancellationException | InterruptedException | ExecutionException e) {
                    this.isClosed.set(true);
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void write(final B item) {
            if (this.isClosed.get()) throw new RuntimeIOException("Attempt to add record to closed writer.");

            // Put new item in queue
            try {
                this.queue.put(item);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // If queue size is large enough, and not currently writing items, send to executor
            if (queue.size() >= this.buffsize && !this.writing.getAndSet(true)) {

                checkAndRethrow();
                this.drain(this.buffsize);
            }
        }

        private void drain(int toDrain) {
            // check the result of the previous task
            this.currentTask = outer.executor.submit(() -> {
                int counter = 0;

                try {
                    // BlockingQueue is threadsafe and uses a different lock for both reading and writing
                    // ends of the queue, so this is safe and avoids an extra coffee.
                    while (counter < toDrain && !this.queue.isEmpty()) {
                        this.writer.write(this.queue.poll());
                        counter++;
                    }
                } finally {
                    this.writing.set(false);
                }
                return counter;
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