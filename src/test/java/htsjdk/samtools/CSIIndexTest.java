package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.BitSet;
import java.util.Iterator;

public class CSIIndexTest extends HtsjdkTest {

    private static DiskBasedBAMFileIndex bai = new DiskBasedBAMFileIndex(new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam.bai"), null);
    private static CSIIndex csi = new CSIIndex(new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam.csi"), false, null);
    private static CSIIndex ucsi = new CSIIndex(Paths.get("src/test/resources/htsjdk/samtools/BAMFileIndexTest/uncompressed_index.bam.csi"), true, null);
    private static DiskBasedBAMFileIndex ubai = new DiskBasedBAMFileIndex(new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/uncompressed_index.bam.bai"),null);

    private static Bin bin1 = new Bin(0, 0);
    private static Bin bin2 = new Bin(0, 1);
    private static Bin bin3 = new Bin(1, 0);
    private static Bin bin4 = new Bin(1, 1);
    private static Bin bin5 = new Bin(1, 9);
    private static Bin bin6 = new Bin(1, 586);
    private static Bin bin7 = new Bin(1, 80);
    private static Bin bin8 = new Bin(1, 4689);
    private static Bin bin9 = new Bin(1, 135853);
    private static Bin bin10 = new Bin(1, 163);


    @Test
    public static void testGetNumIndexLevels() {
        Assert.assertEquals(csi.getBinDepth(), 6);
        Assert.assertEquals(ucsi.getBinDepth(), 7);
    }
    @Test
    public static void testGetMinShift() {
        Assert.assertEquals(csi.getMinShift(), 14);
        Assert.assertEquals(ucsi.getMinShift(), 12);
    }
    @Test
    public static void testGetMaxBins() {
        Assert.assertEquals(csi.getMaxBins(), 37449);
        Assert.assertEquals(ucsi.getMaxBins(), 299593);
    }
    @Test
    public static void testGetMaxSpan() {
        Assert.assertEquals(csi.getMaxSpan(),512*1024*1024);
        Assert.assertEquals(ucsi.getMaxSpan(),1024*1024*1024);
    }

    @Test
    public static void testGetFirstBinInLevelOK() {
        Assert.assertEquals(csi.getFirstBinOnLevel(0), 0);
        Assert.assertEquals(csi.getFirstBinOnLevel(1), 1);
        Assert.assertEquals(csi.getFirstBinOnLevel(2), 9);
        Assert.assertEquals(csi.getFirstBinOnLevel(3), 73);
        Assert.assertEquals(csi.getFirstBinOnLevel(4), 585);
        Assert.assertEquals(csi.getFirstBinOnLevel(5), 4681);

        Assert.assertEquals(ucsi.getFirstBinOnLevel(0), 0);
        Assert.assertEquals(ucsi.getFirstBinOnLevel(1), 1);
        Assert.assertEquals(ucsi.getFirstBinOnLevel(2), 9);
        Assert.assertEquals(ucsi.getFirstBinOnLevel(3), 73);
        Assert.assertEquals(ucsi.getFirstBinOnLevel(4), 585);
        Assert.assertEquals(ucsi.getFirstBinOnLevel(5), 4681);
        Assert.assertEquals(ucsi.getFirstBinOnLevel(6), 37449);
    }

    @Test (expectedExceptions = SAMException.class)
    public static void testGetFirstBinInLevelFail1() {
        csi.getFirstBinOnLevel(6);
    }

    @Test (expectedExceptions = SAMException.class)
    public static void testGetFirstBinInLevelFail2() {
        ucsi.getFirstBinOnLevel(7);
    }

    @Test
    public static void testGetLevelSizeOK() {
        Assert.assertEquals(csi.getLevelSize(0), 1);
        Assert.assertEquals(csi.getLevelSize(1), 8);
        Assert.assertEquals(csi.getLevelSize(2), 64);
        Assert.assertEquals(csi.getLevelSize(3), 512);
        Assert.assertEquals(csi.getLevelSize(4), 4096);
        Assert.assertEquals(csi.getLevelSize(5), 32768);

        Assert.assertEquals(ucsi.getLevelSize(0), 1);
        Assert.assertEquals(ucsi.getLevelSize(1), 8);
        Assert.assertEquals(ucsi.getLevelSize(2), 64);
        Assert.assertEquals(ucsi.getLevelSize(3), 512);
        Assert.assertEquals(ucsi.getLevelSize(4), 4096);
        Assert.assertEquals(ucsi.getLevelSize(5), 32768);
        Assert.assertEquals(ucsi.getLevelSize(6), 262144);
    }

    @Test (expectedExceptions = SAMException.class)
    public static void testGetLevelSizeFail1() {
        csi.getLevelSize(6);
    }

    @Test (expectedExceptions = SAMException.class)
    public static void testGetLevelSizeFail12() {
        csi.getLevelSize(7);
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

        Assert.assertEquals(ucsi.getLevelForBin(bin1), 0);
        Assert.assertEquals(ucsi.getLevelForBin(bin2), 1);
        Assert.assertEquals(ucsi.getLevelForBin(bin3), 0);
        Assert.assertEquals(ucsi.getLevelForBin(bin4), 1);
        Assert.assertEquals(ucsi.getLevelForBin(bin5), 2);
        Assert.assertEquals(ucsi.getLevelForBin(bin6), 4);
        Assert.assertEquals(ucsi.getLevelForBin(bin7), 3);
        Assert.assertEquals(ucsi.getLevelForBin(bin8), 5);
        Assert.assertEquals(ucsi.getLevelForBin(bin9), 6);
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

        Assert.assertEquals(ucsi.getParentBinNumber(bin1), 0);
        Assert.assertEquals(ucsi.getParentBinNumber(bin2), 0);
        Assert.assertEquals(ucsi.getParentBinNumber(bin3), 0);
        Assert.assertEquals(ucsi.getParentBinNumber(bin4), 0);
        Assert.assertEquals(ucsi.getParentBinNumber(bin5), 1);
        Assert.assertEquals(ucsi.getParentBinNumber(bin6), 73);
        Assert.assertEquals(ucsi.getParentBinNumber(bin7), 9);
        Assert.assertEquals(ucsi.getParentBinNumber(bin8), 586);
        Assert.assertEquals(ucsi.getParentBinNumber(bin9), 16981);
        Assert.assertEquals(ucsi.getParentBinNumber(16981), 2122);
        Assert.assertEquals(ucsi.getParentBinNumber(2122), 265);
        Assert.assertEquals(ucsi.getParentBinNumber(265), 33);
        Assert.assertEquals(ucsi.getParentBinNumber(33), 4);
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

        Assert.assertEquals(ucsi.getFirstLocusInBin(bin1), 1);
        Assert.assertEquals(ucsi.getFirstLocusInBin(bin2), 1);
        Assert.assertEquals(ucsi.getFirstLocusInBin(bin3), 1);
        Assert.assertEquals(ucsi.getFirstLocusInBin(bin4), 1);
        Assert.assertEquals(ucsi.getFirstLocusInBin(bin5), 1);
        Assert.assertEquals(ucsi.getFirstLocusInBin(bin6), (1<<18) + 1);
        Assert.assertEquals(ucsi.getFirstLocusInBin(bin7), (1<<21)*7 + 1);
        Assert.assertEquals(ucsi.getFirstLocusInBin(bin8), (1<<15)*8 + 1);
        Assert.assertEquals(ucsi.getFirstLocusInBin(bin9), (1<<12)*98404 + 1);
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

        Assert.assertEquals(ucsi.getLastLocusInBin(bin1), 1<<30);
        Assert.assertEquals(ucsi.getLastLocusInBin(bin2), 1<<27);
        Assert.assertEquals(ucsi.getLastLocusInBin(bin3), 1<<30);
        Assert.assertEquals(ucsi.getLastLocusInBin(bin4), 1<<27);
        Assert.assertEquals(ucsi.getLastLocusInBin(bin5), 1<<24);
        Assert.assertEquals(ucsi.getLastLocusInBin(bin6), (1<<18)*2);
        Assert.assertEquals(ucsi.getLastLocusInBin(bin7), (1<<21)*8);
        Assert.assertEquals(ucsi.getLastLocusInBin(bin8), (1<<15)*9);
        Assert.assertEquals(ucsi.getLastLocusInBin(bin9), (1<<12)*98405);
    }

    @Test
    public static void testRegionToBins() {
        BitSet bs1 = GenomicIndexUtil.regionToBins((1<<12)*98403 + 4094, (1<<12)*98404 + 1, 12, 7 );
        Assert.assertTrue(bs1.get(bin9.getBinNumber()-1)); //135852
        Assert.assertTrue(bs1.get(bin9.getBinNumber())); //135853
        Assert.assertTrue(bs1.get(ucsi.getParentBinNumber(bin9.getBinNumber()))); //16981

        BitSet bs2 = GenomicIndexUtil.regionToBins(939520000, 939529000, 12, 7 );
        Assert.assertTrue(bs2.get(0));
        Assert.assertTrue(bs2.get(7));
        Assert.assertTrue(bs2.get(8));
        Assert.assertTrue(bs2.get(64));
        Assert.assertTrue(bs2.get(65));
        Assert.assertTrue(bs2.get(520));
        Assert.assertTrue(bs2.get(521));
        Assert.assertTrue(bs2.get(4168));
        Assert.assertTrue(bs2.get(4169));
        Assert.assertTrue(bs2.get(33352));
        Assert.assertTrue(bs2.get(33353));
        Assert.assertTrue(bs2.get(266823));
        Assert.assertTrue(bs2.get(266824));
        Assert.assertTrue(bs2.get(266825));
        Assert.assertTrue(bs2.get(266826));
    }

    @Test
    public static void testGetSpanOverlapping() {
        BAMFileSpan bfs1 = ucsi.getSpanOverlapping(1, 939520000, 939529000);
        BAMFileSpan bfs2 = ucsi.getSpanOverlapping(1, 240000000, 249228250);
        BAMFileSpan bfs3 = ubai.getSpanOverlapping(1, 240000000, 249228250);

        Assert.assertTrue(bfs1.isEmpty());
        Assert.assertEquals(bfs2.getChunks(), bfs3.getChunks());

        BAMFileSpan bfs4 = ucsi.getSpanOverlapping(bin10);
        Assert.assertEquals(bfs4.getChunks().size(), 3);
    }
}
