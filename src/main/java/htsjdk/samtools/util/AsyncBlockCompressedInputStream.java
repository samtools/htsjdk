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
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asynchronous read-ahead and decompression implementation of {@link htsjdk.samtools.util.BlockCompressedInputStream}.
 * 
 * Note that this implementation is not synchronized. If multiple threads access an instance concurrently, it must be synchronized externally. 
 */
public class AsyncBlockCompressedInputStream extends BlockCompressedInputStream {
    private static final int READ_AHEAD_BUFFERS = (int)Math.ceil(Defaults.NON_ZERO_BUFFER_SIZE / BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE);
    /**
     * Decompressed blocks ordered by their stream position.
     * Async background tasks should not touch this data structure.
     */
    private final PriorityQueue<CompressionBlock> mOrderedResult = new PriorityQueue<>(
            Comparator.<CompressionBlock>comparingLong(cb -> cb.getBlockAddress())
                // If we somehow read the same block twice (e.g. EOF), preference the one with actual content
                .thenComparing(Comparator.<CompressionBlock>comparingInt(cb -> cb.getBlockCompressedSize()).reversed()));
    /**
     * Blocks that have been asynchronously decompressed but not yet processed on the foreground thread
     */
    private final BlockingQueue<CompressionBlock> mResult = new ArrayBlockingQueue<>(READ_AHEAD_BUFFERS);
    /**
     * Stream position of decompression blocks. This is used to ensure that the order that the blocks
     * are returned by {@link #nextBlock(CompressionBlock) nextBlock} matches the order of the input stream
     * even when the blocks are decompressed out of order.
     */
    private final BlockingQueue<Long> mBlockStreamPosition = new ArrayBlockingQueue<>(READ_AHEAD_BUFFERS);
    private final ConcurrentLinkedQueue<byte[]> mFreeDecompressedBlockedBuffers = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<byte[]> mFreeCompressedBlockBuffers = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<BlockGunzipper> mFreeInflators = new ConcurrentLinkedQueue<>();
    private final AtomicLong mOutstandingTasks = new AtomicLong(0);
    private final AtomicLong mReadAheadBuffersInUse = new AtomicLong(0);
    private volatile boolean mReadTaskActive = false;
    private final InflaterFactory inflatorFactory;
    /**
     * Indicates whether new asynchronous tasks should be scheduled
     */
    private volatile boolean mAsyncActive = true;

    public AsyncBlockCompressedInputStream(final InputStream stream) {
        this(stream, BlockGunzipper.getDefaultInflaterFactory());
    }

    public AsyncBlockCompressedInputStream(final InputStream stream, InflaterFactory inflaterFactory) {
        super(stream, true, inflaterFactory);
        this.inflatorFactory = inflaterFactory;
    }

    public AsyncBlockCompressedInputStream(final File file) throws IOException {
        this(file, BlockGunzipper.getDefaultInflaterFactory());
    }

    public AsyncBlockCompressedInputStream(final File file, InflaterFactory inflaterFactory) throws IOException {
        super(file, inflaterFactory);
        this.inflatorFactory = inflaterFactory;
    }

    public AsyncBlockCompressedInputStream(final URL url) {
        this(url, BlockGunzipper.getDefaultInflaterFactory());
    }

    public AsyncBlockCompressedInputStream(final URL url, InflaterFactory inflaterFactory) {
        super(url, inflaterFactory);
        this.inflatorFactory = inflaterFactory;
    }

    public AsyncBlockCompressedInputStream(final SeekableStream strm) {
        this(strm, BlockGunzipper.getDefaultInflaterFactory());
    }

    public AsyncBlockCompressedInputStream(final SeekableStream strm, InflaterFactory inflaterFactory) {
        super(strm, inflaterFactory);
        this.inflatorFactory = inflaterFactory;
    }

    @Override
    protected CompressionBlock nextBlock(CompressionBlock releasedBlock) {
        if (releasedBlock != null &&
                releasedBlock.getUncompressedBlock() != null &&
                releasedBlock.getUncompressedBlock().length > 0) {
            mFreeDecompressedBlockedBuffers.add(releasedBlock.getUncompressedBlock());
        }
        asyncEnsureReadAhead();
        CompressionBlock cb;
        try {
            cb = takeNextBlock();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted reading from asynchronous input stream", e);
        }
        if (cb.getException() == null) {
            asyncEnsureReadAhead();
        }
        return cb;
    }

