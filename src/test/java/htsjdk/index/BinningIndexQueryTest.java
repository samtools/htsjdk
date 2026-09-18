package htsjdk.index;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.Chunk;
import java.util.List;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link BinningIndex#getSpanOverlapping}: queries against the BAI/TBI binning scheme. */
public class BinningIndexQueryTest extends HtsjdkTest {
    private static final int MIN_SHIFT = BinningIndex.BAI_MIN_SHIFT;
    private static final int DEPTH = BinningIndex.BAI_DEPTH;

    /** Records every 3 kb across 40 Mb of two references, with an occasional one long enough for a higher bin. */
    private static IndexedRecords scatteredRecords() {
        final IndexedRecords records = new IndexedRecords();
        for (int referenceIndex = 0; referenceIndex < 2; referenceIndex++) {
            for (int i = 0, start = 1; start < 40_000_000; i++, start += 3_000) {
                final int length = (i % 500 == 0) ? 2_000_000 : (i % 50 == 0) ? 40_000 : 1;
                records.add(referenceIndex, start, start + length - 1);
            }
        }
        return records;
    }

    @Test
    public void testRandomQueriesReturnEveryOverlappingRecord() {
        final IndexedRecords records = scatteredRecords();
        final BinningIndex index = records.index(MIN_SHIFT, DEPTH, 2);
        final Random random = new Random(7);
        int overlapsSeen = 0;
        for (int i = 0; i < 300; i++) {
            final int referenceIndex = random.nextInt(2);
            final int start = 1 + random.nextInt(41_000_000);
            final int end = start + random.nextInt(i % 3 == 0 ? 3_000_000 : 5_000);
            final BAMFileSpan span = index.getSpanOverlapping(referenceIndex, start, end);
            overlapsSeen +=
                    IndexedRecords.assertSpanCoversOverlaps(records.records(), span, referenceIndex, start, end);
        }
        Assert.assertTrue(overlapsSeen > 1_000, "queries should have hit plenty of records");
    }

    @Test
    public void testQueryAtABinBoundaryFindsRecordsOnBothSides() {
        final int boundary = 1 << MIN_SHIFT; // last base of the first smallest bin
        final IndexedRecords records =
                new IndexedRecords().add(0, boundary, boundary).add(0, boundary + 1, boundary + 1);
        final BinningIndex index = records.index(MIN_SHIFT, DEPTH, 1);
        Assert.assertEquals(
                IndexedRecords.assertSpanCoversOverlaps(
                        records.records(),
                        index.getSpanOverlapping(0, boundary, boundary + 1),
                        0,
                        boundary,
                        boundary + 1),
                2);
    }

    @Test
    public void testRecordSpanningTheWholeReferenceIsFoundFromBinZero() {
        final IndexedRecords records =
                new IndexedRecords().add(0, 1, 500_000_000).add(0, 400_000_000, 400_000_000);
        final BinningIndex index = records.index(MIN_SHIFT, DEPTH, 1);
        Assert.assertEquals(index.getReference(0).getBinNumber(0), 0);
        Assert.assertEquals(
                IndexedRecords.assertSpanCoversOverlaps(
                        records.records(),
                        index.getSpanOverlapping(0, 250_000_000, 250_000_010),
                        0,
                        250_000_000,
                        250_000_010),
                1);
    }

    @Test
    public void testZeroStartAndEndMeanTheWholeReference() {
        final IndexedRecords records = scatteredRecords();
        final BinningIndex index = records.index(MIN_SHIFT, DEPTH, 2);
        final BAMFileSpan span = index.getSpanOverlapping(1, 0, 0);
        final int onReference1 = (int) records.records().stream()
                .filter(rec -> rec.referenceIndex() == 1)
                .count();
        Assert.assertEquals(
                IndexedRecords.assertSpanCoversOverlaps(records.records(), span, 1, 1, Integer.MAX_VALUE),
                onReference1);
    }

    @Test
    public void testEndBeyondTheAddressableRangeIsClamped() {
        final IndexedRecords records = scatteredRecords();
        final BinningIndex index = records.index(MIN_SHIFT, DEPTH, 2);
        final List<Chunk> clamped =
                index.getSpanOverlapping(0, 39_000_000, Integer.MAX_VALUE).getChunks();
        Assert.assertFalse(clamped.isEmpty());
        Assert.assertEquals(clamped, index.getSpanOverlapping(0, 39_000_000, 0).getChunks());
    }

    @Test
    public void testQueryPastTheLastRecordIsEmpty() {
        final BinningIndex index = scatteredRecords().index(MIN_SHIFT, DEPTH, 2);
        Assert.assertTrue(index.getSpanOverlapping(0, 300_000_000, 300_001_000).isEmpty());
    }

    @Test
    public void testQueryBeforeTheFirstRecordIsEmpty() {
        final BinningIndex index =
                new IndexedRecords().add(0, 5_000_000, 5_000_000).index(MIN_SHIFT, DEPTH, 1);
        Assert.assertTrue(index.getSpanOverlapping(0, 1, 1_000).isEmpty());
    }

    @Test
    public void testStartAfterEndIsEmpty() {
        final BinningIndex index = scatteredRecords().index(MIN_SHIFT, DEPTH, 2);
        Assert.assertTrue(index.getSpanOverlapping(0, 2_000, 1_000).isEmpty());
    }

    @Test
    public void testReferenceWithoutRecordsIsEmpty() {
        final BinningIndex index = new IndexedRecords().add(0, 100, 100).index(MIN_SHIFT, DEPTH, 3);
        Assert.assertTrue(index.getSpanOverlapping(1, 1, 1_000_000).isEmpty());
    }

    @Test
    public void testReferenceOutsideTheIndexIsEmpty() {
        final BinningIndex index = new IndexedRecords().add(0, 100, 100).index(MIN_SHIFT, DEPTH, 1);
        Assert.assertTrue(index.getSpanOverlapping(-1, 1, 1_000).isEmpty());
        Assert.assertTrue(index.getSpanOverlapping(1, 1, 1_000).isEmpty());
    }

    @Test
    public void testLinearIndexExcludesChunksThatEndBeforeTheQuery() {
        // A long record puts a chunk in a high-level bin that overlaps any later query; the linear index is what
        // keeps a query far downstream from having to read it.
        final IndexedRecords records = new IndexedRecords().add(0, 1, 3_000_000);
        for (int start = 10; start < 30_000_000; start += 1_000) records.add(0, start, start);
        final BinningIndex index = records.index(MIN_SHIFT, DEPTH, 1);
        final BAMFileSpan span = index.getSpanOverlapping(0, 25_000_000, 25_001_000);
        final long longRecordEnd = records.records().get(0).chunkEnd();
        for (final Chunk chunk : span.getChunks()) {
            Assert.assertTrue(Long.compareUnsigned(chunk.getChunkStart(), longRecordEnd) >= 0);
        }
        IndexedRecords.assertSpanCoversOverlaps(records.records(), span, 0, 25_000_000, 25_001_000);
    }

    @Test
    public void testChunksAreInFileOrderAndDisjoint() {
        final BinningIndex index = scatteredRecords().index(MIN_SHIFT, DEPTH, 2);
        final List<Chunk> chunks =
                index.getSpanOverlapping(0, 1_000_000, 30_000_000).getChunks();
        Assert.assertFalse(chunks.isEmpty());
        for (int i = 1; i < chunks.size(); i++) {
            Assert.assertTrue(Long.compareUnsigned(
                            chunks.get(i - 1).getChunkEnd(), chunks.get(i).getChunkStart())
                    < 0);
        }
    }
}
