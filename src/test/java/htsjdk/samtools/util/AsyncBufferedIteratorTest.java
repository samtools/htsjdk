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

import org.testng.Assert;
import org.testng.annotations.Test;

public class AsyncBufferedIteratorTest {
    private static class TestCloseableIterator implements CloseableIterator<Integer> {
        private int[] results;
        private volatile int offset = 0;
        public volatile boolean isClosed = false;
        public TestCloseableIterator(int[] results) {
            this.results = results;
        }
        @Override
        public void close() {
            isClosed = true;
        }
        @Override
        public boolean hasNext() {
            return offset < results.length;
        }
        @Override
        public Integer next() {
            return results[offset++];
        }
        public int consumed() {
            return offset;
        }
    }
    @Test
    public void testWrapUnderlying() {
        AsyncBufferedIterator<Integer> abi = new AsyncBufferedIterator<Integer>(new TestCloseableIterator(new int[] { 0, 1, 2, 3}), 1, 1);
        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(i, (int)abi.next());
        }
        abi.close();
    }
    @Test
    public void testClose() {
        TestCloseableIterator tci = new TestCloseableIterator(new int[] { 0, 1, 2, 3});
        AsyncBufferedIterator<Integer> abi = new AsyncBufferedIterator<Integer>(tci, 1, 1);
        abi.close();
        Assert.assertTrue(tci.isClosed);
    }
    /**
     * Background thread should block when buffers are full
     */
    @Test
    public void testBackgroundBlocks() throws InterruptedException {
        TestCloseableIterator it = new TestCloseableIterator(new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        AsyncBufferedIterator<Integer> abi = new AsyncBufferedIterator<Integer>(it, 3, 2, "testBackgroundBlocks");
        Assert.assertNotNull(getThreadWithName("testBackgroundBlocks"));
        Thread.sleep(10); // how do we write this test and not be subject to race conditions?
        // should have read 9 records: 2*3 in the buffers, and another 3 read but
        // blocking waiting to be added 
        Assert.assertEquals(it.consumed(), 9);
        abi.close();
    }
    @Test
    public void testBackgroundThreadCompletes() throws InterruptedException {
        TestCloseableIterator it = new TestCloseableIterator(new int[] { 0, 1, 2, 3, 4, 5 });
        AsyncBufferedIterator<Integer> abi = new AsyncBufferedIterator<Integer>(it, 3, 2, "testBackgroundThreadCompletes");
        Assert.assertNotNull(getThreadWithName("testBackgroundThreadCompletes"));
        // both buffers should be full
        // clear out one buffer so the background thread can write the end of stream indicator
        // and complete
        abi.next();
        
        // how do we write this test and not be subject to a race condition
        // since we're waiting for a background thread we have no access?
        Thread t;
        for (int i = 0; i < 64; i++) {
            Thread.sleep(1);
            t = getThreadWithName("testBackgroundThreadCompletes");
            if (t == null || !t.isAlive()) break;
        }
        t = getThreadWithName("testBackgroundThreadCompletes");
        Assert.assertTrue(t == null || !t.isAlive());
        abi.close();
    }
    private static Thread getThreadWithName(String name) {
        Thread[] allthreads = new Thread[Thread.activeCount() + 16];
        int threadCount = Thread.enumerate(allthreads);
        for (int i = 0; i < threadCount; i++) {
            String threadName = allthreads[i].getName(); 
            if (name.equals(threadName)) {
                return allthreads[i];
            }
        }
        return null;
    }
}
