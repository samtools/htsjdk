/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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
import htsjdk.samtools.util.zip.DeflaterFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Parallel implementation of BAM file compression.
 * <p>
 * The main idea is to compress blocks of a BAM file asynchronously. After a next 64K block filled,
 * it sends to the first available processor core, put future result into the buffer and continues to fill a next 64K block of data.
 * When buffer of futures with compressed block is full next writing task will be submitted.
 * <p>
 *
 * @see AbstractBlockCompressedOutputStream
 */
public class ParallelBlockCompressedOutputStream extends AbstractBlockCompressedOutputStream {

    private static final ExecutorService gzipExecutorService = Executors.newFixedThreadPool(
            Defaults.ZIP_THREADS,
            r -> {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setDaemon(true);
                return t;
            }
    );

    private static final Void NOTHING = null;
    private static final int BLOCKS_PACK_SIZE = 16 * Defaults.ZIP_THREADS;

    private DeflaterFactory deflaterFactory;
    private final int compressionLevel;

    List<Future<CompressedBlock>> compressedBlocksInFuture = new ArrayList<>(BLOCKS_PACK_SIZE);
    private CompletableFuture<Void> writeBlocksTask = CompletableFuture.completedFuture(NOTHING);


    public ParallelBlockCompressedOutputStream(final String filename) {
        this(filename, defaultCompressionLevel);
    }

    /**
     * Prepare to compress at the given compression level
     * Note: this constructor uses the default {@link DeflaterFactory}, see {@link #getDefaultDeflaterFactory()}.
     * @param compressionLevel 1 <= compressionLevel <= 9
     */
    public ParallelBlockCompressedOutputStream(final String filename, final int compressionLevel) {
        this(new File(filename), compressionLevel);
    }

    /**
     * Uses default compression level, which is 5 unless changed by setCompressionLevel
     * Note: this constructor uses the default {@link DeflaterFactory}, see {@link #getDefaultDeflaterFactory()}.
     * Use {@link #ParallelBlockCompressedOutputStream(File, int, DeflaterFactory)} to specify a custom factory.
     */
    public ParallelBlockCompressedOutputStream(final File file) {
        this(file, defaultCompressionLevel);
    }

    /**
     * Prepare to compress at the given compression level
     * @param compressionLevel 1 <= compressionLevel <= 9
     * Note: this constructor uses the default {@link DeflaterFactory}, see {@link #getDefaultDeflaterFactory()}.
     * Use {@link #ParallelBlockCompressedOutputStream(File, int, DeflaterFactory)} to specify a custom factory.
     */
    public ParallelBlockCompressedOutputStream(final File file, final int compressionLevel) {
        this(file, compressionLevel, defaultDeflaterFactory);
    }

    /**
     * Prepare to compress at the given compression level
     * @param compressionLevel 1 <= compressionLevel <= 9
     * @param deflaterFactory custom factory to create deflaters (overrides the default)
     */
    public ParallelBlockCompressedOutputStream(final File file, final int compressionLevel, final DeflaterFactory deflaterFactory) {
        super(file);
        this.deflaterFactory = deflaterFactory;
        this.compressionLevel = compressionLevel;
    }

    /**
     * Uses default compression level, which is 5 unless changed by setCompressionLevel
     * Note: this constructor uses the default {@link DeflaterFactory}, see {@link #getDefaultDeflaterFactory()}.
     * Use {@link #ParallelBlockCompressedOutputStream(OutputStream, File, int, DeflaterFactory)} to specify a custom factory.
     *
     * @param file may be null
     */
    public ParallelBlockCompressedOutputStream(final OutputStream os, final File file) {
        this(os, file, defaultCompressionLevel);
    }

    /**
     * Note: this constructor uses the default {@link DeflaterFactory}, see {@link #getDefaultDeflaterFactory()}.
     * Use {@link #ParallelBlockCompressedOutputStream(OutputStream, File, int, DeflaterFactory)} to specify a custom factory.
     */
    public ParallelBlockCompressedOutputStream(final OutputStream os, final File file, final int compressionLevel) {
        this(os, file, compressionLevel, defaultDeflaterFactory);
    }

    /**
     * Creates the output stream.
     * @param os output stream to create a BlockCompressedOutputStream from
     * @param file file to which to write the output or null if not available
     * @param compressionLevel the compression level (0-9)
     * @param deflaterFactory custom factory to create deflaters (overrides the default)
     */
    public ParallelBlockCompressedOutputStream(final OutputStream os, final File file, final int compressionLevel,
                                               DeflaterFactory deflaterFactory) {
        super(os, file);
        this.deflaterFactory = deflaterFactory;
        this.compressionLevel = compressionLevel;
    }

    @Override
    public void flush() throws IOException {
        while (numUncompressedBytes > 0) {
            deflateBlock();
        }
        submitSpillTask();
        writeBlocksTask.join();
        // gzipDeflatingWorkers must be empty then flushing
        codec.getOutputStream().flush();
    }

