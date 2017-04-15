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
                t.write(2); // Will block if the above write has not been executed, but may not trigger checkAndRethrow()
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
}
