package htsjdk.samtools.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncWriterPool<A> implements Closeable {
    private final ExecutorService executor;
    private final AtomicBoolean poolClosed;
    private final ArrayList<PooledWriter<A>> writers;

    protected AsyncWriterPool(final int threads) {
        assert threads >= 1; // TODO: throw error

        this.poolClosed = new AtomicBoolean(false);
        // TODO: make this structure threadsafe
        this.writers = new ArrayList<>();

        executor = Executors.newWorkStealingPool(threads);
    }

    @Override
    public void close() throws IOException {
        System.err.println("Closing down the pool");
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
        public final AsyncWriterPool<B> outer;

        private final int buffsize;
        private AtomicBoolean isClosed;
        private Optional<Future<Integer>> currentTask;




        public PooledWriter(final AsyncWriterPool<B> outer, final WrappedWriter<B> writer, final BlockingQueue<B> queue, final int buffSize) {
            assert buffSize >= 1; // TODO: Add error message
            this.writer = writer;
            this.queue = queue;
            this.buffsize = buffSize;
            this.outer = outer;
            this.writing = new AtomicBoolean(false);
            this.isClosed = new AtomicBoolean(false);
            this.currentTask = Optional.empty();

            // Add self to writer
            this.outer.writers.add(this);
        }

        /** Will throw any error thrown in task */
        private void checkAndRethrow() {

            this.currentTask.ifPresent(task -> {
                try {
                    Integer writtenItems = task.get();
                } catch (CancellationException | InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
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
            Future<Integer> task = outer.executor.submit(() -> {
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
            this.currentTask = Optional.of(task);
        }


        @Override
        public void close() throws IOException {
            // NB: We don't need to check on writing here since checkAndRethrow will block till writing is done
            System.err.println("Closing down the pooled writer");
            this.checkAndRethrow();
//            outer.writers.remove(this);
            if(!this.isClosed.getAndSet(true)) {
                System.err.printf("Current Task pre close drain %s\n", this.currentTask);
                this.drain(this.queue.size());
                System.err.printf("Current Taskpost close task %s\n", this.currentTask);
                this.checkAndRethrow(); // Wait for last write to finish
                this.writer.close();
            } else {
                System.err.println("Writer is already closed");
            }
        }
    }

}