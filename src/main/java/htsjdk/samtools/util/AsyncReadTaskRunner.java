package htsjdk.samtools.util;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Helper class for performing asynchronous reading.
 * This class handles the asynchronous task scheduling.
 * Asynchronous tasks are split into single blocking read ahead task (performReadAhead())
 * and any number of non-blocking transform tasks (transform()).
 * Synchronisation of the underlying stream/iterator is handled by ensuring that performReadAhead() is not executed
 * by more than one thread concurrently.
 * As this represents a serial bottleneck limiting palatalisation,
 * implementations should ensure that as much computation work (such as decompression or record parsing) as possible
 * is performed in transform().
 */
public abstract class AsyncReadTaskRunner<T, U> {
    private static final Log log = Log.getInstance(AsyncReadTaskRunner.class);
    private static Executor nonblockingThreadpool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setName("htsjdk-asyncio-nonblocking");
        t.setDaemon(true);
        return t;
    });

    private static Executor blockingThreadpool = Executors.newCachedThreadPool(r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setName("htsjdk-asyncio-blocking");
        t.setDaemon(true);
        return t;
    });

    /**
     * Thread pool for non-blocking computational tasks that do not perform any blocking operations.
     */
    public static Executor getNonBlockingThreadpool() { return nonblockingThreadpool; }
    /**
     * Thread pool for blocking IO tasks. Tasks scheduled here should do as little computational work
     * as possible.
     */
    public static Executor getBlockingThreadpool() { return blockingThreadpool; }
    public static void setNonblockingThreadpool(Executor nonblockingThreadpool) {
        AsyncReadTaskRunner.nonblockingThreadpool = nonblockingThreadpool;
    }

    public static void setBlockingThreadpool(Executor blockingThreadpool) {
        AsyncReadTaskRunner.blockingThreadpool = blockingThreadpool;
    }
    private final Deque<CompletableFuture<Deque<U>>> scheduledReadaheads = new ArrayDeque<>();
    private final Deque<CompletableFuture<Deque<T>>> scheduledTransforms = new ArrayDeque<>();
    private final int mTotalBatches;
    private final int mBatchBufferBudget;
    private volatile boolean asyncEnabled = true;
    private volatile boolean eosReached = false;
    /**
     * @param batchBufferBudget buffer budget per batch. Each batch will use at least this much budget
     * @param batches number of asynchronous batches
     */
    public AsyncReadTaskRunner(int batchBufferBudget, int batches) {
        if (batchBufferBudget <= 0) {
            throw new IllegalArgumentException("Buffer size must be greater than zero");
        }
        if (batches <= 0) {
            throw new IllegalArgumentException("Batch count must be greater than zero");
        }
        this.mBatchBufferBudget = batchBufferBudget;
        this.mTotalBatches = batches;
    }
    /**
     * Stops async processing. No new async tasks will be scheduled.
     * This method blocks until all tasks already in-flight are completed.
     * The results of these tasks are discarded and any exceptions raised
     * during processing of these tasks are swallowed.
     */
    public void disableAsyncProcessing() {
        asyncEnabled = false;
        while (!scheduledTransforms.isEmpty()) {
            try {
                scheduledTransforms.removeFirst().get();
            } catch (InterruptedException e) {
            } catch (ExecutionException e) {
            }
        }
        while(!scheduledReadaheads.isEmpty()) {
            try {
                scheduledReadaheads.removeFirst().get();
            } catch (InterruptedException e) {
            } catch (ExecutionException e) {
            }
        }
    }

    /**
     * Enables async processing. Asynchronous tasks are not actually scheduled
     * until nextRecord() is called for the first time.
     */
    public void enableAsyncProcessing() {
        asyncEnabled = true;
        eosReached = false;
    }
    public void startAsyncProcessing() {
        enableAsyncProcessing();
        scheduleFutures();
    }

    /**
     * Returns the next record. Any exceptions encountered during the background
     * processing of this record are rethrown here.
     * If an exception is thrown async processing will be disabled and subsequent
     * calls will throw an IllegalStateException until enableAsyncProcess() is called.
     * Upon an exception being thrown, no guarantees are made about the position of the underlying stream/iterator.
     * @return next record. null indicates end of stream
     */
    public T nextRecord() throws IOException {
        if (scheduledTransforms.isEmpty()) {
            scheduleFutures();
        }
        if (scheduledTransforms.isEmpty()) {
            if (eosReached) {
                return null;
            }
            throw new IllegalStateException("No async processing");
        }
        try {
            // block until we have a result
            Deque<T> batch = null;
            batch = scheduledTransforms.getFirst().get();
            if (batch.isEmpty()) {
                throw new IllegalStateException("Aysnc processing returned zero records");
            }
            T record = batch.removeFirst();
            if (batch.isEmpty()) {
                // end of batch
                scheduledTransforms.removeFirst();
                scheduledReadaheads.removeFirst();
                scheduleFutures();
            }
            return record;
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted waiting for asynchronous read to complete", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException)e.getCause();
            } else if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException)e.getCause();
            } else {
                throw new RuntimeException("Exception during asynchronous read", e.getCause());
            }
        }
    }

    private void scheduleFutures() {
        if (!asyncEnabled) return;
        if (eosReached) return;
        int batchesToSchedule = mTotalBatches - scheduledReadaheads.size();
        while (batchesToSchedule > 0) {
            CompletableFuture<Deque<U>> readAheadFuture;
            if (scheduledReadaheads.isEmpty()) {
                readAheadFuture = CompletableFuture.supplyAsync(this::readNextBatch, blockingThreadpool);
            } else {
                readAheadFuture = scheduledReadaheads.getLast().thenApplyAsync((x) -> readNextBatch(), blockingThreadpool);
            }
            scheduledReadaheads.addLast(readAheadFuture);
            scheduledTransforms.addLast(readAheadFuture.thenApplyAsync(this::processNextBatch, nonblockingThreadpool));
            batchesToSchedule--;
        }
    }

    private Deque<U> readNextBatch() {
        long bufferConsumed = 0;
        Deque<U> batch = new ArrayDeque<>();
        while (bufferConsumed < mBatchBufferBudget) {
            Tuple<U, Long> ra;
            try {
                ra = performReadAhead(mBatchBufferBudget - bufferConsumed);
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
            bufferConsumed += ra.b;
            batch.addLast(ra.a);
        }
        return batch;
    }

    private Deque<T> processNextBatch(Deque<U> batch) {
        Deque<T> result = new ArrayDeque<>(batch.size());
        while (!batch.isEmpty()) {
            U u = batch.removeFirst();
            if (u == null) {
                eosReached = true;
            }
            T t = u == null ? null : transform(u);
            result.addLast(t);
        }
        return result;
    }
    /**
     * Performs read-ahead on the underlying stream. This task is executed on the blocking
     * thread pool. Only one task executing this method per instance will be scheduled.
     * @return result of performing read-ahead, typically a byte array, and the amount
     * of buffer budget consumed.
     * A null return value is treated as an end of stream indicator and no further read-ahead will be performed.
     */
    public abstract Tuple<U, Long> performReadAhead(long bufferBudget) throws IOException;

    /**
     * Transforms the read-ahead result into the output result.
     * This task is executed on the non-blocking thread pool.
     * Multiple instances of this tasks can be scheduled with no guarantees made
     * regarding execution order.
     * Implementation of this method should be non-blocking with blocking IO performed in
     * performReadAhead().
     * A task executing this method will only be scheduled if an exception did not occur during performReadAhead().
     * @param record read-ahead result.
     * @return result for transforming read-ahead result, typically a decompression operation or a parsed record.
     * A null return value is treated as an end of stream indicator and no further read-ahead will be performed.
     */
    public abstract T transform(U record);
}
