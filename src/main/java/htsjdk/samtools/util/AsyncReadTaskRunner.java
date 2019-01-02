package htsjdk.samtools.util;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
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
    private final long mTotalBufferBudget;
    private final int mBatchBufferBudget;
    private final AtomicLong mAvailableBufferBudget;
    private boolean mReadAheadEnabled = true;
    private volatile AtomicLong mReadAheadRecordIndex = new AtomicLong();
    private int mOutstandingBatches;
    private long mNextRecordRecordIndex = 0;
    private final ReadRunnable mReadTask = new ReadRunnable();
    private final PriorityQueue<AsyncTransformRecord> mRecords = new PriorityQueue<>(Comparator.comparingLong(r -> r.recordIndex));
    private final BlockingQueue<Collection<AsyncTransformRecord>> mUnorderedRecords = new LinkedBlockingDeque<>();
    // used by foreground thread to indicate whether we have already reached the end of the underlying stream
    private boolean finalRecordReturned = false;

    /**
     * @param minBatchBufferBudget minimum batch size
     * @param minTotalBufferBudget minimum total buffer consumption
     */
    public AsyncReadTaskRunner(int minBatchBufferBudget, int minTotalBufferBudget) {
        if (minBatchBufferBudget <= 0) {
            throw new IllegalArgumentException("Buffer size must be greater than zero");
        }
        if (minTotalBufferBudget <= 0) {
            throw new IllegalArgumentException("Buffer size must be greater than zero");
        }
        this.mBatchBufferBudget = minBatchBufferBudget;
        this.mTotalBufferBudget = minTotalBufferBudget;
        this.mAvailableBufferBudget = new AtomicLong(mTotalBufferBudget);
    }
    /**
     * Stops async processing. No new async tasks will be scheduled.
     * This method blocks until all tasks already in-flight are completed.
     * The results of these tasks are discarded and any exceptions raised
     * during processing of these tasks are swallowed.
     */
    public void disableAsyncProcessing() {
        // Suppress interrupts while we close.
        final boolean isInterrupted = Thread.interrupted();
        try {
            // wait for all the async tasks to complete
            int outstanding;
            synchronized (mReadTask) {
                mReadAheadEnabled = false; // prevent any additional read ahead tasks
                outstanding = mOutstandingBatches;
            }
            while (outstanding > 0) {
                mUnorderedRecords.take();
                synchronized (mReadTask) {
                    assert(mOutstandingBatches > 0);
                    outstanding = --mOutstandingBatches;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted waiting for decompression thread", e);
        }
        // we have to wait for the read task to actually complete
        // it could have processed the record, queued the transforms task,
        // the transform task completed, but the read task not have actually finished yet
        boolean readTaskActive;
        int sleepTime = 0;
        synchronized (mReadTask) {
            readTaskActive = mReadTask.mIsActive;
        }
        while (readTaskActive) {
            try {
                Thread.sleep(sleepTime++);
            } catch (InterruptedException e) {
                break;
            }
            synchronized (mReadTask) {
                readTaskActive = mReadTask.mIsActive;
            }
        }
        assert(mUnorderedRecords.isEmpty());
        mNextRecordRecordIndex = 0;
        mAvailableBufferBudget.set(mTotalBufferBudget);
        mReadAheadRecordIndex.set(0);
        mRecords.clear();
        if (isInterrupted) Thread.currentThread().interrupt();
    }

    /**
     * Enables async processing. Asynchronous tasks are not actually scheduled
     * until nextRecord() is called for the first time.
     */
    public void enableAsyncProcessing() {
        finalRecordReturned = false;
        synchronized (mReadTask) {
            mReadAheadEnabled = true;
        }
    }
    public void startAsyncProcessing() {
        mReadTask.tryScheduleNextReadAheadTask(false);
    }

    /**
     * Returns the next record. Any exceptions encountered during the background
     * processing of this record are rethrown here.
     * If an exception is thrown async processing will be disabled and subsequent
     * calls will throw an IllegalStateException until enableAsyncProcess() is called.
     * Upon an exception being thrown, no guarantees are made about the position of the underlying stream/iterator.
     * @return next record. null indicates end of stream
     */
    private AsyncTransformRecord lastRecord = null;
    public T nextRecord() throws IOException {
        if (finalRecordReturned) {
            return null;
        }
        AsyncTransformRecord record = mRecords.peek();
        if (record != null && record.recordIndex == mNextRecordRecordIndex) {
            record = mRecords.remove();
            mNextRecordRecordIndex++;
        } else {
            mReadTask.tryScheduleNextReadAheadTask(false);
            record = waitUntil(mNextRecordRecordIndex++);
        }
        lastRecord = record;
        mAvailableBufferBudget.addAndGet(record.bufferBudgetUsed);
        finalRecordReturned = record.transformedRecord == null || record.exception != null;
        rethrowAsyncIOBackgroundThreadException(record);
        mReadTask.tryScheduleNextReadAheadTask(false);
        return record.transformedRecord;
    }

    private AsyncTransformRecord waitUntil(long recordIndex) {
        assert(!finalRecordReturned); // will block indefinitely if there's no more records
        if (!mRecords.isEmpty() && mRecords.peek().recordIndex < recordIndex) {
            throw new IllegalArgumentException("Async waiting must match record ordering of the underlying stream/iterator");
        }
        try {
            while (mRecords.isEmpty() || mRecords.peek().recordIndex != recordIndex) {
                Collection<AsyncTransformRecord> r = mUnorderedRecords.take();
                synchronized (mReadTask) {
                    assert(mOutstandingBatches > 0);
                    mOutstandingBatches--;
                }
                mRecords.addAll(r);
                if (!mRecords.isEmpty() && mRecords.peek().recordIndex < recordIndex) {
                    throw new IllegalArgumentException("Async waiting must match record ordering of the underlying stream/iterator");
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted waiting for decompression thread", e);
        }
        return mRecords.remove();
    }

    /**
     * Rethrows an exception encountered during decompression
     * @throws IOException
     */
    private void rethrowAsyncIOBackgroundThreadException(AsyncTransformRecord record) throws IOException {
        if (record != null && record.exception != null) {
            mReadAheadEnabled = false;
            if (record.exception instanceof IOException) {
                throw (IOException) record.exception;
            } else if (record.exception instanceof RuntimeException) {
                throw (RuntimeException) record.exception;
            } else {
                throw new RuntimeException(record.exception);
            }
        }
    }

    /**
     * Performs read-ahead on the underlying stream. This task is executed on the blocking
     * thread pool. Only one task executing this method per instance will be scheduled.
     * @return result of performing read-ahead, typically a byte array, and the amount
     * of buffer budget consumed.
     * A null return value is treated as an end of stream indicator and no further read-ahead will be performed.
     */
    public abstract Tuple<U, Integer> performReadAhead(int bufferBudget) throws IOException;

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
    private class AsyncReadRecord {
        private final long recordIndex;
        private final int bufferBudgetUsed;
        private final U rawRecord;
        private final Exception exception;
        public AsyncReadRecord(int bufferUsed, U record, Exception exception) {
            this.recordIndex = mReadAheadRecordIndex.getAndIncrement();
            this.bufferBudgetUsed = bufferUsed;
            this.rawRecord = record;
            this.exception = exception;
        }
    }
    private class AsyncTransformRecord {
        private final long recordIndex;
        private final int bufferBudgetUsed;
        private final T transformedRecord;
        private final Exception exception;
        public AsyncTransformRecord(AsyncReadRecord record, T transformedRecord, Exception exception) {
            this.recordIndex = record.recordIndex;
            this.bufferBudgetUsed = record.bufferBudgetUsed;
            this.transformedRecord = transformedRecord;
            this.exception = record.exception == null ? exception : record.exception;
        }
    }
    private class TransformRunnable implements Runnable {
        private List<AsyncReadRecord> records;
        public TransformRunnable(List<AsyncReadRecord> records) {
            this.records = records;
        }
        @Override
        public void run() {
            try {
                List<AsyncTransformRecord> out = new ArrayList<>(records.size());
                for (AsyncReadRecord r : records) {
                    try {
                        if (r.exception != null || r.rawRecord == null) {
                            out.add(new AsyncTransformRecord(r, null, null));
                            break;
                        } else {
                            out.add(new AsyncTransformRecord(r, transform(r.rawRecord), null));
                        }
                    } catch (Exception e) {
                        out.add(new AsyncTransformRecord(r, null, e));
                        break;
                    }
                }
                mUnorderedRecords.add(out);
            } catch (Exception e) {
                log.error(e, "Error during asynchronous record precessing.");
            }
        }
    }
    private AsyncReadRecord nextReadAhead() {
        try {
            Tuple<U, Integer> result = performReadAhead((int)mAvailableBufferBudget.get());
            if (result == null) {
                result = new Tuple<>(null, 0);
            }
            return new AsyncReadRecord(result.b, result.a, null);
        } catch (Exception e) {
            return new AsyncReadRecord(0, null, e);
        }
    }
    private class ReadRunnable implements Runnable {
        private boolean mIsActive = false;
        public boolean tryScheduleNextReadAheadTask(boolean calledFromReadTask) {
            synchronized (mReadTask) {
                if (calledFromReadTask) {
                    mIsActive = false;
                }
                if (!mReadAheadEnabled) return false;
                if (mIsActive) return false;
                if (mAvailableBufferBudget.get() <= 0) return false;
                mIsActive = true;
                mOutstandingBatches++;
            }
            if (!calledFromReadTask) {
                // Reuse the same task if possible
                getBlockingThreadpool().execute(this);
            }
            return true;
        }
        @Override
        public void run() {
            try {
                boolean readAgain = true;
                while (readAgain) {
                    List<AsyncReadRecord> batch = new ArrayList<>();
                    try {
                        int batchRemaining = mBatchBufferBudget;
                        while (mReadAheadEnabled && batchRemaining > 0) {
                            AsyncReadRecord r = nextReadAhead();
                            batch.add(r);
                            batchRemaining -= r.bufferBudgetUsed;
                            mAvailableBufferBudget.addAndGet(-r.bufferBudgetUsed);
                            if (r.exception != null || r.rawRecord == null) {
                                // stop upon exception or EOF
                                synchronized (mReadTask) {
                                    mReadAheadEnabled = false;
                                }
                                readAgain = false;
                            }
                        }
                    } finally {
                        getNonBlockingThreadpool().execute(new TransformRunnable(batch));
                        readAgain = tryScheduleNextReadAheadTask(true) & readAgain;
                    }
                }
            } catch (Exception e) {
                log.error(e, "Error performing asynchronous read.");
            }
        }
    }
}
