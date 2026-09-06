package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Region-query semantics of {@link CRAIQueryIndex}: which containers a region resolves to.
 */
public class CRAIQueryIndexTest extends HtsjdkTest {

    /** A CRAI entry; slice offset and size are irrelevant to queries. */
    static CRAIEntry entry(final int sequenceId, final int start, final int span, final long containerOffset) {
        return new CRAIEntry(sequenceId, start, span, containerOffset, 0, 100);
    }

    /** Three non-overlapping slices on reference 0, covering 1-100, 201-300 and 401-500. */
    private static CRAIQueryIndex threeSlices() {
        return new CRAIQueryIndex(List.of(entry(0, 1, 100, 1000), entry(0, 201, 100, 2000), entry(0, 401, 100, 3000)));
    }

    @Test
    public void testQueryInsideASliceFindsThatSlice() {
        Assert.assertEquals(threeSlices().getContainerOffsets(0, 250, 260), new long[] {2000});
    }

    @Test
    public void testQuerySpanningTwoSlicesFindsBoth() {
        Assert.assertEquals(threeSlices().getContainerOffsets(0, 250, 450), new long[] {2000, 3000});
    }

    @Test
    public void testQueryCoveringEverythingFindsEverySlice() {
        Assert.assertEquals(threeSlices().getContainerOffsets(0, 1, 500), new long[] {1000, 2000, 3000});
    }

    @Test
    public void testQueryInAGapBetweenSlicesFindsNothing() {
        // htslib would return the nearest slice; see CRAIQueryIndex.
        Assert.assertEquals(threeSlices().getContainerOffsets(0, 150, 160), new long[0]);
    }

    @Test
    public void testQueryBeforeTheFirstSliceFindsNothing() {
        Assert.assertEquals(
                new CRAIQueryIndex(List.of(entry(0, 201, 100, 2000))).getContainerOffsets(0, 1, 50), new long[0]);
    }

    @Test
    public void testQueryAfterTheLastSliceFindsNothing() {
        Assert.assertEquals(threeSlices().getContainerOffsets(0, 900, 1000), new long[0]);
    }

    @Test
    public void testQueryAtTheFirstBaseOfASliceFindsIt() {
        Assert.assertEquals(threeSlices().getContainerOffsets(0, 201, 201), new long[] {2000});
    }

    @Test
    public void testQueryAtTheLastBaseOfASliceFindsIt() {
        // The slice covers 201-300 inclusive.
        Assert.assertEquals(threeSlices().getContainerOffsets(0, 300, 300), new long[] {2000});
    }

    @Test
    public void testQueryOneBaseBeforeASliceMissesIt() {
        Assert.assertEquals(threeSlices().getContainerOffsets(0, 200, 200), new long[0]);
    }

    @Test
    public void testQueryOneBaseAfterASliceMissesIt() {
        Assert.assertEquals(threeSlices().getContainerOffsets(0, 301, 301), new long[0]);
    }

    @Test
    public void testSliceContainedInAnEarlierLongerSliceIsStillFound() {
        // The first slice contains the two that follow it.
        final CRAIQueryIndex index = new CRAIQueryIndex(
                List.of(entry(0, 100, 10000, 1000), entry(0, 200, 50, 2000), entry(0, 300, 50, 3000)));
        // Inside the containing slice and the contained one.
        Assert.assertEquals(index.getContainerOffsets(0, 210, 220), new long[] {1000, 2000});
        // In the containing slice only.
        Assert.assertEquals(index.getContainerOffsets(0, 260, 270), new long[] {1000});
        Assert.assertEquals(index.getContainerOffsets(0, 300, 310), new long[] {1000, 3000});
    }

    @Test
    public void testLongSpanningSliceIsFoundFromAQueryFarToItsRight() {
        // The search must not stop at entries starting before the query.
        final CRAIQueryIndex index = new CRAIQueryIndex(
                List.of(entry(0, 1, 1_000_000, 1000), entry(0, 10, 20, 2000), entry(0, 30, 20, 3000)));
        Assert.assertEquals(index.getContainerOffsets(0, 999_000, 999_100), new long[] {1000});
    }

