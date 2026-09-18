package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AbstractBAMFileIndexTest extends HtsjdkTest {

    private static final AbstractBAMFileIndex afi = new DiskBasedBAMFileIndex(
            Path.of("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam.bai"), null);

    @Test
    public static void testGetNumIndexLevels() {
        Assert.assertEquals(AbstractBAMFileIndex.getNumIndexLevels(), 6);
    }

    @Test
    public static void testGetFirstBinInLevelOK() {
        Assert.assertEquals(AbstractBAMFileIndex.getFirstBinInLevel(0), 0);
        Assert.assertEquals(AbstractBAMFileIndex.getFirstBinInLevel(1), 1);
        Assert.assertEquals(AbstractBAMFileIndex.getFirstBinInLevel(2), 9);
        Assert.assertEquals(AbstractBAMFileIndex.getFirstBinInLevel(3), 73);
        Assert.assertEquals(AbstractBAMFileIndex.getFirstBinInLevel(4), 585);
        Assert.assertEquals(AbstractBAMFileIndex.getFirstBinInLevel(5), 4681);
    }

    @Test(expectedExceptions = SAMException.class)
    public static void testGetFirstBinInLevelFail() {
        AbstractBAMFileIndex.getFirstBinInLevel(6);
    }

    @Test
    public static void testGetLevelSizeOK() {
        Assert.assertEquals(afi.getLevelSize(0), 1);
        Assert.assertEquals(afi.getLevelSize(1), 8);
        Assert.assertEquals(afi.getLevelSize(2), 64);
        Assert.assertEquals(afi.getLevelSize(3), 512);
        Assert.assertEquals(afi.getLevelSize(4), 4096);
        Assert.assertEquals(afi.getLevelSize(5), 32768);
    }

    @Test(expectedExceptions = SAMException.class)
    public static void testGetLevelSizeFail() {
        afi.getLevelSize(6);
    }
}
