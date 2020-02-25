package htsjdk.samtools.util.async;

import htsjdk.samtools.model.CompressedBlock;
import htsjdk.samtools.util.Log;

import java.util.concurrent.*;
import java.util.function.Consumer;

public class WriteConsumer {

    private static final Log log = Log.getInstance(WriteConsumer.class);
    private static final CompressedBlock poisonPill = new CompressedBlock();

    private final BlockingQueue<CompressedBlock> resultQueue;
    private final ExecutorService writerExecutor;

    public WriteConsumer(Consumer<CompressedBlock> consumer, int queueCapacity) {
        this.resultQueue = new ArrayBlockingQueue<>(1000);
        this.writerExecutor = Executors.newSingleThreadExecutor();

        writerExecutor.execute(new WritingTask(resultQueue, consumer));
        writerExecutor.shutdown();
    }

    public void close() throws InterruptedException {
        resultQueue.put(poisonPill);
        writerExecutor.shutdownNow();
    }

    public void put(Future<CompressedBlock> t) throws InterruptedException {
        try {
            resultQueue.put(t.get());
        } catch (ExecutionException e) {
            throw new RuntimeException("WriteConsumer.put()", e);
        }
    }

    private static class WritingTask implements Runnable {

        private final BlockingQueue<CompressedBlock> futuresQueue;
        private final Consumer<CompressedBlock> consumer;

        private WritingTask(BlockingQueue<CompressedBlock> futuresQueue, Consumer<CompressedBlock> consumer) {
            this.futuresQueue = futuresQueue;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            boolean isNotInterrupted = true;
            while (isNotInterrupted) {
                try {
                    CompressedBlock compressedBlock = futuresQueue.take();
                    if (compressedBlock == poisonPill) {
                        break;
                    }

                    consumer.accept(compressedBlock);
                } catch (InterruptedException e) {
                    isNotInterrupted = false;
                    log.error(e, "WritingTask was interrupted");
                }
            }
        }
    }
}