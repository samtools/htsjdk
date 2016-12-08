/*
 * The MIT License
 *
 * Copyright (c) 2016 Daniel Cameron
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.util;


import htsjdk.samtools.Defaults;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.zip.InflaterFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

/**
 * Asynchronous read-ahead implementation of {@link htsjdk.samtools.util.BlockCompressedInputStream}.   
 * 
 * Note that this implementation is not synchronized. If multiple threads access an instance concurrently, it must be synchronized externally. 
 */
public class AsyncBlockCompressedInputStream extends BlockCompressedInputStream {
    private static final int READ_AHEAD_BUFFERS = (int)Math.ceil(Defaults.NON_ZERO_BUFFER_SIZE / BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE);
    private static final Executor threadpool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setDaemon(true);
                return t;
            }
        });
    /**
     * Next blocks (in stream order) that have already been decompressed. 
     */
    private final BlockingQueue<DecompressedBlock> mResult = new ArrayBlockingQueue<>(READ_AHEAD_BUFFERS);
    /**
     * Buffers used to decompress previous blocks that are no longer in use.
     * These buffers are reused if possible.
     * Note that no blocking occurs on this buffer and a blocking queue is used purely
     * because it is a base library synchronized queue implementation
     * (and Collections.synchronizedQueue() does not exist).
     */
    private final BlockingQueue<byte[]> freeBuffers = new ArrayBlockingQueue<>(READ_AHEAD_BUFFERS);
    /**
     * Indicates whether a read-ahead task has been scheduled to run. Only one read-ahead task
     * per stream can be scheduled at any one time.
     */
    private final Semaphore running = new Semaphore(1);
    /**
     * Indicates whether any scheduled task should abort processing and terminate
     * as soon as possible since the result will be discarded anyway.
     */
    private volatile boolean mAbort = false;

    public AsyncBlockCompressedInputStream(final InputStream stream) {
        super(stream, true);
    }

    public AsyncBlockCompressedInputStream(final InputStream stream, InflaterFactory inflaterFactory) {
        super(stream, true, inflaterFactory);
    }

    public AsyncBlockCompressedInputStream(final File file)
        throws IOException {
        super(file);
    }

    public AsyncBlockCompressedInputStream(final File file, InflaterFactory inflaterFactory)
            throws IOException {
        super(file, inflaterFactory);
    }

    public AsyncBlockCompressedInputStream(final URL url) {
        super(url);
    }

    public AsyncBlockCompressedInputStream(final URL url, InflaterFactory inflaterFactory) {
        super(url, inflaterFactory);
    }

    public AsyncBlockCompressedInputStream(final SeekableStream strm) {
        super(strm);
    }

    public AsyncBlockCompressedInputStream(final SeekableStream strm, InflaterFactory inflaterFactory) {
        super(strm, inflaterFactory);
    }

    @Override
    protected DecompressedBlock nextBlock(byte[] bufferAvailableForReuse) {
        if (bufferAvailableForReuse != null) {
            freeBuffers.offer(bufferAvailableForReuse);
        }
        return nextBlockSync();
    }
    
    @Override
    protected void prepareForSeek() {
        flushReadAhead();
        super.prepareForSeek();
    }

    @Override
    public void close() throws IOException {
        // Suppress interrupts while we close.
        final boolean isInterrupted = Thread.interrupted();
        mAbort = true;
        try {
            flushReadAhead();
            super.close();
        } finally {
            if (isInterrupted) Thread.currentThread().interrupt();
        }
    }
    /**
     * Foreground thread blocking operation that aborts all read-ahead tasks
     * and flushes all read-ahead results.
     */
    private void flushReadAhead() {
        final boolean abortStatus = mAbort;
        mAbort = true;
        try {
            // block until the thread pool operation has completed
            running.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted waiting for decompression thread", e);
        }
        // flush any read-ahead results
        mResult.clear();
        mAbort = abortStatus;
        running.release();
    }
    /**
     * Ensures that a read-ahead task for this stream exists in the thread pool.
     */
    private void ensureReadAhead() {
        if (running.tryAcquire()) {
            tryQueueTask();
        }
    }
    /**
     * Try to queue another read-ahead buffer
     * This method should only be invoked by the owner of the running semaphore
     */
    private void tryQueueTask() {
        if (mAbort) {
            // Potential deadlock between getNextBlock() and flushReadAhead() here
            // This requires seek()/close() and another method to be called
            // at the same time. Since the parent class is not thread-safe
            // this is an acceptable behavior.
            running.release();
            return;
        }
        if (mResult.remainingCapacity() == 0) {
            // read-ahead has already filled the results buffer
            running.release();
            if (mResult.remainingCapacity() > 0) {
                // race condition this second check fixes:
                // - worker thread context switch after checking remaining capacity is zero
                // - foreground thread calls getNextBlock() repeatedly until blocking
                // - worker thread switches back in and releases mutex
                // = foreground blocking on mResult.take(), mutex free, no worker
                // -> try to take back mutex and start worker
                // if that fails, the someone else took the lock and would
                // have started the background worker. (except if flushReadAhead()
                // took the lock with getNextBlock() still blocking: not thread-safe
                // so we don't care)
                ensureReadAhead();
                return;
            } else {
                return;
            }
        }
        // we are able to perform a read-ahead operation
        // ownership of the running mutex is now with the threadpool task
        threadpool.execute(new AsyncBlockCompressedInputStreamRunnable());
    }
    /**
     * Foreground thread blocking operation that retrieves the next read-ahead buffer.
     * Lazy initiation of read-ahead is performed if required.
     * @return next decompressed block in input stream 
     */
    private DecompressedBlock nextBlockSync() {
        ensureReadAhead();
        DecompressedBlock nextBlock;
        try {
            nextBlock = mResult.take();
        } catch (InterruptedException e) {
            return new DecompressedBlock(0, 0, e);
        }
        ensureReadAhead();
        return nextBlock;
    }
    private class AsyncBlockCompressedInputStreamRunnable implements Runnable {
        /**
         * Thread pool operation that fills the read-ahead queue
         */
        @Override
        public void run() {
            final DecompressedBlock decompressed = processNextBlock(freeBuffers.poll());
            if (!mResult.offer(decompressed)) {
                // offer should never block since we never queue a task when the results buffer is full
                running.release(); // safety release to ensure foreground close() does not block indefinitely
                throw new IllegalStateException("Decompression buffer full");
            }
            tryQueueTask();
        }
    }
}
