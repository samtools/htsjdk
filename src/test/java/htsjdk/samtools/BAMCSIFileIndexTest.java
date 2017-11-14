package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.SeekableStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

public class BAMCSIFileIndexTest extends HtsjdkTest {

    private static final File BAM_FILE = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    private static BAMCSIFileIndex csi = new BAMCSIFileIndex(new File(BAM_FILE.getPath() + ".csi"), null);


    @Test
    public static void testGetNumIndexLevels() {
        Assert.assertEquals(csi.getBinDepth(), 6);
    }
    @Test
    public static void testGetMinShift() { Assert.assertEquals(csi.getMinShift(), 14);
    }
    @Test
    public static void testGetMaxBins() {
        Assert.assertEquals(csi.getMaxBins(), 37449);
    }
    @Test
    public static void testGetMaxSpan() {Assert.assertEquals(csi.getMaxSpan(),512*1024*1024);}

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

    @Test
    public static void testGetAuxData() {
        Assert.assertEquals(csi.getAuxData().length, 0);
    }

    @Test
    public static void testGetNReferences() {
        Assert.assertEquals(csi.getnReferences(), 45);
    }

    @Test
    public static void testGetParentBin() {
        Assert.assertEquals(csi.getParentBinNumber(4681), 585);
        Assert.assertEquals(csi.getParentBinNumber(4688), 585);
        Assert.assertEquals(csi.getParentBinNumber(4689), 586);
        Assert.assertEquals(csi.getParentBinNumber(585), 73);
        Assert.assertEquals(csi.getParentBinNumber(592), 73);
        Assert.assertEquals(csi.getParentBinNumber(593), 74);
    }

    @Test
    public static void testIndexArraySize() {
        Assert.assertEquals(csi.sequenceIndexes.length, csi.getnReferences() + 1);
    }

    @Test
    public static void testRegionToBin() {
        Assert.assertEquals(GenomicIndexUtil.regionToBin(12653, 1876491), GenomicIndexUtil.regionToBin(12653, 1876491, 14, 6));
        Assert.assertEquals(GenomicIndexUtil.regionToBin(1048576, 1146880), GenomicIndexUtil.regionToBin(1048576, 1146880, 14, 6));
        Assert.assertEquals(GenomicIndexUtil.regionToBin(536870912, 1073741824), GenomicIndexUtil.regionToBin(536870912, 1073741824, 14, 6));
    }
}
