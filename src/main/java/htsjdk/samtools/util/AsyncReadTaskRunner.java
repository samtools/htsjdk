package htsjdk.samtools.util;

import htsjdk.samtools.Defaults;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Helper class for performing asynchronous reading.
 * This class handles the asynchronous task scheduling.
 * Asynchronous tasks are split into single blocking read ahead task (performReadAhead())
 * and any number of non-blocking transform tasks (transform()).
 * Synchronisation of the underlying stream/iterator is handled by ensuring that performReadAhead() is not executed
 * by more than one thread concurrently.
 * As this represents a serial bottleneck limiting parallelization,
 * implementations should ensure that as much computation work (such as decompression or record parsing) as possible
 * is performed in transform().
 */
public abstract class AsyncReadTaskRunner<T, U> {
    private static final Log log = Log.getInstance(AsyncReadTaskRunner.class);

    private static Executor nonblockingThreadpool = Executors.newFixedThreadPool(Defaults.ASYNC_READ_COMPUTATIONAL_THREADS, r -> {
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
    private final BlockingDeque<CompletableFuture<Deque<RecordOrException<U>>>> scheduledReadaheads = new LinkedBlockingDeque<>();
    private final BlockingDeque<CompletableFuture<Deque<RecordOrException<T>>>> scheduledTransforms = new LinkedBlockingDeque<>();
    private final int mTotalBatches;
    private final int mBatchBufferBudget;
    private volatile boolean asyncEnabled = true;
    private volatile boolean interruptAsyncTasks = false;
    private volatile boolean eosReached = false;

    /**
     * @param batchBufferBudget buffer budget per batch in bytes.
     * Records will be included batch together until the total size of the batch equals or exceeds this threshold.
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
     * Stops async processing.
     * No new async tasks will be scheduled.
     * Existing async tasks are unaffected.
     */
    public void disableAsyncProcessing() {
        asyncEnabled = false;
    }

    /**
     * Stops async processing and discards the results from any
     * in-flight or completed asynchronous processing.
     *
     * This method blocks until all tasks already in-flight are completed.
     * The results of these tasks are discarded and any exceptions raised
     * during processing of these tasks are swallowed.
     */
    public synchronized void flushAsyncProcessing() {
        interruptAsyncTasks = true;
        disableAsyncProcessing();
        while (!scheduledTransforms.isEmpty()) {
            try {
                CompletableFuture<Deque<RecordOrException<T>>> task = scheduledTransforms.removeFirst();
                task.get();
            } catch (InterruptedException | ExecutionException | RuntimeException e) {
              log.warn(e);
            }
        }
        while(!scheduledReadaheads.isEmpty()) {
            try {
                CompletableFuture<Deque<RecordOrException<U>>> task = scheduledReadaheads.removeFirst();
                task.get();
            } catch (InterruptedException | ExecutionException | RuntimeException e) {
                log.warn(e);
            }
        }
        interruptAsyncTasks = false;
    }

    /**
     * Enables async processing. Asynchronous tasks are not actually scheduled
     * until nextRecord() is called for the first time.
     */
    public void enableAsyncProcessing() {
        asyncEnabled = true;
        eosReached = false;
    }

    /**
     * Enable async processing and schedules read-ahead tasks.
     */
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
    public synchronized T nextRecord() throws IOException {
        if (scheduledTransforms.isEmpty()) {
            scheduleFutures();
        }
        if (scheduledTransforms.isEmpty()) {
            if (eosReached) {
                return null;
            }
            throw new IllegalStateException("No async processing");
        }
        Deque<RecordOrException<T>> batch = null;
        try {
            // block until we have a result
            batch = scheduledTransforms.getFirst().get();
        } catch (InterruptedException | ExecutionException e) {
            raiseAsynchronousProcessingException(e);
        }
        if (batch.isEmpty()) {
            throw new IllegalStateException("Async processing returned zero records");
        }
        RecordOrException<T> record = batch.removeFirst();
        if (record.exception != null) {
            asyncEnabled = false;
        }
        if (batch.isEmpty()) {
            // end of batch
            scheduledTransforms.removeFirst();
            scheduledReadaheads.removeFirst();
            scheduleFutures();
        }
        if (record.exception != null) {
            raiseAsynchronousProcessingException(record.exception);
        }
        return record.record;
    }

    private void raiseAsynchronousProcessingException(Throwable e) throws IOException {
        if (e instanceof InterruptedException ){
            throw new RuntimeException("Interrupted waiting for asynchronous read to complete", e);
        } else if (e instanceof ExecutionException) {
            raiseAsynchronousProcessingException(e.getCause());
        } else if (e instanceof IOException) {
            throw (IOException)e;
        } else if (e instanceof RuntimeException) {
            throw (RuntimeException)e;
        } else {
            throw new RuntimeException("Exception during asynchronous read", e);
        }
    }

    private void scheduleFutures() {
        if (!asyncEnabled) return;
        if (eosReached) return;
        int batchesToSchedule = mTotalBatches - scheduledReadaheads.size();
        while (batchesToSchedule > 0) {
            CompletableFuture<Deque<RecordOrException<U>>> readAheadFuture;
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

    private Deque<RecordOrException<U>> readNextBatch() {
        long bufferConsumed = 0;
        Deque<RecordOrException<U>> batch = new ArrayDeque<>();
        if (eosReached) {
            batch.addLast(new RecordOrException((U)null));
        } else {
            while (bufferConsumed < mBatchBufferBudget && !eosReached) {
                Tuple<U, Long> ra;
                try {
                    if (interruptAsyncTasks) {
                        throw new InterruptedException();
                    }
                    ra = performReadAhead(mBatchBufferBudget - bufferConsumed);
                    if (ra == null || ra.a == null) {
                        eosReached = true;
                    }
                    batch.addLast(new RecordOrException(ra == null ? null : ra.a));
                    if (ra == null || ra.b == 0 || ra.b == null) {
                        // performReadAhead() keeps returning 0 to us, we'll never exit
                        break; // safety exit to ensure we are guaranteed to finish the batch
                    }
                    bufferConsumed += ra.b;
                } catch (IOException | InterruptedException | RuntimeException e) {
                    batch.addLast(new RecordOrException(e));
                    break;
                }
            }
        }
        return batch;
    }

    private Deque<RecordOrException<T>> processNextBatch(Deque<RecordOrException<U>> batch) {
        Deque<RecordOrException<T>> result = new ArrayDeque<>(batch.size());
        while (!batch.isEmpty()) {
            RecordOrException<U> u = batch.removeFirst();
            if (u.exception != null) {
                result.addLast(new RecordOrException<>(u.exception));
                return result;
            }
            try {
                if (interruptAsyncTasks) {
                    throw new InterruptedException();
                }
                T t = u.record == null ? null : transform(u.record);
                result.addLast(new RecordOrException<>(t));
            } catch (InterruptedException | RuntimeException e) {
                result.addLast(new RecordOrException<>(e));
                return result;
            }
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
     * A task executing this method will only be scheduled if an exception did not occur during performReadAhead()
     * and performReadAhead() returned a non-null record.
     * @param record read-ahead result.
     * @return result for transforming read-ahead result, typically a decompression operation or a parsed record.
     * A null return value is treated as an end of stream indicator and no further read-ahead will be performed.
     */
    public abstract T transform(U record);

    private static class RecordOrException<T> {
        private final T record;
        private final Exception exception;

        public RecordOrException(T record) {
            this.record = record;
            this.exception = null;
        }
        public RecordOrException(Exception e) {
            this.record = null;
            this.exception = e;
        }
    }
}
