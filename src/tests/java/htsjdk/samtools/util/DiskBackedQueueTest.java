package htsjdk.samtools.util;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;

/**
 * Created by bradt on 4/28/14.
 */
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
        return DiskBackedQueue.newInstance(new StringCodec(), maxRecordsInRam, Collections.singletonList(tmpDir));
    }

}
