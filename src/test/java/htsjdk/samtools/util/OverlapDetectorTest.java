package htsjdk.samtools.util;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

public class OverlapDetectorTest {

    @DataProvider(name="intervalsMultipleContigs")
    public Object[][] intervalsMultipleContigs(){
        final List<Locatable> input = Arrays.asList(
                new Interval("1", 10, 100),
                new Interval("2", 200, 300)
        );
        final List<Locatable> empty = new ArrayList<>();
        final List<Locatable> contig1 = Arrays.asList(
                new Interval("1",  10, 100)
        );
        final List<Locatable> contig2 = Arrays.asList(
                new Interval("2", 200, 300)
        );

        // returns input, query range, expected SimpleIntervals
        return new Object[][] {
                // we already test elsewhere that it works within a contig, so here we just have to make sure that
                // it picks the correct contig and can deal with not-yet-mentioned contigs.
                new Object[] {input, new Interval("1", 100, 200), contig1},
                new Object[] {input, new Interval("1", 1, 5), empty},
                new Object[] {input, new Interval("2", 100, 200), contig2},
                new Object[] {input, new Interval("3", 100, 200), empty},
        };
    }

    @Test(dataProvider = "intervalsMultipleContigs")
    public void testOverlap(final List<Locatable> input, final Locatable query, final Collection<Locatable> expected) throws Exception {
        final OverlapDetector<Locatable> targetDetector = new OverlapDetector<>(0, 0);
        targetDetector.addAll(input, input);

        final Collection<Locatable> actual = targetDetector.getOverlaps(query);
        Assert.assertEquals(actual, expected);
    }

    @DataProvider(name="intervalsSameContig")
    public Object[][] intervalsSameContig(){
        final List<Locatable> input = Arrays.asList(
                new Interval("1",10,100)
        );
        final List<Locatable> empty = new ArrayList<>();
        final List<Locatable> manyOverlapping = Arrays.asList(
                new Interval("1",10,100),
                // special case: multiple intervals starting at the same place
                new Interval("1",20,50),
                new Interval("1",20,51),
                new Interval("1",20,52)
        );
        final List<Locatable> mixInput = Arrays.asList(
                // ends before query interval
                new Interval("1",10,20),
                // ends in query interval
                new Interval("1",10,60),
                // equal to query interval
                new Interval("1",30,50),
                // covered by query interval
                new Interval("1",40,42),
                // ends after query interval
                new Interval("1",45,60),
                // starts after query interval
                new Interval("1",60,100)
        );
        final List<Locatable> mixExpected = Arrays.asList(
                // ends in query interval
                new Interval("1",10,60),
                // equal to query interval
                new Interval("1",30,50),
                // covered by query interval
                new Interval("1",40,42),
                // ends after query interval
                new Interval("1",45,60)
        );
        // returns input single SimpleInterval, query range, expected SimpleInterval
        return new Object[][] {
                // single-point boundary cases
                new Object[] {input, new Interval("1", 10, 10), input},
                new Object[] {input, new Interval("1", 100, 100), input},
                new Object[] {input, new Interval("1", 9, 9), empty},
                new Object[] {input, new Interval("1", 11, 11), input},
                new Object[] {input, new Interval("1", 99, 99), input},
                new Object[] {input, new Interval("1", 101, 101), empty},
                // different contig
                new Object[] {input, new Interval("2", 10, 100), empty},
                // empty list boundary case
                new Object[] {empty, new Interval("1", 101, 101), empty},
                // input exactly matches the query interval
                new Object[] {input, new Interval("1", 10, 100), input},
                // multiple intervals in the same place (potential edge case for indexing)
                new Object[] {manyOverlapping, new Interval("1", 20, 20), manyOverlapping},
                // input with multiple intervals
                new Object[] {mixInput, new Interval("1",30,50), mixExpected},
                // input with multiple intervals , non overlapping query
                new Object[] {mixInput, new Interval("1",300,500), empty},
        };
    }

