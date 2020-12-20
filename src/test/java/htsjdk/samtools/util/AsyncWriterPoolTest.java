package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;


public class AsyncWriterPoolTest extends HtsjdkTest {
    private static class TestWriter implements WrappedWriter<String> {
        private final BufferedWriter writer;

        public TestWriter(Path file) {
            try {
                this.writer = Files.newBufferedWriter(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
        @Override
        public void write(String item) {
            try {
                this.writer.write(item);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() throws IOException {
           this.writer.close();
        }
    }


    @Test
    public void testWritingToFile() throws IOException {
        AsyncWriterPool<String> pool = new AsyncWriterPool<>(4);
        int fileNum = 8;
        ArrayList<AsyncWriterPool.PooledWriter<String>> writers = new ArrayList<>();
        for (int i = 0; i < fileNum; i++) {
            File file = File.createTempFile(String.format("AsyncPoolWriter_%s", i), ".tmp");
            System.out.println(file.toPath().toString());
            TestWriter writer = new TestWriter(file.toPath());
            writers.add(new AsyncWriterPool.PooledWriter<>(pool, writer, new LinkedBlockingQueue<>(), 100));
        }
        for (int i = 0; i < 8000; i++) {
            for (int j = 0; j < 1000; j++) {
                writers.get(i % 8 ).write(String.format("%s-%s\n", i % 8, j));
            }
        }
        pool.close();
    }

//    @Test
//    public void testNoSelfSuppression() {
//        try (AsyncWriterPool<W> t = new TestAsyncWriter()) {
//            try {
//                t.write(1); // Will trigger exception in writing thread
//                t.write(2); // Will block if the above write has not been executed, but may not trigger checkAndRethrow()
//                t.write(3); // Will trigger checkAndRethrow() if not already done by the above write
//                Assert.fail("Expected exception");
//            } catch (MyException e) {
//                // Pre-bug fix, this was a "Self-suppression not permitted" exception from Java, rather than MyException
//                Assert.assertEquals(1, e.item.intValue());
//            }
//            // Verify that attempts to write after exception will fail
//            try {
//                t.write(4);
//                Assert.fail("Expected exception");
//            } catch (RuntimeIOException e) {
//                // Expected
//            }
//        }
//    }
}
