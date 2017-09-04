package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.Test;

public class QueryIntervalTest {

    @Test
    public void testOptimizeIntervals() throws Exception {
        final QueryInterval[] overlappingIntervals = new QueryInterval[] {
                new QueryInterval(0, 1520, 1521),
                new QueryInterval(0, 1521, 1525)
        };

        final QueryInterval[] optimizedOverlapping = QueryInterval.optimizeIntervals(overlappingIntervals);

        final QueryInterval[] abuttingIntervals = new QueryInterval[]{
                new QueryInterval(0, 1520, 1521),
                new QueryInterval(0, 1522, 1525)
        };

        final QueryInterval[] optimizedAbutting = QueryInterval.optimizeIntervals(abuttingIntervals);

        final QueryInterval[] expected = new QueryInterval[]{
                new QueryInterval(0, 1520, 1525),
        };

        Assert.assertEquals(optimizedOverlapping, expected);
        Assert.assertEquals(optimizedAbutting, expected);


        final QueryInterval[]
                nonOptimizableSeparatedIntervals = new QueryInterval[]{
                new QueryInterval(0, 1520, 1521),
                new QueryInterval(0, 1523, 1525)
        };

        final QueryInterval[] optimizedSeparated = QueryInterval.optimizeIntervals(nonOptimizableSeparatedIntervals);

        Assert.assertEquals(optimizedSeparated, nonOptimizableSeparatedIntervals);
    }
}
