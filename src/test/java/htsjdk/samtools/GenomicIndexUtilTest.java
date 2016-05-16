package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for GenomicIndexUtil.
 */
public class GenomicIndexUtilTest {

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