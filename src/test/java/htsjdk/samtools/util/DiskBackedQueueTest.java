/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;

public class DiskBackedQueueTest extends SortingCollectionTest {
    @DataProvider(name = "diskBackedQueueProvider")
    public Object[][] createDBQTestData() {
        return new Object[][] {
                {"empty", 0, 100},
                {"singleton", 1, 100},
                {"no ram records", 10, 0},
                {"less than threshold", 100, 200},
                {"threshold minus 1", 99, 100},
                {"greater than threshold", 550, 100},
                {"threshold multiple", 600, 100},
                {"threshold multiple plus one", 101, 100},
                {"exactly threshold", 100, 100},
        };
    }

    @BeforeMethod void setup() { resetTmpDir(); }
    @AfterMethod void tearDown() { resetTmpDir(); }

    /**
     * Generate some strings, put into SortingCollection, confirm that the right number of
     * Strings come out, and in the right order.
     * @param numStringsToGenerate
     * @param maxRecordsInRam
     */
    @Test(dataProvider = "diskBackedQueueProvider")
    public void testPositive(final String testName, final int numStringsToGenerate, final int maxRecordsInRam) {
        final String[] strings = new String[numStringsToGenerate];
        int numStringsGenerated = 0;
        final DiskBackedQueue<String> diskBackedQueue = makeDiskBackedQueue(maxRecordsInRam);
        for (final String s : new RandomStringGenerator(numStringsToGenerate)) {
            diskBackedQueue.add(s);
            strings[numStringsGenerated++] = s;
        }
        Assert.assertEquals(tmpDirIsEmpty(), numStringsToGenerate <= maxRecordsInRam);
        assertQueueEqualsList(strings, diskBackedQueue);
        Assert.assertEquals(diskBackedQueue.canAdd(), numStringsToGenerate <= maxRecordsInRam);
        Assert.assertEquals(diskBackedQueue.size(), 0);
        Assert.assertTrue(diskBackedQueue.isEmpty());
        Assert.assertEquals(diskBackedQueue.poll(), null);
        diskBackedQueue.clear();
        Assert.assertTrue(diskBackedQueue.canAdd());
    }

    private void assertQueueEqualsList(final String[] strings, final DiskBackedQueue<String> diskBackedQueue) {
        int i = 0;
        while (!diskBackedQueue.isEmpty()) {
            final String s = diskBackedQueue.poll();
            Assert.assertEquals(s, strings[i]);
            i++;
        }
        Assert.assertEquals(i, strings.length);
    }

    private DiskBackedQueue<String> makeDiskBackedQueue(final int maxRecordsInRam) {
        return DiskBackedQueue.newInstance(new StringCodec(), maxRecordsInRam, Collections.singletonList(tmpDir()));
    }

    @Test
    public void testReadOnlyQueueJustBeforeReadingFromDisk() {
        final DiskBackedQueue<String> queue = makeDiskBackedQueue(2);
        queue.add("foo");
        queue.add("bar");
        queue.add("baz");
        Assert.assertEquals("foo", queue.poll());
        Assert.assertEquals("bar", queue.poll());

        // Spilled-to-disk records have not been read yet, but one has been loaded into headRecord, so the queue is
        // closed for enqueue-ing.
        Assert.assertFalse(queue.canAdd());
        Assert.assertEquals("baz", queue.poll());

        Assert.assertEquals(queue.size(), 0);
        Assert.assertTrue(queue.isEmpty());
        Assert.assertEquals(queue.poll(), null);
        queue.clear();
        Assert.assertTrue(queue.canAdd());
    }

    /** See: https://github.com/broadinstitute/picard/issues/327 */
    @Test(expectedExceptions = IllegalStateException.class)
    public void testPathologyIssue327() {

        final DiskBackedQueue<String> queue = makeDiskBackedQueue(2);

        // testing a particular order of adding to the queue, setting the result state, and emitting.
        queue.add("0");
        queue.add("1");
        queue.add("2"); // spills to disk
        Assert.assertEquals(queue.poll(), "0"); // gets from ram, so now there is space in ram, but a record on disk
        queue.add("3"); // adds, but we assumed we added all records before removing them
        Assert.assertEquals(queue.poll(), "1");
        Assert.assertEquals(queue.poll(), "2");
        Assert.assertEquals(queue.poll(), "3");
    }
}
