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

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Iterator that uses a background thread to perform read-ahead to improve
 * throughput at the expense of increased latency.
 * 
 * @author Daniel Cameron
 *
 */
public class AsyncBufferedIterator<T> implements CloseableIterator<T> {
    private static final Log log = Log.getInstance(AsyncBufferedIterator.class);
    /** Count of threads created */
    private static AtomicInteger threadsCreated = new AtomicInteger(0);
    private final Thread reader;
    private final ReaderRunnable readerRunnable;
    private final AtomicReference<Throwable> ex = new AtomicReference<Throwable>(null);
    private final Iterator<T> underlying;
    private final BlockingQueue<List<T>> buffer;
    private final int bufferSize;
    private boolean closeCalled = false;
    /**
     * Current buffer of records returned from the background thread. An empty
     * list indicates the background thread has encountered the end of the
     * underlying stream.
     */
    private List<T> currentList = null;
    private Iterator<T> currentBuffer = null;

    /**
     * Creates a new iterator that traverses the given iterator on a background
     * thread
     * 
     * @param iterator iterator to traverse
     * @param bufferCount number of read-ahead buffers
     * @param bufferSize size of each read-ahead buffer. A larger size will increase both throughput and latency.
     */
    public AsyncBufferedIterator(final Iterator<T> iterator, final int bufferCount, final int bufferSize) {
        this(iterator, null, bufferCount, bufferSize);
    }

    /**
     * Creates a new iterator that traverses the given iterator on a background
     * thread
     * 
     * @param iterator iterator to traverse
     * @param threadName background thread name
     * @param bufferCount number of read-ahead buffers
     * @param bufferSize size of each read-ahead buffer. A larger size will increase both throughput and latency.
     */
    public AsyncBufferedIterator(final Iterator<T> iterator, final String threadName, final int bufferCount, final int bufferSize) {
        if (iterator == null) throw new IllegalArgumentException();
        if (bufferCount <= 0) throw new IllegalArgumentException("Must use at least 1 buffer.");
        if (bufferSize <= 0) throw new IllegalArgumentException("Buffer size must be at least 1 record.");
        this.underlying = iterator;
        this.buffer = new ArrayBlockingQueue<List<T>>(bufferCount);
        this.bufferSize = bufferSize;
        this.readerRunnable = new ReaderRunnable();
        this.reader = new Thread(readerRunnable, threadName == null ? getThreadNamePrefix() + threadsCreated.incrementAndGet() : threadName);
        this.reader.setDaemon(true);
        log.debug("Starting thread " + this.reader.getName());
        this.reader.start();
    }

    protected String getThreadNamePrefix() {
        return "AsyncBufferedIterator";
    }

    @Override
    public void close() {
        closeCalled = true;
        try {
            reader.interrupt();
            // flush buffer so EOS indicator can be written if writer is blocking
            buffer.clear();
            reader.join();
        } catch (InterruptedException ie) {
            throw new RuntimeException("Interrupted waiting for background thread to complete", ie);
        } finally {
            CloserUtil.close(underlying);
        }
    }

    @Override
    public boolean hasNext() {
        throwOnCallingThread();
        if (closeCalled) return false;
        if (currentList != null && currentList.isEmpty()) {
            // encountered end of stream
            return false;
        }
        if (currentList == null || !currentBuffer.hasNext()) {
            try {
                currentList = buffer.take();
                currentBuffer = currentList.iterator();
            } catch (InterruptedException e) {
                throw new RuntimeException("Error reading from background thread", e);
            }
            // rethrow any exceptions raised on the background thread while we
            // were blocking on the next record
            throwOnCallingThread();
        }
        return currentBuffer.hasNext();
    }

    @Override
    public T next() {
        if (hasNext()) {
            return (T) currentBuffer.next();
        }
        throw new NoSuchElementException("next");
    }

    /**
     * Rethrows any exception encountered on the background
     * thread to the caller 
     */
    private final void throwOnCallingThread() {
        final Throwable t = this.ex.get();
        if (t != null) {
            if (t instanceof Error) {
                throw (Error) t;
            } else if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new RuntimeException(t);
            }
        }
    }

    /**
     * Reads the given iterator, passing back records to the calling thread in batches
     */
    private class ReaderRunnable implements Runnable {
        public void run() {
            boolean eosWritten = false;
            try {
                while (underlying.hasNext()) {
                    final List<T> readAhead = new ArrayList<T>(bufferSize);
                    for (int i = 0; i < bufferSize && underlying.hasNext(); i++) {
                        readAhead.add(underlying.next());
                    }
                    buffer.put(readAhead);
                    if (readAhead.isEmpty()) {
                        eosWritten = true;
                    }
                }
            } catch (InterruptedException ie) {
                // log.debug("Thread interrupt received - closing on background thread.");
            } catch (Throwable t) {
                ex.set(t);
                throw new RuntimeException(t);
            } finally {
                Thread.interrupted();
                try {
                    if (!eosWritten) {
                        buffer.put(new ArrayList<T>());
                    }
                } catch (InterruptedException e2) {
                    log.warn("Thread interrupt received whilst writing end of stream indicator");
                }
            }
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    protected String getBackgroundThreadName() {
        return this.reader.getName();
    }
}
