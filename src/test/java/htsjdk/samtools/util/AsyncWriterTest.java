/*
 * The MIT License
 *
 * Copyright (c) 2016 Len Trigg
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

import htsjdk.HtsjdkTest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AsyncWriterTest extends HtsjdkTest {
    private static class MyException extends RuntimeException {
        final Integer item;

        public MyException(Integer item) {
            this.item = item;
        }
    }

    private static class TestAsyncWriter extends AbstractAsyncWriter<Integer> {
        protected TestAsyncWriter() {
            super(1); // Queue size of 1 to give us more control over the order of events
        }

        @Override
        protected String getThreadNamePrefix() {
            return "TestAsyncWriter";
        }

        @Override
        protected void synchronouslyWrite(Integer item) {
            throw new MyException(item);
        }

        @Override
        protected void synchronouslyClose() {
            // Nothing
        }
    }

    @Test
    public void testNoSelfSuppression() {
        try (TestAsyncWriter t = new TestAsyncWriter()) {
            try {
                t.write(1); // Will trigger exception in writing thread
                t.write(2); // Will block if the above write has not been executed, but may not trigger
                // checkAndRethrow()
                t.write(3); // Will trigger checkAndRethrow() if not already done by the above write
                Assert.fail("Expected exception");
            } catch (MyException e) {
                // Pre-bug fix, this was a "Self-suppression not permitted" exception from Java, rather than MyException
                Assert.assertEquals(1, e.item.intValue());
            }
            // Verify that attempts to write after exception will fail
            try {
                t.write(4);
                Assert.fail("Expected exception");
            } catch (RuntimeIOException e) {
                // Expected
            }
        }
    }

    /** A writer that keeps what it is given, and fails on one item if asked to. */
    private static class CollectingAsyncWriter extends AbstractAsyncWriter<Integer> {
        final List<Integer> written = new ArrayList<>();
        final Integer failOn;

        CollectingAsyncWriter(final Integer failOn) {
            super(DEFAULT_QUEUE_SIZE);
            this.failOn = failOn;
        }

        @Override
        protected String getThreadNamePrefix() {
            return "CollectingAsyncWriter";
        }

        @Override
        protected void synchronouslyWrite(final Integer item) {
            if (item.equals(failOn)) throw new MyException(item);
            written.add(item);
        }

        @Override
        protected void synchronouslyClose() {
            // Nothing
        }
    }

    @Test
    public void testItemsAreWrittenInTheOrderGiven() {
        final CollectingAsyncWriter writer = new CollectingAsyncWriter(null);
        final List<Integer> items = IntStream.range(0, 100_000).boxed().collect(Collectors.toList());
        items.forEach(writer::write);
        writer.close();
        Assert.assertEquals(writer.written, items);
    }

    @Test
    public void testItemsThatDoNotFillABatchAreWrittenOnClose() {
        final CollectingAsyncWriter writer = new CollectingAsyncWriter(null);
        writer.write(1);
        writer.write(2);
        writer.write(3);
        writer.close();
        Assert.assertEquals(writer.written, List.of(1, 2, 3));
    }

    /** A writer whose writing thread fails on the first item it is given, and so stops. */
    private static class FailOnFirstItemAsyncWriter extends AbstractAsyncWriter<Integer> {
        static final String THREAD_NAME_PREFIX = "FailOnFirstItemAsyncWriter";

        FailOnFirstItemAsyncWriter() {
            super(DEFAULT_QUEUE_SIZE);
        }

        @Override
        protected String getThreadNamePrefix() {
            return THREAD_NAME_PREFIX;
        }

        @Override
        protected void synchronouslyWrite(final Integer item) {
            throw new MyException(item);
        }

        @Override
        protected void synchronouslyClose() {
            // Nothing
        }
    }

    @Test
    public void testAFailureInTheWritingThreadIsThrownWithinOneBatchOfWrites() throws InterruptedException {
        // Items go over in batches of a quarter of the queue size
        final int batchSize = AbstractAsyncWriter.DEFAULT_QUEUE_SIZE / 4;
        final FailOnFirstItemAsyncWriter writer = new FailOnFirstItemAsyncWriter();
        final Thread writingThread = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith(FailOnFirstItemAsyncWriter.THREAD_NAME_PREFIX))
                .findFirst()
                .orElseThrow();

        // Hand over one batch. The writing thread fails on its first item and stops; the write that hands the batch
        // over may already see that.
        try {
            for (int i = 0; i < batchSize; i++) {
                writer.write(i);
            }
        } catch (final MyException e) {
            Assert.assertEquals(e.item.intValue(), 0);
            return;
        }
        writingThread.join(30_000);
        Assert.assertFalse(writingThread.isAlive(), "the writing thread did not stop after failing");

        final MyException thrown = Assert.expectThrows(MyException.class, () -> {
            for (int i = 0; i < batchSize; i++) {
                writer.write(i);
            }
        });
        Assert.assertEquals(thrown.item.intValue(), 0);
    }
}
