package htsjdk.tribble.util.popgen;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Created by farjoun on 7/18/14.
 */
public class HardyWeinbergCalculationTest {

    @DataProvider
    public Object[][] testHwCalculateData() {
        return new Object[][] {
                new Object[] {generateHWTrio(100, 0.01), 1.0},
                new Object[] {generateHWTrio(100, 0.1), 1.0},
                new Object[] {generateHWTrio(1000, 0.1), 1.0},
                new Object[] {generateHWTrio(1000, 0.01), 1.0},
                new Object[] {generateHWTrio(1000, 0.001), 1.0},
                new Object[] {generateHWTrio(10000, 0.1), 1.0},
                new Object[] {generateHWTrio(10000, 0.01), 1.0},
                new Object[] {generateHWTrio(10000, 0.001), 1.0},
                new Object[] {generateHWTrio(100000, 0.1), 1.0},
                new Object[] {generateHWTrio(100000, 0.01), 1.0},
                new Object[] {generateHWTrio(100000, 0.001), 1.0},

        };
    }

    private int[] generateHWTrio(final int total, final double p) {
        return new int[] {
                (int) Math.round(total * p * p),
                (int) Math.round(total * 2 * p * (1 - p)),
                (int) Math.round(total * (1 - p) * (1 - p))};
    }

    @Test(dataProvider = "testHwCalculateData")
    public void testHwCalculate(final int[] genotypeCounts, final double expectedHWS) throws Exception {
        Assert.assertEquals(HardyWeinbergCalculation.hwCalculate(genotypeCounts[0], genotypeCounts[1], genotypeCounts[2]), expectedHWS);
    }
}
