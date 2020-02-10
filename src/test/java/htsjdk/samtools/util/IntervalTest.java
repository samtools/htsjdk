package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.annotation.Strand;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

public class IntervalTest extends HtsjdkTest {
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

    @Test()
    public void testCountBases(){
        // make sure we handle cases where the sum exceeds capacity of an integer
        final List<Interval> intervals = Arrays.asList(
            new Interval("chr1", 1, Integer.MAX_VALUE, false, "name1"),
            new Interval("chr2", 1, Integer.MAX_VALUE, false, "name2"));
        final long expected = Integer.MAX_VALUE * 2L;
        Assert.assertEquals(Interval.countBases(intervals), expected);
    }

}
