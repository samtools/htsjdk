package htsjdk.samtools.util;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SimpleIntervalTest {
    final SimpleInterval i1 = new SimpleInterval("1",1,100);
    final SimpleInterval i2 = new SimpleInterval("1",1,100);
    final SimpleInterval i3 = new SimpleInterval("chr1", 1, 100);
    final SimpleInterval i4 = new SimpleInterval("chr1", 2, 100);
    final SimpleInterval i5 = new SimpleInterval("chr1", 1, 200);

    @Test
    public void testZeroLengthInterval(){
        SimpleInterval interval = new SimpleInterval("1",100,99);
        Assert.assertEquals(interval.getContig(), "1");
        Assert.assertEquals(interval.getStart(), 100);
        Assert.assertEquals(interval.getEnd(), 99);
    }

    @DataProvider(name = "badIntervals")
    public Object[][] badIntervals(){
        return new Object[][]{
                {null,1,12, "null contig"},
                {"1", 0, 10, "start==0"},
                {"1", -10, 10, "negative start"},
                {"1", 10, 8, "end < start - 1"}
        };
    }

    @Test(dataProvider = "badIntervals", expectedExceptions = IllegalArgumentException.class)
    public void badIntervals(String contig, int start, int end, String name){
        SimpleInterval interval = new SimpleInterval(contig, start, end);
    }

    @Test
    public void testEquality(){
        Assert.assertTrue(i1.equals(i1));
        Assert.assertTrue(i1.equals(i2));
        Assert.assertTrue(i2.equals(i1));
        Assert.assertFalse(i1.equals(i3));
        Assert.assertFalse(i1.equals(i4));
        Assert.assertFalse(i1.equals(i5));

        //currently Interval cannot be equal to SimpleInterval, maybe we want to change this?
        final Interval namedInterval = new Interval("1",1,100);
        Assert.assertFalse(namedInterval.equals(i1));
        Assert.assertFalse(i1.equals(namedInterval));
    }

    @Test
    public void testToString(){
        Assert.assertEquals(i1.toString(), "1:1-100");
    }
}