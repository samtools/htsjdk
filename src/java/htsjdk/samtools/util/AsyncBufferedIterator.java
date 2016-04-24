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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Iterator that uses a dedicated background thread to perform read-ahead to improve
 * throughput at the expense of increased latency. This iterator will block
 * until the background thread has read a full buffer of records.
 * 
 * Note that this implementation is not synchronized. If multiple threads
 * access an instance concurrently, it must be synchronized externally. 
 * 
 * @author Daniel Cameron
 *
 */
public class AsyncBufferedIterator<T> implements CloseableIterator<T> {
    private static final Log log = Log.getInstance(AsyncBufferedIterator.class);
    private static final AtomicInteger threadsCreated = new AtomicInteger(0);
    private final int bufferSize;
    /**
     * A dedicated background thread is required since these iterators can be chained
     * thus able to block on each other. Usage of a thread pool would result in
     * a deadlock due to task dependencies.
     */
    private Thread backgroundThread;
    private final Iterator<T> underlyingIterator;
    private final BlockingQueue<RecordBlock> buffer;
    private RecordBlock currentBlock = new RecordBlock(Collections.emptyList());

    /**
     * Creates a new iterator that traverses the given iterator on a background
     * thread
     * 
     * @param iterator iterator to traverse
     * @param bufferSize size of read-ahead buffer. A larger size will increase both throughput and latency.
     * Double buffering is used so the maximum number of records on which read-ahead is performed is twice this.
     */
    public AsyncBufferedIterator(final Iterator<T> iterator, final int bufferSize) {
        this(iterator, bufferSize, 1, null);
    }
    
    /**
     * Creates a new iterator that traverses the given iterator on a background
     * thread
     * 
     * @param iterator iterator to traverse
     * @param bufferSize size of each read-ahead buffer. A larger size will increase both throughput and latency.
     * @param bufferCount number of read-ahead buffers
     */
    public AsyncBufferedIterator(final Iterator<T> iterator, final int bufferSize, final int bufferCount) {
        this(iterator, bufferSize, bufferCount, null);
    }

    /**
     * Creates a new iterator that traverses the given iterator on a background
     * thread
     * 
     * @param iterator iterator to traverse
     * @param bufferSize size of each read-ahead buffer. A larger size will increase both throughput and latency.
     * @param bufferCount number of read-ahead buffers
     * @param threadName background thread name. A name will be automatically generated if this parameter is null.
     */
    public AsyncBufferedIterator(final Iterator<T> iterator, final int bufferSize, final int bufferCount, final String threadName) {
        if (iterator == null) throw new IllegalArgumentException("iterator cannot be null");
        if (bufferCount <= 0) throw new IllegalArgumentException("Must use at least 1 buffer.");
        if (bufferSize <= 0) throw new IllegalArgumentException("Buffer size must be at least 1 record.");
        this.underlyingIterator = iterator;
        this.buffer = new ArrayBlockingQueue<>(bufferCount);
        this.bufferSize = bufferSize;
        int threadNumber = threadsCreated.incrementAndGet();
        this.backgroundThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    backgroundRun();
                }
            }, threadName != null ? threadName : getThreadNamePrefix() + threadNumber);
        this.backgroundThread.setDaemon(true);
        log.debug("Starting thread " + this.backgroundThread.getName());
        this.backgroundThread.start();
    }

    protected String getThreadNamePrefix() {
        return AsyncBufferedIterator.class.getSimpleName();
    }

    @Override
    public void close() {
        if (backgroundThread != null) {
            try {
                backgroundThread.interrupt();
                buffer.clear();
                backgroundThread.join();
            } catch (InterruptedException ie) {
                throw new RuntimeException("Interrupted waiting for background thread to complete", ie);
            } finally {
                CloserUtil.close(underlyingIterator);
                backgroundThread = null;
                currentBlock = null;
            }
        }
    }

    @Override
    public boolean hasNext() {
        if (backgroundThread == null) {
            throw new IllegalStateException("iterator has been closed");
        }
        if (currentBlock.hasNext()) return true;
        
        // rethrow any exceptions raised on the background thread
        // at the point the exception would have been encountered
        // if we had performed synchronous iteration
        Throwable t = currentBlock.getException();
        if (t != null) {
            if (t instanceof Error) {
                throw (Error) t;
            } else if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new RuntimeException(t);
            }
        }
        if (currentBlock.isEndOfStream()) return false;
        try {
            // try loading next block
            currentBlock = buffer.take();
        } catch (InterruptedException e) {
            throw new RuntimeException("Error reading from background thread", e);
        }
        return hasNext();
    }

    @Override
    public T next() {
        if (hasNext()) {
            return currentBlock.next();
        }
        throw new NoSuchElementException("next");
    }

    /**
     * Performs 1 buffer worth of read-ahead on the underlying iterator
     * (background thread method) 
     */
    private RecordBlock readAhead() {
        if (!underlyingIterator.hasNext()) return new RecordBlock();
        final List<T> readAhead = new ArrayList<>(bufferSize);
        try {
            for (int i = 0; i < bufferSize && underlyingIterator.hasNext(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    // eager abort if we've been told to stop
                    return new RecordBlock(readAhead, new InterruptedException());
                }
                readAhead.add(underlyingIterator.next());
            }
            return new RecordBlock(readAhead);
        } catch (Throwable t) {
            return new RecordBlock(readAhead, t);
        }
    }
    /**
     * Background thread run loop
     * @throws InterruptedException 
     */
    private void backgroundRun() {
        try {
            RecordBlock block;
            do {
                block = readAhead();
                if (block.getException() instanceof InterruptedException) {
                    // stop thread immediately if we've been told to stop
                    return;
                }
                buffer.put(block);
            } while (!block.isEndOfStream());
        } catch (InterruptedException e) {
            // stop thread
        }
    }
    /**
     * Block of records from the underlying iterator 
     */
    private class RecordBlock implements Iterator<T> {
        private final Throwable exception;
        private final Iterator<T> it;
        public RecordBlock(Iterable<T> it) {
            this.it = it.iterator();
            this.exception = null;
        }

        /**
         * Record block with exception thrown when attempting to retrieve the next record
         * @param it records successfully iterated over
         * @param exception exception thrown when attempting to iterate over the next record
         */
        public RecordBlock(Iterable<T> it, Throwable exception) {
            this.it = it.iterator();
            this.exception = exception;
        }
        
        /**
         * Record block indicating end of stream 
         */
        public RecordBlock() {
            this.it = null;
            this.exception = null;
        }

        @Override
        public boolean hasNext() {
            return it != null && it.hasNext();
        }

        @Override
        public T next() {
            return it.next();
        }
        
        public boolean isEndOfStream() {
            return it == null;
        }
        
        /**
         * Exception thrown when attempting to retrieve records from the underlying stream
         * @return exception thrown on background thread, null if no exception occurred
         */
        public Throwable getException() {
            return exception;
        }
    }
}