    @Test(dataProvider = "intervalsSameContig")
    public void testOverlap(final List<Locatable> input, final Interval query, final List<Locatable> expected) throws Exception {
        final OverlapDetector<Locatable> targetDetector = OverlapDetector.create(input);

        final Set<Locatable> actual = targetDetector.getOverlaps(query);
        Assert.assertEquals(actual, new HashSet<>(expected));

        Assert.assertEquals(targetDetector.overlapsAny(query), !expected.isEmpty());

        Assert.assertEquals(new HashSet<>(targetDetector.getAll()), new HashSet<>(input));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testOverlapsNullArg() throws Exception {
        final List<Locatable> input = Arrays.asList(
                new Interval("1",10,100)
        );
        final OverlapDetector<Locatable> targetDetector = OverlapDetector.create(input);
        targetDetector.overlapsAny(null);
    }

    @Test
    public void testNoOverlapsAny() throws Exception {
        final List<Locatable> input = Arrays.asList(
                new Interval("1",10,110)
        );
        final OverlapDetector<Locatable> trimmedTargetDetector = new OverlapDetector(20,20);
        trimmedTargetDetector.addAll(input, input);
        Assert.assertFalse(trimmedTargetDetector.overlapsAny( new Interval("1",50,85)));//no overlap because of trim
        Assert.assertTrue(trimmedTargetDetector.getOverlaps( new Interval("1",50,85)).isEmpty());//no overlap because of trim

        final OverlapDetector<Locatable> untrimmedTargetDetector = new OverlapDetector(0,0);
        untrimmedTargetDetector.addAll(input, input);
        Assert.assertTrue(untrimmedTargetDetector.overlapsAny( new Interval("1",50,85)));//overlaps - no trim
    }

    @Test
    public void testLotsOfTinyIntervals() throws Exception {
        final List<Locatable> input = new ArrayList<>();
        final int n = 1000000;
        for (int i = 0; i < n; i++) {
            input.add(new Interval("1", 3*i+1, 3*i+2)); //1:1-2, 1:4-5, 1:7-8
        }
        final OverlapDetector<Locatable> detector = OverlapDetector.create(input);
        final Set<Locatable> overlapping = detector.getOverlaps(new Interval("1", 1, 3 * n + 2));
        Assert.assertEquals(new HashSet<>(input), overlapping);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testAddAllDifferentSizes() throws Exception {
        final List<Locatable> input = Arrays.asList(
                new Interval("1",10,100)
        );
        final OverlapDetector<Locatable> targetDetector = OverlapDetector.create(input);

        final List<Locatable> input1Interval = Arrays.asList(
                new Interval("1",11,101)
        );

        final List<Locatable> input2Intervals = Arrays.asList(
                new Interval("1",20,200),
                new Interval("1",20,200)
        );
        targetDetector.addAll(input1Interval, input2Intervals);

    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullObjectAddLHS() throws Exception {
        final List<Locatable> input = Arrays.asList(
                new Interval("1",10,100)
        );
        final OverlapDetector<Locatable> targetDetector = OverlapDetector.create(input);
        targetDetector.addLhs(null, new Interval("2",10,100));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullIntervalAddLHS() throws Exception {
        final List<Locatable> input = Arrays.asList(
                new Interval("1",10,100)
        );
        final OverlapDetector<Locatable> targetDetector = OverlapDetector.create(input);
        targetDetector.addLhs(new Interval("2",10,100), null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullObjectsAddAll() throws Exception {
        final List<Locatable> input = Arrays.asList(
                new Interval("1",10,100)
        );
        final OverlapDetector<Locatable> targetDetector = OverlapDetector.create(input);
        targetDetector.addAll(null, Arrays.asList(new Interval("2",10,100)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullIntervalsAddAll() throws Exception {
        final List<Locatable> input = Arrays.asList(
                new Interval("1",10,100)
        );
        final OverlapDetector<Locatable> targetDetector = OverlapDetector.create(input);
        targetDetector.addAll(Arrays.asList(new Interval("2",10,100)), null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testDifferentSizesAddAll() throws Exception {
        final List<Locatable> input = Arrays.asList(
                new Interval("1",10,100)
        );
        final OverlapDetector<Locatable> targetDetector = OverlapDetector.create(input);
        final List<Locatable> l1 = Arrays.asList(new Interval("2", 10, 100));
        final List<Locatable> l2 = Arrays.asList(new Interval("2", 10, 100), new Interval("3", 10, 100));
        targetDetector.addAll(l1, l2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullArgGetOverlaps() throws Exception {
        final List<Locatable> input = Arrays.asList(
                new Interval("1",10,100)
        );
        final OverlapDetector<Locatable> targetDetector = OverlapDetector.create(input);
        targetDetector.getOverlaps(null);
    }

    @Test
    public void testAddTwice() throws Exception {
        final List<Locatable> input = Arrays.asList(
                new Interval("1",10,100),
                new Interval("1",10,100)
        );
        final OverlapDetector<Locatable> detector = OverlapDetector.create(input);
        final Set<Locatable> overlaps = detector.getOverlaps(new Interval("1", 50, 200));
        Assert.assertEquals(overlaps.size(), 1);
        Assert.assertEquals(overlaps, Collections.singleton(new Interval("1",10,100)));
    }
}
