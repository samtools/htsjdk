package htsjdk.samtools.util;

import htsjdk.tribble.annotation.Strand;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class IntervalTest {
    @DataProvider
    public Object[][] booleanProvider(){
        return new Object[][]{{true}, {false}};
    }

    @Test(dataProvider = "booleanProvider")
    public void testGetStrand(boolean isNegativeStrand){
        final Interval interval = new Interval("chr1", 1, 100, isNegativeStrand, "name");
        Assert.assertEquals(isNegativeStrand, interval.isNegativeStrand());
        Assert.assertNotEquals(isNegativeStrand, interval.isPositiveStrand());
        Assert.assertEquals(isNegativeStrand, interval.getStrand() == Strand.NEGATIVE);
    }
}