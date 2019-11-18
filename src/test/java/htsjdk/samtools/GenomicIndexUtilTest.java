package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for GenomicIndexUtil.
 */
public class GenomicIndexUtilTest extends HtsjdkTest {

    @DataProvider(name="bin2LevelProvider")
    private Object[][] getBin2LevelTests() {
        return new Object[][] {
                // level boundary tests
                { 0, 0, "bin=0, level=0, first bin=0, bin size=536,870,912 bin range=(0-536,870,912)" },
                { 1, 1, "bin=1, level=1, first bin=1, bin size=67,108,864 bin range=(0-67,108,864)"},
                { 8, 1, "bin=8, level=1, first bin=1, bin size=67,108,864 bin range=(469,762,048-536,870,912)"},
                { 9, 2, "bin=9, level=2, first bin=9, bin size=8,388,608 bin range=(0-8,388,608)" },
                { 72, 2, "bin=72, level=2, first bin=9, bin size=8,388,608 bin range=(528,482,304-536,870,912)" },
                { 73, 3, "bin=73, level=3, first bin=73, bin size=1,048,576 bin range=(0-1,048,576)"},
                { 584, 3, "bin=584, level=3, first bin=73, bin size=1,048,576 bin range=(535,822,336-536,870,912)"},
                { 585, 4, "bin=585, level=4, first bin=585, bin size=131,072 bin range=(0-131,072)"},
                { 591, 4, "bin=591, level=4, first bin=585, bin size=131,072 bin range=(786,432-917,504)"},
                { 4680, 4, "bin=4680, level=4, first bin=585, bin size=131,072 bin range=(536,739,840-536,870,912)"},
                { 4681, 5, "bin=4681, level=5, first bin=4681, bin size=16,384 bin range=(0-16,384)"},
                { 37448, 5, "bin=37448, level=5, first bin=4681, bin size=16,384 bin range=(536,854,528-536,870,912)"}
        };
    }

    @Test(dataProvider = "bin2LevelProvider")
    public void testBin2Level(final int bin, final int expectedLevel, final String ignored) {
        Assert.assertEquals(GenomicIndexUtil.binTolevel(bin), expectedLevel);
    }

    @Test(dataProvider = "bin2LevelProvider")
    public void testBin2RegionString(final int bin, final int ignored, final String expectedString) {
        Assert.assertEquals(GenomicIndexUtil.getBinSummaryString(bin), expectedString);
    }

    @Test(dataProvider = "testRegionToBinDataProvider")
    public void testRegionToBin(final int beg, final int end, final int bin) {
        Assert.assertEquals(GenomicIndexUtil.regionToBin(beg, end), bin);
    }

    @DataProvider(name = "testRegionToBinDataProvider")
    public Object[][] testRegionToBinDataProvider() {
        return new Object[][] {
                {0, 0, 0},
                {1, 1, 4681},
                {0, 1<<14, 4681},
                {0, (1<<14)+1, 585},
                
                {1<<14, 1<<14, 585},
                {(1<<14)+1, (1<<14)+1, 4682},
                {1<<14, 1<<17, 585},
                {1<<14, (1<<17)+1, 73},

                {1<<17, 1<<17, 73},
                {(1<<17)+1, (1<<17)+1, 4689},
                {1<<17, 1<<20, 73},
                {1<<17, (1<<20)+1, 9},

                {1<<20, 1<<20, 9},
                {(1<<20)+1, (1<<20)+1, 4745},
                {1<<20, 1<<23, 9},
                {1<<20, (1<<23)+1, 1},

                {1<<23, 1<<23, 1},
                {(1<<23)+1, (1<<23)+1, 5193},
                {1<<23, 1<<26, 1},
                {1<<23, (1<<26)+1, 0},

                {1<<26, 1<<26, 0},
                {(1<<26)+1, (1<<26)+1, 8777},
                {1<<26, 1<<26+1, 2}
        };
    }
}
