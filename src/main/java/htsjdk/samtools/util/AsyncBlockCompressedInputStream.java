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
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Asynchronous read-ahead and decompression implementation of {@link htsjdk.samtools.util.BlockCompressedInputStream}.
 * 
 * Note that this implementation is not synchronized. If multiple threads access an instance concurrently, it must be synchronized externally. 
 */
public class AsyncBlockCompressedInputStream extends BlockCompressedInputStream {
    private final ConcurrentLinkedQueue<byte[]> mFreeDecompressedBlockedBuffers = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<byte[]> mFreeCompressedBlockBuffers = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<BlockGunzipper> mFreeInflators = new ConcurrentLinkedQueue<>();
    private final AsyncBlockCompressedInputStreamTaskRunner async = new AsyncBlockCompressedInputStreamTaskRunner(
            BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE,
            Math.max(1, Defaults.NON_ZERO_BUFFER_SIZE / BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE));
    private final InflaterFactory inflatorFactory;

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
    protected CompressionBlock nextBlock(CompressionBlock releasedBlock) throws IOException {
        if (releasedBlock != null &&
                releasedBlock.getUncompressedBlock() != null &&
                releasedBlock.getUncompressedBlock().length > 0) {
            mFreeDecompressedBlockedBuffers.add(releasedBlock.getUncompressedBlock());
        }
        CompressionBlock cb = async.nextRecord();
        return cb;
    }

    @Override
    protected void prepareForSeek() {
        async.disableAsyncProcessing();
        async.flushAsyncProcessing();
        async.enableAsyncProcessing();
        super.prepareForSeek();
    }

    @Override
    public void close() throws IOException {
        async.disableAsyncProcessing();
        async.flushAsyncProcessing();
        super.close();
    }

    private class AsyncBlockCompressedInputStreamTaskRunner extends AsyncReadTaskRunner<CompressionBlock, CompressionBlock> {
        public AsyncBlockCompressedInputStreamTaskRunner(int batchBufferSize, int batches) {
            super(batchBufferSize, batches);
        }
        @Override
        public Tuple<CompressionBlock, Long> performReadAhead(long bufferBudget) throws IOException {
            byte[] compressedBuffer = mFreeCompressedBlockBuffers.poll();
            if (compressedBuffer == null) {
                compressedBuffer = new byte[BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE];
            }
            CompressionBlock block = readNextBlock(compressedBuffer);
            if (block != null) {
                // since the buffer we allocate is BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE
                // we use this as the consumed size as opposed to the actual size block.getBlockCompressedSize()
                return new Tuple<>(block, (long)BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE);
            }
            return null;
        }

        @Override
        public CompressionBlock transform(CompressionBlock record) {
            BlockGunzipper inflator = mFreeInflators.poll();
            if (inflator == null) {
                inflator = new BlockGunzipper(inflatorFactory);
            }
            try {
                record.decompress(mFreeDecompressedBlockedBuffers.poll(), inflator);
            } finally {
                mFreeInflators.add(inflator);
            }
            // compressed block is not needed once we have the decompressed version
            byte[] compressedBlockBuffer = record.getCompressedBlock();
            if (compressedBlockBuffer != null && compressedBlockBuffer.length > 0) {
                mFreeCompressedBlockBuffers.add(compressedBlockBuffer);
            }
            return record;
        }
    }
}
