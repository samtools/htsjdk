package htsjdk.samtools.cram;

import static htsjdk.samtools.cram.CRAIQueryIndexTest.entry;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.QueryInterval;
import java.util.List;
import java.util.OptionalLong;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * The coordinate array {@link CRAIQueryIndex} hands to {@code CramSpanContainerIterator}, and the
 * first unplaced container offset.
 */
public class CRAIQueryIndexCoordinatesTest extends HtsjdkTest {

    private static final int CONTAINER_OFFSET_SHIFT = 16;

    private static QueryInterval[] intervals(final QueryInterval... intervals) {
        return intervals;
    }

    @Test
    public void testEachContainerBecomesOnePairAddressingOnlyItself() {
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(0, 1, 100, 1000)));
        final long[] coordinates = index.getCoordinatesForQueries(intervals(new QueryInterval(0, 1, 100)));

        Assert.assertEquals(coordinates.length, 2);
        Assert.assertEquals(coordinates[0] >> CONTAINER_OFFSET_SHIFT, 1000);
        Assert.assertEquals(coordinates[1] >> CONTAINER_OFFSET_SHIFT, 1000);
    }

    @Test
    public void testEveryPairHasStartStrictlyBelowEnd() {
        // CramSpanContainerIterator.Boundary requires start < end.
        final CRAIQueryIndex index =
                new CRAIQueryIndex(List.of(entry(0, 1, 100, 0), entry(0, 201, 100, 2000), entry(0, 401, 100, 3000)));
        final long[] coordinates = index.getCoordinatesForQueries(intervals(new QueryInterval(0, 1, 500)));

        Assert.assertEquals(coordinates.length, 6);
        for (int i = 0; i < coordinates.length; i += 2) {
            Assert.assertTrue(
                    coordinates[i] < coordinates[i + 1],
                    "Pair " + i / 2 + " is not a valid boundary: " + coordinates[i] + " >= " + coordinates[i + 1]);
        }
    }

    @Test
    public void testPairsAreInAscendingFileOrder() {
        // The iterator only seeks forward.
        final CRAIQueryIndex index =
                new CRAIQueryIndex(List.of(entry(0, 401, 100, 3000), entry(0, 1, 100, 1000), entry(0, 201, 100, 2000)));
        final long[] coordinates = index.getCoordinatesForQueries(intervals(new QueryInterval(0, 1, 500)));

        for (int i = 2; i < coordinates.length; i += 2) {
            Assert.assertTrue(coordinates[i] > coordinates[i - 1], "Pairs are not ascending at index " + i);
        }
    }

    @Test
    public void testContainersFarApartDoNotDragInEverythingBetween() {
        // A single wide pair would read containers 1000 through 9000.
        final CRAIQueryIndex index =
                new CRAIQueryIndex(List.of(entry(0, 1, 100, 1000), entry(0, 201, 100, 5000), entry(0, 401, 100, 9000)));
        final long[] coordinates =
                index.getCoordinatesForQueries(intervals(new QueryInterval(0, 1, 100), new QueryInterval(0, 401, 500)));

        Assert.assertEquals(coordinates.length, 4);
        Assert.assertEquals(coordinates[0] >> CONTAINER_OFFSET_SHIFT, 1000);
        Assert.assertEquals(coordinates[2] >> CONTAINER_OFFSET_SHIFT, 9000);
    }

    @Test
    public void testIntervalsHittingTheSameContainerProduceOnePair() {
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(0, 1, 1000, 1000)));
        final long[] coordinates =
                index.getCoordinatesForQueries(intervals(new QueryInterval(0, 10, 20), new QueryInterval(0, 500, 600)));

        Assert.assertEquals(coordinates.length, 2);
        Assert.assertEquals(coordinates[0] >> CONTAINER_OFFSET_SHIFT, 1000);
    }

    @Test
    public void testIntervalsOnDifferentReferencesAreCombined() {
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(0, 1, 100, 1000), entry(1, 1, 100, 2000)));
        final long[] coordinates =
                index.getCoordinatesForQueries(intervals(new QueryInterval(0, 1, 100), new QueryInterval(1, 1, 100)));

        Assert.assertEquals(coordinates.length, 4);
        Assert.assertEquals(coordinates[0] >> CONTAINER_OFFSET_SHIFT, 1000);
        Assert.assertEquals(coordinates[2] >> CONTAINER_OFFSET_SHIFT, 2000);
    }

    @Test
    public void testAMultiReferenceContainerIsReadOnlyOnce() {
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(0, 50, 100, 5000), entry(1, 70, 100, 5000)));
        final long[] coordinates =
                index.getCoordinatesForQueries(intervals(new QueryInterval(0, 60, 60), new QueryInterval(1, 80, 80)));

        Assert.assertEquals(coordinates.length, 2);
        Assert.assertEquals(coordinates[0] >> CONTAINER_OFFSET_SHIFT, 5000);
    }

    @Test
    public void testNoMatchProducesAnEmptyArray() {
        // CRAMFileReader returns an empty iterator for this; CramSpanContainerIterator would throw.
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(0, 1, 100, 1000)));
        Assert.assertEquals(index.getCoordinatesForQueries(intervals(new QueryInterval(0, 500, 600))).length, 0);
    }

    @Test
    public void testNoIntervalsProducesAnEmptyArray() {
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(0, 1, 100, 1000)));
        Assert.assertEquals(index.getCoordinatesForQueries(new QueryInterval[0]).length, 0);
    }

    @Test
    public void testFirstUnplacedContainerOffsetIsTheLowestUnplacedContainer() {
        final CRAIQueryIndex index = new CRAIQueryIndex(
                List.of(entry(0, 1, 100, 1000), entry(-1, 0, 0, 9500), entry(-1, 0, 0, 9000), entry(-1, 0, 0, 9800)));
        Assert.assertEquals(index.getFirstUnplacedContainerOffset(), OptionalLong.of(9000));
    }

    @Test
    public void testFirstUnplacedContainerOffsetFindsUnplacedRecordsSharingAContainerWithPlacedOnes() {
        // A multi-reference slice with both kinds has an entry for each.
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(0, 1, 100, 1000), entry(-1, 0, 0, 1000)));
        Assert.assertEquals(index.getFirstUnplacedContainerOffset(), OptionalLong.of(1000));
    }

    @Test
    public void testFirstUnplacedContainerOffsetIsPresentWhenNothingIsPlaced() {
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(-1, 0, 0, 9000)));
        Assert.assertEquals(index.getFirstUnplacedContainerOffset(), OptionalLong.of(9000));
    }

    @Test
    public void testFirstUnplacedContainerOffsetIsEmptyWhenNothingIsUnplaced() {
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(0, 1, 100, 1000), entry(1, 1, 100, 7000)));
        Assert.assertEquals(index.getFirstUnplacedContainerOffset(), OptionalLong.empty());
        Assert.assertTrue(index.getSpanOfUnplaced().isEmpty());
    }

    @Test
    public void testFirstUnplacedContainerOffsetIsEmptyForAnEmptyIndex() {
        Assert.assertEquals(new CRAIQueryIndex(List.of()).getFirstUnplacedContainerOffset(), OptionalLong.empty());
    }

    @Test
    public void testAContainerAtOffsetZeroStillProducesAValidPair() {
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(0, 1, 100, 0)));
        final long[] coordinates = index.getCoordinatesForQueries(intervals(new QueryInterval(0, 1, 100)));

        Assert.assertEquals(coordinates[0], 0);
        Assert.assertTrue(coordinates[0] < coordinates[1]);
        Assert.assertEquals(coordinates[1] >> CONTAINER_OFFSET_SHIFT, 0);
    }
}
