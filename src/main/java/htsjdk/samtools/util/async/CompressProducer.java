package htsjdk.samtools.util.async;

import htsjdk.samtools.model.CompressedBlock;
import htsjdk.samtools.model.TempBlock;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.GZIIndex;
import htsjdk.samtools.util.Log;

import java.util.concurrent.*;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public class CompressProducer {

    private static final Log log = Log.getInstance(CompressProducer.class);
    private static final TempBlock poisonPill = new TempBlock();

    private final BlockingQueue<TempBlock> resultQueue;
    private final ExecutorService compressorExecutor;
    private final CompressingTask compressingTask;

    public CompressProducer(Deflater deflater, Deflater noCompressionDeflater, CRC32 crc32, GZIIndex.GZIIndexer indexer,
                            BinaryCodec codec) {
        int compressingThreads = Runtime.getRuntime().availableProcessors();
        int queueCapacity = compressingThreads * 2;
        this.resultQueue = new ArrayBlockingQueue<>(queueCapacity);
        this.compressorExecutor = Executors.newSingleThreadExecutor();

        compressingTask = new CompressingTask(resultQueue, deflater, noCompressionDeflater, crc32, indexer, codec,
                queueCapacity, compressingThreads);
        compressorExecutor.execute(compressingTask);
    }

    public void close() throws InterruptedException {
        compressingTask.close();
        compressorExecutor.shutdown();
    }

    public void put(TempBlock block) throws InterruptedException {
        resultQueue.put(block);
    }

    private static class CompressingTask implements Runnable {

        private final BlockingQueue<TempBlock> resultQueue;
        private final WriteConsumer writeConsumer;
        private final ExecutorService compressorExecutor;

        private final Deflater deflater;
        private final Deflater noCompressionDeflater;

        private final CRC32 crc32;
        private final GZIIndex.GZIIndexer indexer;
        private final BinaryCodec codec;
        private long mBlockAddress = 0;

        private CompressingTask(BlockingQueue<TempBlock> resultQueue, Deflater deflater, Deflater noCompressionDeflater, CRC32 crc32,
                                GZIIndex.GZIIndexer indexer, BinaryCodec codec, int queueCapacity, int nThreads) {
            this.resultQueue = resultQueue;
            this.deflater = deflater;
            this.noCompressionDeflater = noCompressionDeflater;
            this.crc32 = crc32;
            this.indexer = indexer;
            this.codec = codec;

            compressorExecutor = Executors.newFixedThreadPool(nThreads);
            writeConsumer = makeWriteConsumer(queueCapacity);
        }

        public void close() throws InterruptedException {
            resultQueue.put(poisonPill);
            writeConsumer.close();

            compressorExecutor.shutdown();
            try {
                if (!compressorExecutor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    compressorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                compressorExecutor.shutdownNow();
            }
        }

        private WriteConsumer makeWriteConsumer(int queueCapacity) {
            return new WriteConsumer(this::writeCompressedBlock, queueCapacity);
        }

        private void writeCompressedBlock(CompressedBlock compressedBlock) {
            final int bytesToCompress = compressedBlock.getBytesToCompress();
            final int totalBlockSize = BlockCompressedOutputStream.writeGzipBlock(compressedBlock.getCompressedSize(), bytesToCompress,
                    crc32.getValue(),
                    compressedBlock.getCompressedBuffer(), codec);

            // Call out to the indexer if it exists
            if (indexer != null) {
                indexer.addGzipBlock(mBlockAddress, bytesToCompress);
            }
            mBlockAddress += totalBlockSize;
        }

        private CompressedBlock compressBlock(TempBlock block) {
            return BlockCompressedOutputStream.deflateBlock(deflater, noCompressionDeflater, crc32, block.getCompressedBuffer(),
                    block.getBytesToCompress(), block.getUncompressedBuffer());
        }

        @Override
        public void run() {
            boolean isNotInterrupted = true;
            while (isNotInterrupted) {
                try {
                    TempBlock block = resultQueue.take();
                    if (block == poisonPill) {
                        break;
                    }

                    writeConsumer.put(compressorExecutor.submit(() -> compressBlock(block)));
                } catch (InterruptedException e) {
                    isNotInterrupted = false;
                    log.error(e, "CompressingTask was interrupted");
                }
            }
        }
    }
}