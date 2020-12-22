package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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
                throw new RuntimeIOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            this.writer.close();
        }
    }

    /**
     * Helper method to check if list is sorted
     *
     * @param list input array
     * @return true if sorted, false otherwise
     */
    private static boolean isSorted(List<Integer> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            if (list.get(i) > list.get(i + 1)) {
                return false;
            }
        }
        return true;
    }


    @Test
    public void testWritingToFile() throws IOException {
        AsyncWriterPool<String> pool = new AsyncWriterPool<>(4);
        int fileNum = 8;
        ArrayList<File> files = new ArrayList<>();
        ArrayList<AsyncWriterPool.PooledWriter<String>> writers = new ArrayList<>();
        ArrayList<Iterator<Integer>> streams = new ArrayList<>();
        for (int i = 0; i < fileNum; i++) {
            File file = File.createTempFile(String.format("AsyncPoolWriter_%s", i), ".tmp");
            TestWriter writer = new TestWriter(file.toPath());
            files.add(file);
            writers.add(new AsyncWriterPool.PooledWriter<>(pool, writer, new LinkedBlockingQueue<>(), 15));
            streams.add(Stream.iterate(0, val -> val + 1).iterator());
        }

        // Write batches of 10 integers at a team to each filehandle using a stream for each file to generate
        // sequential integers
        for (int i = 0; i < fileNum * 10; i++) {
            for (int j = 0; j < 10; j++) {
                writers.get(i % fileNum).write(String.format("%s\n", streams.get(i % fileNum).next()));
            }
        }
        pool.close();

        // Verify that values wrote in order and in full
        for (int i = 0; i < fileNum; i++) {
            File file = files.get(i);
            List<Integer> lines = IOUtil.slurpLines(file).stream().map(Integer::parseInt).collect(Collectors.toList());
            assert AsyncWriterPoolTest.isSorted(lines);
            assert lines.size() == 100;
        }
    }

    @Test
    public void testNoSelfSuppression() throws IOException {

        AsyncWriterPool<String> pool = new AsyncWriterPool<>(4);
        File file = File.createTempFile("AsyncPoolWriterTest", ".tmp");
        TestWriter writer = new TestWriter(file.toPath());
        AsyncWriterPool.PooledWriter<String> pooledWriter = new AsyncWriterPool.PooledWriter<>(pool, writer, new LinkedBlockingQueue<>(), 1); // NB: buffsize must be 1 to make tests work
        writer.close(); // Close the inner writer so an exception will be thrown when a thread trys to write to it
        try {
            pooledWriter.write("Exception"); // Will trigger exception in writing thread
            pooledWriter.close(); // Will flush writer and check errors
            Assert.fail("Expected exception");
        } catch (Exception e) {
            // expected
        }
        // Verify that attempts to closed pool writer will fail
        try {
            pooledWriter.write("Third Exception");
            Assert.fail("Expected exception");
        } catch (RuntimeIOException e) {
            // expected
        }
    }
}
