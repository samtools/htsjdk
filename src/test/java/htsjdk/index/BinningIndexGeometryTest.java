package htsjdk.index;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.BAMFileSpan;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link BinningIndex} under binning schemes other than the fixed BAI/TBI one. */
public class BinningIndexGeometryTest extends HtsjdkTest {

    private static void assertRandomQueriesCoverOverlaps(
            final IndexedRecords records, final BinningIndex index, final int maxStart, final int maxLength) {
        final Random random = new Random(11);
        int overlapsSeen = 0;
        for (int i = 0; i < 200; i++) {
            final int start = 1 + random.nextInt(maxStart);
            final int end = (int) Math.min(Integer.MAX_VALUE, (long) start + random.nextInt(maxLength));
            final BAMFileSpan span = index.getSpanOverlapping(0, start, end);
            overlapsSeen += IndexedRecords.assertSpanCoversOverlaps(records.records(), span, 0, start, end);
        }
        Assert.assertTrue(overlapsSeen > 100, "queries should have hit plenty of records");
    }

    @Test
    public void testSmallShallowSchemeReturnsEveryOverlappingRecord() {
        // minShift 10, depth 3: smallest bins of 1 kb, 512 kb addressable.
        final IndexedRecords records = new IndexedRecords();
        for (int i = 0, start = 1; start < 500_000; i++, start += 300) {
            records.add(0, start, Math.min(1 << 19, start + ((i % 40 == 0) ? 60_000 : (i % 7 == 0) ? 1_500 : 0)));
        }
        assertRandomQueriesCoverOverlaps(records, records.index(10, 3, 1), 510_000, 20_000);
    }

    @Test
    public void testDeepSchemeIndexesPositionsBeyondTheBaiLimit() {
        // minShift 14, depth 6: 2^32 addressable, as tabix -C chooses by default.
        final IndexedRecords records = new IndexedRecords();
        for (int i = 0, start = 1; start < 2_000_000_000 && start > 0; i++, start += 1_500_007) {
            records.add(0, start, start + ((i % 25 == 0) ? 40_000_000 : 0));
        }
        final BinningIndex index = records.index(14, 6, 1);
        Assert.assertEquals(index.getMaxPosition(), 1L << 32);
        assertRandomQueriesCoverOverlaps(records, index, 2_000_000_000, 100_000_000);
    }

    @Test
    public void testWholeReferenceQueryOnADeepSchemeReturnsEverything() {
        final IndexedRecords records = new IndexedRecords();
        for (int start = 1; start < 2_000_000_000 && start > 0; start += 10_000_019) records.add(0, start, start);
        final BAMFileSpan span = records.index(14, 6, 1).getSpanOverlapping(0, 1, Integer.MAX_VALUE);
        Assert.assertEquals(
                IndexedRecords.assertSpanCoversOverlaps(records.records(), span, 0, 1, Integer.MAX_VALUE),
                records.records().size());
    }

    @Test
    public void testLongRecordUnderADeepSchemeGoesInTheSmallestBinThatHoldsIt() {
        // Depth 8 is what a CSI gets without a dictionary. Its top levels shift by 32 and 35 bits; a record
        // spanning 600 Mb fits a level-2 bin (2^32 bases), and must not fall through to bin 0.
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 8);
        builder.add(0, 1, 600_000_000, 0, 100);
        Assert.assertEquals(builder.build(1).getReference(0).getBinNumber(0), 9);
    }

    @Test
    public void testMaxPositionOfTheBaiSchemeIs512Mb() {
        final BinningIndex index = new BinningIndex.Builder(14, 5).build(0);
        Assert.assertEquals(index.getMaxPosition(), 1L << 29);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRecordBeyondTheAddressableRangeIsRejected() {
        new BinningIndex.Builder(14, 5).add(0, 600_000_000, 600_000_000, 0, 100);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRecordEndingBeyondTheAddressableRangeIsRejected() {
        new BinningIndex.Builder(14, 5).add(0, 536_870_000, 536_871_000, 0, 100);
    }

    @Test
    public void testRecordEndingAtTheLastAddressablePositionIsAccepted() {
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 5);
        builder.add(0, 1 << 29, 1 << 29, 0, 100);
        Assert.assertFalse(
                builder.build(1).getSpanOverlapping(0, 1 << 29, 1 << 29).isEmpty());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testDepthTooGreatForIntBinNumbersIsRejected() {
        new BinningIndex.Builder(14, 10);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testZeroDepthIsRejected() {
        new BinningIndex.Builder(14, 0);
    }
}
