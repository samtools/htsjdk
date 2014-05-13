package htsjdk.samtools.util;

import htsjdk.samtools.util.DiskBackedQueue;
import htsjdk.samtools.util.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collections;

/**
 * Created by bradt on 4/28/14.
 */
public class DiskBackedQueueTest extends SortingCollectionTest {
    /**
     * Generate some strings, put into SortingCollection, confirm that the right number of
     * Strings come out, and in the right order.
     * @param numStringsToGenerate
     * @param maxRecordsInRam
     */
    @Test(dataProvider = "test1")
    public void testPositive(final String testName, final int numStringsToGenerate, final int maxRecordsInRam) {
        final String[] strings = new String[numStringsToGenerate];
        int numStringsGenerated = 0;
        final DiskBackedQueue<String> diskBackedQueue = makeDiskBackedQueue(maxRecordsInRam);
        for (final String s : new RandomStringGenerator(numStringsToGenerate)) {
            diskBackedQueue.add(s);
            strings[numStringsGenerated++] = s;
        }

        Assert.assertEquals(tmpDirIsEmpty(), numStringsToGenerate <= maxRecordsInRam);
        assertIteratorEqualsList(strings, diskBackedQueue);
        Assert.assertEquals(diskBackedQueue.size(), 0);

    }

    private DiskBackedQueue<String> makeDiskBackedQueue(final int maxRecordsInRam) {
        return DiskBackedQueue.newInstance(new StringCodec(), maxRecordsInRam, Collections.singletonList(tmpDir));
    }

}
