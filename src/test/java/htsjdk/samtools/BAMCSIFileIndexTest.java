package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;

public class BAMCSIFileIndexTest extends HtsjdkTest {

    private static DiskBasedBAMFileIndex bai = new DiskBasedBAMFileIndex(new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam.bai"), null);
    private static BAMCSIFileIndex csi = new BAMCSIFileIndex(new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam.csi"), null);
    private static Bin bin1 = new Bin(0, 0);
    private static Bin bin2 = new Bin(0, 1);
    private static Bin bin3 = new Bin(1, 0);
    private static Bin bin4 = new Bin(1, 1);
    private static Bin bin5 = new Bin(1, 9);
    private static Bin bin6 = new Bin(1, 586);
    private static Bin bin7 = new Bin(1, 80);
    private static Bin bin8 = new Bin(1, 4689);



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
        Assert.assertEquals(csi.getNumberOfReferences(), 45);
    }

    @Test
    public static void testGetParentBinOK() {
        Assert.assertEquals(csi.getParentBinNumber(4681), 585);
        Assert.assertEquals(csi.getParentBinNumber(4688), 585);
        Assert.assertEquals(csi.getParentBinNumber(4689), 586);
        Assert.assertEquals(csi.getParentBinNumber(585), 73);
        Assert.assertEquals(csi.getParentBinNumber(592), 73);
        Assert.assertEquals(csi.getParentBinNumber(593), 74);
        Assert.assertEquals(csi.getParentBinNumber(0), 0);
    }

    @Test (expectedExceptions = SAMException.class)
    public static void testGetParentBinFail() {
        csi.getParentBinNumber(37449);
    }

    @Test
    public static void testRegionToBin() {
        Assert.assertEquals(GenomicIndexUtil.regionToBin(12653, 1876491), GenomicIndexUtil.regionToBin(12653, 1876491, 14, 6));
        Assert.assertEquals(GenomicIndexUtil.regionToBin(1048576, 1146880), GenomicIndexUtil.regionToBin(1048576, 1146880, 14, 6));
        Assert.assertEquals(GenomicIndexUtil.regionToBin(536870912, 1073741824), GenomicIndexUtil.regionToBin(536870912, 1073741824, 14, 6));
    }

    @Test
    public static void testGetNoCoorCount() {
        long noCoorBai = bai.getNoCoordinateCount().longValue();
        long noCoorCsi = csi.getNoCoordinateCount().longValue();

        Assert.assertEquals(noCoorBai, 279);
        Assert.assertEquals(noCoorCsi, 279);
    }

    @Test
    public static void testGetLevelForBin() {
        Assert.assertEquals(csi.getLevelForBin(bin1), 0);
        Assert.assertEquals(csi.getLevelForBin(bin2), 1);
        Assert.assertEquals(csi.getLevelForBin(bin3), 0);
        Assert.assertEquals(csi.getLevelForBin(bin4), 1);
        Assert.assertEquals(csi.getLevelForBin(bin5), 2);
        Assert.assertEquals(csi.getLevelForBin(bin6), 4);
        Assert.assertEquals(csi.getLevelForBin(bin7), 3);
        Assert.assertEquals(csi.getLevelForBin(bin8), 5);
    }

    @Test
    public static void testGetParentBinNumber() {
        Assert.assertEquals(csi.getParentBinNumber(bin1), 0);
        Assert.assertEquals(csi.getParentBinNumber(bin2), 0);
        Assert.assertEquals(csi.getParentBinNumber(bin3), 0);
        Assert.assertEquals(csi.getParentBinNumber(bin4), 0);
        Assert.assertEquals(csi.getParentBinNumber(bin5), 1);
        Assert.assertEquals(csi.getParentBinNumber(bin6), 73);
        Assert.assertEquals(csi.getParentBinNumber(bin7), 9);
        Assert.assertEquals(csi.getParentBinNumber(bin8), 586);
    }

    @Test
    public static void testGetFirstLocusInBin() {
        Assert.assertEquals(csi.getFirstLocusInBin(bin1), 1);
        Assert.assertEquals(csi.getFirstLocusInBin(bin2), 1);
        Assert.assertEquals(csi.getFirstLocusInBin(bin3), 1);
        Assert.assertEquals(csi.getFirstLocusInBin(bin4), 1);
        Assert.assertEquals(csi.getFirstLocusInBin(bin5), 1);
        Assert.assertEquals(csi.getFirstLocusInBin(bin6), (1<<17) + 1);
        Assert.assertEquals(csi.getFirstLocusInBin(bin7), (1<<20)*7 + 1);
        Assert.assertEquals(csi.getFirstLocusInBin(bin8), (1<<14)*8 + 1);
    }

    @Test
    public static void testGetLastLocusInBin() {
        Assert.assertEquals(csi.getLastLocusInBin(bin1), 1<<29);
        Assert.assertEquals(csi.getLastLocusInBin(bin2), 1<<26);
        Assert.assertEquals(csi.getLastLocusInBin(bin3), 1<<29);
        Assert.assertEquals(csi.getLastLocusInBin(bin4), 1<<26);
        Assert.assertEquals(csi.getLastLocusInBin(bin5), 1<<23);
        Assert.assertEquals(csi.getLastLocusInBin(bin6), (1<<17)*2);
        Assert.assertEquals(csi.getLastLocusInBin(bin7), (1<<20)*8);
        Assert.assertEquals(csi.getLastLocusInBin(bin8), (1<<14)*9);
    }
}