    /**
     * @see AbstractBlockCompressedOutputStream#getFilePointer();
     * This implementation of get file pointer is blocking, because we have to wait to the end
     * of writing all provided data for getting right pointer.
     */
    @Override
    public long getFilePointer() {
        // we have to wait all writing tasks in other case we risk to get wrong mBlockAddress
        submitSpillTask();
        writeBlocksTask.join();
        return super.getFilePointer();
    }

    /**
     * Submit new deflate task of the current uncompressed byte buffer.
     */
    @Override
    protected int deflateBlock() {
        if (numUncompressedBytes == 0) {
            return 0;
        }
        submitDeflateTask();
        numUncompressedBytes = 0;

        if (compressedBlocksInFuture.size() == BLOCKS_PACK_SIZE) {
            submitSpillTask();
        }

        return 0;
    }

    void submitDeflateTask() {
        UncompressedBlock uncompressedBlock = new UncompressedBlock(
                Arrays.copyOf(this.uncompressedBuffer, numUncompressedBytes),
                numUncompressedBytes
        );
        compressedBlocksInFuture.add(
                CompletableFuture.supplyAsync(new BlockZipper(uncompressedBlock), gzipExecutorService)
        );
    }

    void submitSpillTask() {
        writeBlocksTask.join();
        writeBlocksTask = CompletableFuture.runAsync(new BlocksSpiller(compressedBlocksInFuture), gzipExecutorService);
        compressedBlocksInFuture = new ArrayList<>(BLOCKS_PACK_SIZE);
    }

    /**
     * Inner class which represent task for spilling block which was passed
     * */
    private class BlocksSpiller implements Runnable {

        private List<Future<CompressedBlock>> compressedBlocks;

        BlocksSpiller(List<Future<CompressedBlock>> compressedBlocks) {
            this.compressedBlocks = compressedBlocks;
        }

        @Override
        public void run() {
            for (Future<CompressedBlock> compressedBlockFuture : compressedBlocks) {
                try {
                    CompressedBlock compressedBlock = compressedBlockFuture.get();
                    mBlockAddress += writeGzipBlock(
                            compressedBlock.compressedBuffer,
                            compressedBlock.compressedSize,
                            compressedBlock.bytesToCompress,
                            compressedBlock.crc32Value
                    );
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeIOException("Enable to write Gzip block", e);
                }
            }
        }
    }


    /**
     * Inner class which represent task for zipping block which was passed
     * */
    private class BlockZipper implements Supplier<CompressedBlock> {

        private UncompressedBlock block;

        BlockZipper(UncompressedBlock block) {
            this.block = block;
        }

        @Override
        public CompressedBlock get() {
            return compressBlock(block);

        }

        private CompressedBlock compressBlock(UncompressedBlock uncompressedBlock) {
            final CRC32 crc32 = new CRC32();
            final Deflater deflater = deflaterFactory.makeDeflater(compressionLevel, true);

            final byte[] compressedBuffer = new byte[BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE -
                    BlockCompressedStreamConstants.BLOCK_HEADER_LENGTH];
            // Compress the input
            deflater.setInput(uncompressedBlock.uncompressedBuffer, 0, uncompressedBlock.bytesToCompress);
            deflater.finish();
            int compressedSize = deflater.deflate(compressedBuffer, 0, compressedBuffer.length);

            // If it didn't all fit in uncompressedBuffer.length, set compression level to NO_COMPRESSION
            // and try again.  This should always fit.
            if (!deflater.finished()) {
                final Deflater noCompressionDeflater = deflaterFactory.makeDeflater(Deflater.NO_COMPRESSION, true);
                noCompressionDeflater.setInput(uncompressedBlock.uncompressedBuffer, 0, uncompressedBlock.bytesToCompress);
                noCompressionDeflater.finish();
                compressedSize = noCompressionDeflater.deflate(compressedBuffer, 0, compressedBuffer.length);
                if (!noCompressionDeflater.finished()) {
                    throw new IllegalStateException("unpossible");
                }
            }

            // Data compressed small enough, so write it out.
            crc32.update(uncompressedBlock.uncompressedBuffer, 0, uncompressedBlock.bytesToCompress);
            return new CompressedBlock(compressedBuffer, compressedSize, uncompressedBlock.bytesToCompress, crc32.getValue());
        }

    }

    static class UncompressedBlock {

        byte[] uncompressedBuffer;
        int bytesToCompress;

        UncompressedBlock(byte[] uncompressedBuffer, int bytesToCompress) {
            this.uncompressedBuffer = uncompressedBuffer;
            this.bytesToCompress = bytesToCompress;
        }

    }
    static class CompressedBlock {

        byte[] compressedBuffer;
        int compressedSize;
        int bytesToCompress;
        long crc32Value;

        CompressedBlock(byte[] compressedBuffer, int compressedSize, int bytesToCompress, long crc32Value) {
            this.compressedBuffer = compressedBuffer;
            this.compressedSize = compressedSize;
            this.bytesToCompress = bytesToCompress;
            this.crc32Value = crc32Value;
        }

    }
}