    @Test
    public void testQueryOnAReferenceWithNoEntriesFindsNothing() {
        Assert.assertEquals(threeSlices().getContainerOffsets(7, 1, 1000), new long[0]);
    }

    @Test
    public void testQueryOnlyReturnsSlicesFromTheRequestedReference() {
        final CRAIQueryIndex index =
                new CRAIQueryIndex(List.of(entry(0, 1, 100, 1000), entry(1, 1, 100, 2000), entry(2, 1, 100, 3000)));
        Assert.assertEquals(index.getContainerOffsets(1, 1, 100), new long[] {2000});
    }

    @Test
    public void testNonPositiveStartMeansTheStartOfTheReference() {
        Assert.assertEquals(threeSlices().getContainerOffsets(0, 0, 100), new long[] {1000});
        Assert.assertEquals(threeSlices().getContainerOffsets(0, -1, 100), new long[] {1000});
    }

    @Test
    public void testNonPositiveEndMeansTheEndOfTheReference() {
        // CRAMFileReader.queryAlignmentStart builds a QueryInterval with end = -1.
        Assert.assertEquals(threeSlices().getContainerOffsets(0, 250, -1), new long[] {2000, 3000});
        Assert.assertEquals(threeSlices().getContainerOffsets(0, 250, 0), new long[] {2000, 3000});
    }

    @Test
    public void testSlicesSharingAContainerAreReportedOnce() {
        final CRAIQueryIndex index =
                new CRAIQueryIndex(List.of(entry(0, 1, 100, 1000), entry(0, 101, 100, 1000), entry(0, 201, 100, 2000)));
        Assert.assertEquals(index.getContainerOffsets(0, 1, 300), new long[] {1000, 2000});
    }

    @Test
    public void testMultiReferenceSliceIsFoundThroughEachOfItsReferences() {
        // One entry per reference, sharing a container.
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(0, 50, 100, 5000), entry(1, 70, 100, 5000)));
        Assert.assertEquals(index.getContainerOffsets(0, 60, 60), new long[] {5000});
        Assert.assertEquals(index.getContainerOffsets(1, 80, 80), new long[] {5000});
    }

    @Test
    public void testUnplacedEntriesAreNotReturnedByRegionQueries() {
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(0, 1, 100, 1000), entry(-1, 0, 0, 9000)));
        Assert.assertEquals(index.getContainerOffsets(-1, 1, 1000), new long[0]);
        Assert.assertEquals(index.getContainerOffsets(0, 1, 1000), new long[] {1000});
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testAnEntryWithAMultiReferenceIdIsRejected() {
        // -2 is never valid in a CRAI.
        new CRAIQueryIndex(List.of(entry(-2, 1, 100, 1000)));
    }

    @Test
    public void testAnEmptyIndexAnswersEveryQueryWithNothing() {
        Assert.assertEquals(new CRAIQueryIndex(List.of()).getContainerOffsets(0, 1, 1000), new long[0]);
    }

    @Test
    public void testCoordinatesBeyondTheBaiCeilingAreQueryable() {
        // BAI bins top out at 2^29-1; a CRAI has no ceiling (issue #1747).
        final int beyondBaiCeiling = 600_000_000;
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(0, beyondBaiCeiling, 1000, 4242)));
        Assert.assertEquals(
                index.getContainerOffsets(0, beyondBaiCeiling + 10, beyondBaiCeiling + 20), new long[] {4242});
    }

    @Test
    public void testSpanReachingPastIntegerMaxDoesNotOverflow() {
        final CRAIQueryIndex index = new CRAIQueryIndex(List.of(entry(0, Integer.MAX_VALUE - 10, 1000, 77)));
        Assert.assertEquals(index.getContainerOffsets(0, Integer.MAX_VALUE - 5, Integer.MAX_VALUE), new long[] {77});
    }

    @Test
    public void testEntriesAreQueryableRegardlessOfInputOrder() {
        final CRAIQueryIndex shuffled =
                new CRAIQueryIndex(List.of(entry(0, 401, 100, 3000), entry(0, 1, 100, 1000), entry(0, 201, 100, 2000)));
        Assert.assertEquals(shuffled.getContainerOffsets(0, 1, 500), new long[] {1000, 2000, 3000});
    }
}
