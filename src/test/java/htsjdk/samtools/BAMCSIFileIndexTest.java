package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.SeekableStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

public class BAMCSIFileIndexTest extends HtsjdkTest {

    private static final File BAM_FILE = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/9211_1#49.bam");
    private static BAMCSIFileIndex csi = new BAMCSIFileIndex(new File(BAM_FILE.getPath() + ".csi"), null, 14, 6);


    @Test
    public static void testGetNumIndexLevels() {
        Assert.assertEquals(csi.getBinDepth(), 6);
    }

    @Test
    public static void testGetFirstBinInLevelOK() {
        Assert.assertEquals(csi.getFirstBinOnLevel(0), 0);
        Assert.assertEquals(csi.getFirstBinOnLevel(1), 1);
        Assert.assertEquals(csi.getFirstBinOnLevel(2), 9);
        Assert.assertEquals(csi.getFirstBinOnLevel(3), 73);
        Assert.assertEquals(csi.getFirstBinOnLevel(4), 585);
        Assert.assertEquals(csi.getFirstBinOnLevel(5), 4681);
    }

    @Test (expectedExceptions = SAMException.class)
    public static void testGetFirstBinInLevelFail() {
        csi.getFirstBinOnLevel(6);
    }

    @Test
    public static void testGetLevelSizeOK() {
        Assert.assertEquals(csi.getLevelSize(0), 1);
        Assert.assertEquals(csi.getLevelSize(1), 8);
        Assert.assertEquals(csi.getLevelSize(2), 64);
        Assert.assertEquals(csi.getLevelSize(3), 512);
        Assert.assertEquals(csi.getLevelSize(4), 4096);
        Assert.assertEquals(csi.getLevelSize(5), 32768);
    }

    @Test (expectedExceptions = SAMException.class)
    public static void testGetLevelSizeFail() {

        csi.getLevelSize(6);
    }
}
