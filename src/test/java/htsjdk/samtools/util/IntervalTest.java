package htsjdk.samtools.util;

import com.google.common.collect.Streams;
import htsjdk.HtsjdkTest;
import htsjdk.tribble.annotation.Strand;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.stream.Stream;

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

    @DataProvider
    public Object[][] getIntervalsForIntersectionAndAbutment(){
        final String chr1 = "chr1";
        final String chr2 = "chr2";

        final Interval chr1_10_20 = new Interval(chr1, 10, 20);
        final Interval chr1_10_21 = new Interval(chr1, 10, 21);
        final Interval chr1_10_22 = new Interval(chr1, 10, 22);
        final Interval chr1_10_23 = new Interval(chr1, 10, 23);
        final Interval chr1_20_30 = new Interval(chr1, 20, 30);
        final Interval chr1_21_30 = new Interval(chr1, 21, 30);
        final Interval chr1_22_30 = new Interval(chr1, 22, 30);
        final Interval chr1_23_30 = new Interval(chr1, 23, 30);
        final Interval chr1_24_30 = new Interval(chr1, 24, 30);
        final Interval zeroLength_chr1_22_21 = new Interval(chr1, 22, 21);
        final Interval chr2_10_20 = new Interval(chr2, 10, 20);
        final Interval chr2_21_30 = new Interval(chr2, 21,  30);

        /*

           10-20  <---|
           10-21  <--------|
           10-22  <-------------|
           10-23  <------------------|
           22-21                |
           20-30 |----------------------->
           21-30      |------------------>
           22-30           |------------->
           23-30                |-------->
           24-30                     |--->
             ... | 20 | 21 | 22 | 23 | 24
         */

        final Object[][] cases = new Object[][]{
                //interval1, interval2, intersects, abuts
                {chr1_10_20, chr1_10_20, true, false},
                {chr1_10_20, chr1_20_30, true, false},
                {chr1_10_20, chr1_21_30, false, true},
                {chr1_10_20, chr1_22_30, false, false},
                {chr1_10_20, zeroLength_chr1_22_21, false, false},
                {chr1_10_21, zeroLength_chr1_22_21, false, false},
                {chr1_10_22, zeroLength_chr1_22_21, false, true},
                {chr1_10_23, zeroLength_chr1_22_21, true, false},
                {chr1_20_30, zeroLength_chr1_22_21, true, false},
                {chr1_21_30, zeroLength_chr1_22_21, true, false},
                {chr1_22_30, zeroLength_chr1_22_21, true, false},
                {chr1_23_30, zeroLength_chr1_22_21, false, true},
                {chr1_24_30, zeroLength_chr1_22_21, false, false},
                {zeroLength_chr1_22_21, zeroLength_chr1_22_21, true, false},
                {chr2_10_20, chr1_10_20, false, false},
                {chr1_10_20, chr2_21_30, false, false}
        };
        //run intervals in both first and second position in case of weird asymmetry bugs
        return Arrays.stream(cases)
                .flatMap((Object[] test) -> {
                    final Object[] reversed = {test[1], test[0], test[2], test[3]};
                    return Stream.of(test, reversed);
                }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "getIntervalsForIntersectionAndAbutment")
    public void testIntersect(Interval interval1, Interval interval2, boolean expected, boolean ignored){
        Assert.assertEquals(interval1.intersects(interval2), expected);
    }

    @Test(dataProvider = "getIntervalsForIntersectionAndAbutment")
    public void testAbuts(Interval interval1, Interval interval2, boolean ignored, boolean expected){
        Assert.assertEquals(interval1.abuts(interval2), expected);
    }

    @Test(dataProvider = "getIntervalsForIntersectionAndAbutment")
    public void testOverlaps(Interval interval1, Interval interval2, boolean expected, boolean ignored){
        Assert.assertEquals(interval1.overlaps(interval2), expected);
    }

    @Test(dataProvider = "getIntervalsForIntersectionAndAbutment")
    public void testAbutsImplementedWithWithinDistance(Interval interval1, Interval interval2, boolean ignored, boolean expected){
        final boolean within1 = interval1.withinDistanceOf(interval2, 1);
        final boolean overlaps = interval1.overlaps(interval2);
        Assert.assertEquals(within1 && !overlaps, expected);
    }
}