    private CompressionBlock takeNextBlock() throws InterruptedException {
        if (mReadAheadBuffersInUse.get() == 0) {
            throw new IllegalStateException("Attempt to read from async stream when asynchronous processing is not active.");
        }
        long targetPosition = mBlockStreamPosition.take();
        while (mOrderedResult.isEmpty() || mOrderedResult.peek().getBlockAddress() != targetPosition) {
            CompressionBlock cb = mResult.take();
            mOrderedResult.add(cb);
        }
        CompressionBlock correctBlock = mOrderedResult.remove();
        mReadAheadBuffersInUse.decrementAndGet();
        return correctBlock;
    }

    @Override
    protected void prepareForSeek() {
        flushReadAhead();
        mAsyncActive = true;
        super.prepareForSeek();
    }

    @Override
    public void close() throws IOException {
        // Suppress interrupts while we close.
        final boolean isInterrupted = Thread.interrupted();
        mAsyncActive = false;
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
        final boolean abortStatus = mAsyncActive;
        mAsyncActive = false;
        // enter the synchronized block to ensure that any background read tasks that are in-flight are recorded as such
        asyncEnsureReadAhead();
        try {
            // wait for all the async tasks to complete
            while(mOutstandingTasks.get() > 0) {
                mResult.take();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted waiting for decompression thread", e);
        }
        assert(mOutstandingTasks.get() == 0);
        assert(!mReadTaskActive);
        mResult.clear();
        mBlockStreamPosition.clear();
        mOrderedResult.clear();
        mReadAheadBuffersInUse.set(0);
        // restore abort status
        // prepareForSeek() needs to abort async processing but the stream is still valid
        mAsyncActive = abortStatus;
    }

    public synchronized void asyncEnsureReadAhead() {
        if (mAsyncActive && !mReadTaskActive && mReadAheadBuffersInUse.get() < READ_AHEAD_BUFFERS) {
            mReadTaskActive = true;
            mOutstandingTasks.incrementAndGet();
            mReadAheadBuffersInUse.incrementAndGet();
            AsynchronousIOThreadPools.getBlockingThreadpool().execute(new ReadRunnable());
        }
    }
    private void asyncReadNextBlock() {
        byte[] buffer = mFreeCompressedBlockBuffers.poll();
        boolean decompressionQueued = false;
        if (buffer == null || buffer.length < BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE) {
            buffer = new byte[BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE];
        }
        CompressionBlock block = null;
        try {
            block = readNextBlock(buffer);
            // we need to add the expected block position immediately (before the readNextBlock() is called again)
            // to ensure we return results in the correct order
            mBlockStreamPosition.add(block.getBlockAddress());
            mReadTaskActive = false;
            if (block.getException() == null) {
                AsynchronousIOThreadPools.getNonBlockingThreadpool().execute(new DecompressRunnable(block));
                decompressionQueued = true;
                asyncEnsureReadAhead();
            }
        } catch (Exception e) {
            if (block != null) {
                block = new CompressionBlock(block.getBlockAddress(), block.getBlockCompressedSize(), e);
            } else {
                block = new CompressionBlock(Long.MAX_VALUE, 0, e);
            }
        } finally {
            if (block.getException() != null) {
                // stop processing once we get an exception
                mAsyncActive = false;
            }
            if (!decompressionQueued) {
                mResult.add(block);
                mOutstandingTasks.decrementAndGet();
            }
        }
    }
    private void asyncDecompress(CompressionBlock block) {
        try {
            if (block.getException() == null) {
                BlockGunzipper inflator = mFreeInflators.poll();
                if (inflator == null) {
                    inflator = new BlockGunzipper(inflatorFactory);
                }
                block.decompress(mFreeDecompressedBlockedBuffers.poll(), inflator);
                mFreeInflators.add(inflator);
                // compressed block is not needed once we have the decompressed version
                byte[] compressedBlockBuffer = block.getCompressedBlock();
                if (compressedBlockBuffer != null && compressedBlockBuffer.length > 0) {
                    mFreeCompressedBlockBuffers.add(compressedBlockBuffer);
                }
            }
        } catch (Exception e) {
            block = new CompressionBlock(block.getBlockAddress(), block.getBlockCompressedSize(), e);
        } finally {
            if (block.getException() != null) {
                // stop processing once we get an exception
                mAsyncActive = false;
            }
            // we always have enough room to store the decompression result since our buffer size matches
            // the maximum number of blocks we will read ahead
            mResult.add(block);
            mOutstandingTasks.decrementAndGet();
        }
    }
    private class DecompressRunnable implements Runnable {
        private final CompressionBlock block;
        public DecompressRunnable(CompressionBlock block) {
            this.block = block;
        }
        @Override
        public void run() {
            asyncDecompress(block);
        }
    }
    private class ReadRunnable implements Runnable {
        @Override
        public void run() {
            asyncReadNextBlock();
        }
    }
}
