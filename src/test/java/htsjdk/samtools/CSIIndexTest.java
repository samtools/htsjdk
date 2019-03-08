package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

public class CSIIndexTest extends HtsjdkTest {

    public static final String TEST_DATA_DIR = "src/test/resources/htsjdk/samtools/BAMFileIndexTest/";
    private static DiskBasedBAMFileIndex bai;
    private static CSIIndex csi;
    private static CSIIndex mcsi;
    private static CSIIndex ucsi;
    private static DiskBasedBAMFileIndex ubai;

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

    // The CSI index is a generalization of the bai index which introduces these as parameters instead of hard coding them.
    // These are equivalent parameter values for CSI that are baked into the bai calculations.
    // CSI calculations with these values should match the bai calculations exactly.
    public static final int BAI_EQUIVALENT_MIN_SHIFT = 14;
    public static final int BAI_EQUIVALENT_BIN_DEPTH = 6;

    @BeforeTest
    public void init() {
        bai = new DiskBasedBAMFileIndex(new File(TEST_DATA_DIR, "index_test.bam.bai"), null);
        csi = new CSIIndex(new File(TEST_DATA_DIR, "index_test.bam.csi"), false, null);
        mcsi = new CSIIndex(new File(TEST_DATA_DIR, "index_test.bam.csi"), true, null);
        try {
            ucsi = new CSIIndex(Paths.get(TEST_DATA_DIR, "uncompressed_index.bam.csi"), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        ubai = new DiskBasedBAMFileIndex(new File(TEST_DATA_DIR, "uncompressed_index.bam.bai"), null);
    }


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
        Assert.assertEquals(csi.getMaxSpan(), 512 * 1024 * 1024);
        Assert.assertEquals(ucsi.getMaxSpan(), 1024 * 1024 * 1024);
    }

    @DataProvider
    public Object[][] getTestFirstBinInLevelData() {
        return new Object[][]{
                {csi, 0, 0},
                {csi, 1, 1},
                {csi, 2, 9},
                {csi, 3, 73},
                {csi, 4, 585},
                {csi, 5, 4681},

                {ucsi, 0, 0},
                {ucsi, 1, 1},
                {ucsi, 2, 9},
                {ucsi, 3, 73},
                {ucsi, 4, 585},
                {ucsi, 5, 4681},
                {ucsi, 6, 37449}
        };
    }

    @Test(dataProvider = "getTestFirstBinInLevelData")
    public static void testGetFirstBinInLevelOK(CSIIndex index, int levelNumber, int expected) {
        Assert.assertEquals(index.getFirstBinInLevelForCSI(levelNumber), expected);
    }

    @Test(expectedExceptions = SAMException.class)
    public static void testGetFirstBinInLevelFail1() {
        csi.getFirstBinInLevelForCSI(6);
    }

    @Test(expectedExceptions = SAMException.class)
    public static void testGetFirstBinInLevelFail2() {
        ucsi.getFirstBinInLevelForCSI(7);
    }

    @DataProvider
    public Object[][] getTestLevelSizeData() {
        return new Object[][]{
                {csi, 0, 1},
                {csi, 1, 8},
                {csi, 2, 64},
                {csi, 3, 512},
                {csi, 4, 4096},
                {csi, 5, 32768},

                {ucsi, 0, 1},
                {ucsi, 1, 8},
                {ucsi, 2, 64},
                {ucsi, 3, 512},
                {ucsi, 4, 4096},
                {ucsi, 5, 32768},
                {ucsi, 6, 262144}
        };
    }

    @Test(dataProvider = "getTestLevelSizeData")
    public static void testGetLevelSizeOK(CSIIndex index, int level, int expected) {
        Assert.assertEquals(index.getLevelSize(level), expected);
    }

    @Test(expectedExceptions = SAMException.class)
    public static void testGetLevelSizeFail1() {
        csi.getLevelSize(6);
    }

    @Test(expectedExceptions = SAMException.class)
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

    @DataProvider
    public Object[][] getTestParentBinData() {
        return new Object[][]{
                {4681, 585},
                {4688, 585},
                {4689, 586},
                {585, 73},
                {592, 73},
                {593, 74},
                {0, 0}
        };
    }

    @Test(dataProvider = "getTestParentBinData")
    public static void testGetParentBinOK(int bin, int expected) {
        Assert.assertEquals(csi.getParentBinNumber(bin), expected);
    }

    @Test(expectedExceptions = SAMException.class)
    public static void testGetParentBinFail() {
        csi.getParentBinNumber(37449);
    }

    @DataProvider
    public Object[][] getTestRegionToBinMatchesBaiData() {
        return new Object[][]{
                //start, end
                {12653, 1876491},
                {1048576, 1146880},
                {536870912, 1073741824},
        };
    }

    @Test(dataProvider = "getTestRegionToBinMatchesBaiData")
    public static void testRegionToBinMatchesBai(int start, int end) {
        final int csiComputation = GenomicIndexUtil.regionToBin(start, end, BAI_EQUIVALENT_MIN_SHIFT, BAI_EQUIVALENT_BIN_DEPTH);
        final int baiComputation = GenomicIndexUtil.regionToBin(start, end);
        Assert.assertEquals(csiComputation, baiComputation);
    }

    @Test
    public static void testGetNoCoorCount() {
        long noCoorBai = bai.getNoCoordinateCount();
        long noCoorCsi = csi.getNoCoordinateCount();

        Assert.assertEquals(noCoorBai, 279);
        Assert.assertEquals(noCoorCsi, 279);
    }

    @DataProvider
    public Object[][] getDataForTestGetLevelForBin(){
        return new Object[][]{
                {csi, bin1, 0},
                {csi, bin2, 1},
                {csi, bin3, 0},
                {csi, bin4, 1},
                {csi, bin5, 2},
                {csi, bin6, 4},
                {csi, bin7, 3},
                {csi, bin8, 5},

                {ucsi, bin1, 0},
                {ucsi, bin2, 1},
                {ucsi, bin3, 0},
                {ucsi, bin4, 1},
                {ucsi, bin5, 2},
                {ucsi, bin6, 4},
                {ucsi, bin7, 3},
                {ucsi, bin8, 5},
                {ucsi, bin9, 6}
        };
    }

    @Test(dataProvider = "getDataForTestGetLevelForBin")
    public static void testGetLevelForBin(CSIIndex index, Bin bin, int expected) {
        Assert.assertEquals(index.getLevelForBin(bin), expected);
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

    @DataProvider
    public Object[][] testDataForGetFirstLocusInBin() {
        return new Object[][]{
                {csi, bin1, 1},
                {csi, bin2, 1},
                {csi, bin3, 1},
                {csi, bin4, 1},
                {csi, bin5, 1},
                {csi, bin6, (1 << 17) + 1},
                {csi, bin7, (1 << 20) * 7 + 1},
                {csi, bin8, (1 << 14) * 8 + 1},

                {ucsi, bin1, 1},
                {ucsi, bin2, 1},
                {ucsi, bin3, 1},
                {ucsi, bin4, 1},
                {ucsi, bin5, 1},
                {ucsi, bin6, (1 << 18) + 1},
                {ucsi, bin7, (1 << 21) * 7 + 1},
                {ucsi, bin8, (1 << 15) * 8 + 1},
                {ucsi, bin9, (1 << 12) * 98404 + 1},
        };
    }

    @Test(dataProvider = "testDataForGetFirstLocusInBin")
    public static void testGetFirstLocusInBin(CSIIndex index, Bin bin, int expected) {
        Assert.assertEquals(index.getFirstLocusInBin(bin), expected);
    }


    @DataProvider
    public Object[][] getDataForTestGetLastLocusInBin() {
        return new Object[][]{
                {csi, bin1, 1 << 29},
                {csi, bin2, 1 << 26},
                {csi, bin3, 1 << 29},
                {csi, bin4, 1 << 26},
                {csi, bin5, 1 << 23},
                {csi, bin6, (1 << 17) * 2},
                {csi, bin7, (1 << 20) * 8},
                {csi, bin8, (1 << 14) * 9},

                {ucsi, bin1, 1 << 30},
                {ucsi, bin2, 1 << 27},
                {ucsi, bin3, 1 << 30},
                {ucsi, bin4, 1 << 27},
                {ucsi, bin5, 1 << 24},
                {ucsi, bin6, (1 << 18) * 2},
                {ucsi, bin7, (1 << 21) * 8},
                {ucsi, bin8, (1 << 15) * 9},
                {ucsi, bin9, (1 << 12) * 98405},
        };
    }

    @Test(dataProvider = "getDataForTestGetLastLocusInBin")
    public static void testGetLastLocusInBin(CSIIndex index, Bin bin, int expected) {
        Assert.assertEquals(index.getLastLocusInBin(bin), expected);
    }


    @DataProvider
    public Object[][] getTestRegionToBinsMatchesBaiData() {
        return new Object[][]{
                //start, end
                {12653, 1876491},
                {1048576, 1146880},
                {536870912, 1073741824},
                {939520000, 939529000},
                {1, 2},
                {1_000, 1_001},
                {1_000, 10_001},
                {1_000, 100_001},
                {1_000, 1_000_001},
                {10_000, 10_0001},
                {1, 10_000_000},
                {939529000, 939529001},
                {558419286, 558424804},
        };
    }

    @Test(dataProvider = "getTestRegionToBinsMatchesBaiData")
    public static void testRegionToBinsMatchesBai(int start, int end) {
        final BitSet csiComputation = GenomicIndexUtil.regionToBins(start, end, BAI_EQUIVALENT_MIN_SHIFT, BAI_EQUIVALENT_BIN_DEPTH);
        final BitSet baiComputation = GenomicIndexUtil.regionToBins(start, end);
        Assert.assertEquals(csiComputation, baiComputation);
    }

    @DataProvider
    public Object[][] getTestRegionToBinsData() {
        return new Object[][]{
                //start, end, minShift, binDepth, expectedBins
                {(1 << 12) * 98403 + 4094, (1 << 12) * 98404 + 1, 12, 7, Arrays.asList(0, 4, 33, 265, 2122, bin9.getBinNumber() - 1, bin9.getBinNumber(), ucsi.getParentBinNumber(bin9.getBinNumber()))},
                {939520000, 939529000, 12, 7, Arrays.asList(0, 7, 8, 64, 65, 520, 521, 4168, 4169, 33352, 33353, 266823, 266824, 266825, 266826)},
                // This case tests https://github.com/samtools/htsjdk/issues/1047.
                // This combination of minShift and binDepth result in a maxPos larger than maxInt which caused
                // an overflow error.
                {558419286, 558424804, 14, 7, Arrays.asList(0, 2, 17, 139, 1117, 8941, 71532)}
        };
    }

    @Test(dataProvider = "getTestRegionToBinsData")
    public static void testRegionToBins(int start, int end, int minShift, int binDepth, List<Integer> expectedBins) {
        BitSet bins = GenomicIndexUtil.regionToBins(start, end, minShift, binDepth);
        final BitSet expected = new BitSet();
        expectedBins.forEach(expected::set);
        Assert.assertEquals(bins, expected);
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

    @DataProvider
    public Object[][] getDataForTestLongReferenceQuery() {
        return new Object[][]{
                //counts generated using samtools view
                //chrom , start, end, expected number of reads
                {"chr1H", 558419286, 558424804, 131},
                {"chr1H", 558414281, 558414323, 102},
                {"chr1H", 558414281, 558424804, 3015}
        };
    }

    // test for https://github.com/samtools/htsjdk/issues/1047
    @Test(dataProvider = "getDataForTestLongReferenceQuery")
    public void testLongReferenceQuery(String chr, int start, int end, int expectedReads) throws IOException {
        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
        try (final SamReader read = samReaderFactory.open(new File(TEST_DATA_DIR, "long_references.bam"))) {
            final SAMRecordIterator values = read.query(chr, start, end, false);
            Assert.assertEquals(values.toList().size(), expectedReads);
        }
    }
}
