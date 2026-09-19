package htsjdk.index;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.Chunk;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link BinningIndex.Builder}: how records become bins, chunks and linear-index entries. */
public class BinningIndexBuilderTest extends HtsjdkTest {
    private static final int SMALLEST_BIN_0 = 4681; // first smallest bin of the BAI scheme

    /** A BGZF virtual offset. */
    private static long offset(final long blockAddress, final int withinBlock) {
        return BlockCompressedFilePointerUtil.makeFilePointer(blockAddress, withinBlock);
    }

    /** A reference's bins, in bin-number order, each with its chunks. */
    private static Map<Integer, List<Chunk>> binsOf(final ReferenceBins reference) {
        final Map<Integer, List<Chunk>> bins = new LinkedHashMap<>();
        for (int i = 0; i < reference.getBinCount(); i++) bins.put(reference.getBinNumber(i), reference.getChunks(i));
        return bins;
    }

    /** A builder for the BAI/TBI binning scheme. */
    private static BinningIndex.Builder baiBuilder() {
        return new BinningIndex.Builder(BinningIndex.BAI_MIN_SHIFT, BinningIndex.BAI_DEPTH);
    }

    @Test
    public void testRecordGoesInTheSmallestBinThatContainsIt() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 1, 16_384, offset(0, 0), offset(0, 10)); // exactly the first 16 kb bin
        builder.add(1, 16_384, 16_385, offset(0, 10), offset(0, 20)); // straddles two 16 kb bins -> first 128 kb bin
        final BinningIndex index = builder.build(2);
        Assert.assertEquals(index.getReference(0).getBinNumber(0), SMALLEST_BIN_0);
        Assert.assertEquals(index.getReference(1).getBinNumber(0), 585);
    }

    @Test
    public void testChunksInTheSameOrAdjacentBlocksAreStoredAsOne() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 10, 10, offset(0, 0), offset(0, 50));
        builder.add(0, 20, 20, offset(0, 50), offset(0, 100));
        builder.add(0, 30, 30, offset(1, 0), offset(1, 50));
        final List<Chunk> chunks = builder.build(1).getReference(0).getChunks(0);
        Assert.assertEquals(chunks, List.of(new Chunk(offset(0, 0), offset(1, 50))));
    }

    @Test
    public void testChunksInDistantBlocksStaySeparate() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 10, 10, offset(0, 0), offset(0, 50));
        builder.add(0, 20, 20, offset(10_000_000, 0), offset(10_000_000, 50));
        Assert.assertEquals(builder.build(1).getReference(0).getChunks(0).size(), 2);
    }

    @Test
    public void testLinearIndexHoldsTheSmallestOffsetOverlappingEachWindow() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 1, 40_000, offset(0, 0), offset(0, 10)); // windows 0-2
        builder.add(0, 20_000, 20_000, offset(0, 10), offset(0, 20)); // window 1, later in the file
        Assert.assertEquals(
                builder.build(1).getReference(0).getLinearIndex(),
                new long[] {offset(0, 0), offset(0, 0), offset(0, 0)});
    }

    @Test
    public void testLinearIndexWindowsWithoutRecordsTakeTheOffsetOfTheNextWindowWithOne() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 20_000, 20_000, offset(5, 0), offset(5, 10)); // window 1
        builder.add(0, 70_000, 70_000, offset(9, 0), offset(9, 10)); // window 4
        Assert.assertEquals(
                builder.build(1).getReference(0).getLinearIndex(),
                new long[] {offset(5, 0), offset(5, 0), offset(9, 0), offset(9, 0), offset(9, 0)});
    }

    @Test
    public void testRecordWhoseEndPrecedesItsStartIsIndexedAtItsStart() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 16_385, 16_384, offset(0, 0), offset(0, 10)); // e.g. a zero-length BED interval
        final BinningIndex index = builder.build(1);
        Assert.assertEquals(index.getReference(0).getBinNumber(0), SMALLEST_BIN_0 + 1);
        Assert.assertFalse(index.getSpanOverlapping(0, 16_385, 16_385).isEmpty());
    }

    @Test
    public void testSkippedAndTrailingReferencesAreEmpty() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(1, 100, 100, offset(0, 0), offset(0, 10));
        builder.add(3, 100, 100, offset(0, 10), offset(0, 20));
        final BinningIndex index = builder.build(5);
        Assert.assertEquals(index.getReferenceCount(), 5);
        for (final int empty : new int[] {0, 2, 4})
            Assert.assertTrue(index.getReference(empty).isEmpty());
        for (final int populated : new int[] {1, 3})
            Assert.assertFalse(index.getReference(populated).isEmpty());
    }

    @Test
    public void testNoRecordsGivesAnIndexOfEmptyReferences() {
        final BinningIndex index = baiBuilder().build(2);
        Assert.assertEquals(index.getReferenceCount(), 2);
        Assert.assertTrue(index.getReference(0).isEmpty());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testReferencesOutOfOrderAreRejected() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(1, 100, 100, offset(0, 0), offset(0, 10));
        builder.add(0, 100, 100, offset(0, 10), offset(0, 20));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativeReferenceIndexIsRejected() {
        baiBuilder().add(-1, 100, 100, offset(0, 0), offset(0, 10));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testStartBelowOneIsRejected() {
        baiBuilder().add(0, 0, 10, offset(0, 0), offset(0, 10));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBuildingForFewerReferencesThanWereAddedIsRejected() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(2, 100, 100, offset(0, 0), offset(0, 10));
        builder.build(2);
    }

    @Test
    public void testReportedCountsBecomeTheReferenceMetadata() {
        final BinningIndex.Builder builder = baiBuilder().reportingRecordCounts();
        builder.add(0, 100, 150, offset(10, 0), offset(10, 50));
        builder.addRecordCounts(1, 0);
        builder.add(0, 200, 200, offset(10, 50), offset(10, 90));
        builder.addRecordCounts(0, 1);
        builder.add(0, 40_000, 40_100, offset(900, 0), offset(900, 70));
        builder.addRecordCounts(5, 2); // one entry may stand for many records, as a CRAM slice does

        final ReferenceBins.Metadata metadata =
                builder.build(1).getReference(0).getMetadata().orElseThrow();
        Assert.assertEquals(metadata, new ReferenceBins.Metadata(offset(10, 0), offset(900, 70), 6, 3));
    }

    @Test
    public void testReportedCountsStartAfreshForEachReference() {
        final BinningIndex.Builder builder = baiBuilder().reportingRecordCounts();
        builder.add(0, 100, 150, offset(10, 0), offset(10, 50));
        builder.addRecordCounts(3, 1);
        builder.add(2, 100, 150, offset(20, 0), offset(20, 50));
        builder.addRecordCounts(1, 0);
        final BinningIndex index = builder.build(3);

        Assert.assertEquals(index.getReference(0).getMetadata().orElseThrow().mappedCount(), 3);
        Assert.assertFalse(index.getReference(1).getMetadata().isPresent());
        final ReferenceBins.Metadata last = index.getReference(2).getMetadata().orElseThrow();
        Assert.assertEquals(last.mappedCount(), 1);
        Assert.assertEquals(last.unmappedCount(), 0);
    }

    @Test
    public void testReportingCountsRecordsANoCoordinateCountEvenOfZero() {
        Assert.assertEquals(
                baiBuilder()
                        .reportingRecordCounts()
                        .build(1)
                        .getNoCoordinateCount()
                        .orElseThrow(),
                0);
    }

    @Test
    public void testNoCoordinateRecordsAreSummed() {
        final BinningIndex.Builder builder = baiBuilder().reportingRecordCounts();
        builder.addNoCoordinateRecords(2);
        builder.addNoCoordinateRecords(5);
        Assert.assertEquals(builder.build(1).getNoCoordinateCount().orElseThrow(), 7);
    }

    @Test
    public void testWithoutReportedCountsThereIsNoMetadataOrNoCoordinateCount() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 100, 150, offset(10, 0), offset(10, 50));
        final BinningIndex index = builder.build(1);
        Assert.assertFalse(index.getReference(0).getMetadata().isPresent());
        Assert.assertFalse(index.getNoCoordinateCount().isPresent());
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testRecordCountsAreRejectedUnlessReportingWasAskedFor() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 100, 150, offset(10, 0), offset(10, 50));
        builder.addRecordCounts(1, 0);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testRecordCountsBeforeAnyRecordAreRejected() {
        baiBuilder().reportingRecordCounts().addRecordCounts(1, 0);
    }

    @Test
    public void testEmptyWindowsAreLeftUnsetWhenBuildingForMerging() {
        final BinningIndex.Builder builder = baiBuilder().forMerging();
        builder.add(0, 40_000, 40_100, offset(10, 0), offset(10, 50)); // third 16 kb window only
        Assert.assertEquals(builder.build(1).getReference(0).getLinearIndex(), new long[] {-1, -1, offset(10, 0)});
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativeRecordCountsAreRejected() {
        final BinningIndex.Builder builder = baiBuilder().reportingRecordCounts();
        builder.add(0, 100, 150, offset(10, 0), offset(10, 50));
        builder.addRecordCounts(1, -1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativeNoCoordinateCountIsRejected() {
        baiBuilder().reportingRecordCounts().addNoCoordinateRecords(-1);
    }

    // In the tests of folding, a record at 16,384-16,385 straddles the first two 16 kb bins and so goes in the first
    // 128 kb bin, 585, which is the parent of the 16 kb bins 4681 and 4682; one at 131,072-131,073 goes in the first
    // 1 Mb bin, 73, the parent of 585.

    @Test
    public void testBinSpanningLittleOfTheFileIsFoldedIntoItsParent() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 10, 10, offset(0, 0), offset(0, 50)); // bin 4681
        builder.add(0, 16_384, 16_385, offset(1_000, 0), offset(1_000, 50)); // its parent, bin 585
        Assert.assertEquals(
                binsOf(builder.build(1).getReference(0)),
                Map.of(
                        585,
                        List.of(
                                new Chunk(offset(0, 0), offset(0, 50)),
                                new Chunk(offset(1_000, 0), offset(1_000, 50)))));
    }

    @Test
    public void testSmallBinIsKeptWhenItsParentHasNoChunksOfItsOwn() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 10, 10, offset(0, 0), offset(0, 50)); // bin 4681
        builder.add(0, 131_072, 131_073, offset(1_000, 0), offset(1_000, 50)); // bin 73, its grandparent
        Assert.assertEquals(binsOf(builder.build(1).getReference(0)).keySet(), Set.of(73, 4681));
    }

    @Test
    public void testBinSpanningJustUnder64KbOfTheFileIsFolded() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 10, 10, offset(0, 0), offset(0, 50));
        builder.add(0, 20, 20, offset(65_535, 0), offset(65_535, 50));
        builder.add(0, 16_384, 16_385, offset(200_000, 0), offset(200_000, 50));
        Assert.assertEquals(binsOf(builder.build(1).getReference(0)).keySet(), Set.of(585));
    }

    @Test
    public void testBinSpanning64KbOfTheFileIsKept() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 10, 10, offset(0, 0), offset(0, 50));
        builder.add(0, 20, 20, offset(65_536, 0), offset(65_536, 50));
        builder.add(0, 16_384, 16_385, offset(200_000, 0), offset(200_000, 50));
        Assert.assertEquals(binsOf(builder.build(1).getReference(0)).keySet(), Set.of(585, 4681));
    }

    @Test
    public void testBinMadeUpByItsChildrenIsFoldedInItsTurn() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 10, 10, offset(0, 0), offset(0, 50)); // bin 4681
        builder.add(0, 16_384, 16_385, offset(0, 50), offset(0, 100)); // bin 585
        builder.add(0, 131_072, 131_073, offset(0, 100), offset(0, 150)); // bin 73
        Assert.assertEquals(
                binsOf(builder.build(1).getReference(0)), Map.of(73, List.of(new Chunk(offset(0, 0), offset(0, 150)))));
    }

    @Test
    public void testBinIsJudgedWithWhatItsChildrenGaveIt() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 10, 10, offset(0, 0), offset(0, 50)); // bin 4681, folded into 585
        builder.add(0, 16_384, 16_385, offset(100_000, 0), offset(100_000, 50)); // bin 585: small alone, not with 4681
        builder.add(0, 131_072, 131_073, offset(200_000, 0), offset(200_000, 50)); // bin 73
        Assert.assertEquals(binsOf(builder.build(1).getReference(0)).keySet(), Set.of(73, 585));
    }

    @Test
    public void testFoldedChunksAreFiledInFileOrder() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 16_384, 16_385, offset(0, 0), offset(0, 50)); // bin 585
        builder.add(0, 16_390, 16_390, offset(1_000, 0), offset(1_000, 50)); // bin 4682, between 585's two chunks
        builder.add(0, 32_768, 32_769, offset(2_000, 0), offset(2_000, 50)); // bin 585
        Assert.assertEquals(
                binsOf(builder.build(1).getReference(0)).get(585),
                List.of(
                        new Chunk(offset(0, 0), offset(0, 50)),
                        new Chunk(offset(1_000, 0), offset(1_000, 50)),
                        new Chunk(offset(2_000, 0), offset(2_000, 50))));
    }

    @Test
    public void testFoldedChunkLyingWithinOneOfItsParentsLeavesThatChunkWhole() {
        final BinningIndex.Builder builder = baiBuilder();
        // Bin 585's two records are in one block, so they are stored as one chunk, which covers the record of
        // bin 4682 that lies between them.
        builder.add(0, 16_384, 16_385, offset(0, 0), offset(0, 50)); // bin 585
        builder.add(0, 16_390, 16_390, offset(0, 50), offset(0, 100)); // bin 4682
        builder.add(0, 32_768, 32_769, offset(0, 100), offset(0, 150)); // bin 585
        Assert.assertEquals(
                binsOf(builder.build(1).getReference(0)),
                Map.of(585, List.of(new Chunk(offset(0, 0), offset(0, 150)))));
    }

    @Test
    public void testFoldedChunkStartingInTheBlockAfterOneOfItsParentsEndsInStaysApartAsInHtslib() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 16_384, 16_385, offset(5, 0), offset(5, 50)); // bin 585
        builder.add(0, 16_390, 16_390, offset(6, 0), offset(6, 50)); // bin 4682, folded into 585
        Assert.assertEquals(
                binsOf(builder.build(1).getReference(0)).get(585),
                List.of(new Chunk(offset(5, 0), offset(5, 50)), new Chunk(offset(6, 0), offset(6, 50))));
    }

    @Test
    public void testBinsAreNotFoldedWhenBuildingForMerging() {
        final BinningIndex.Builder builder = baiBuilder().forMerging();
        builder.add(0, 10, 10, offset(0, 0), offset(0, 50));
        builder.add(0, 16_384, 16_385, offset(1_000, 0), offset(1_000, 50));
        Assert.assertEquals(binsOf(builder.build(1).getReference(0)).keySet(), Set.of(585, 4681));
    }

    // moveEndOfLastRecord

    @Test
    public void testMoveEndOfLastRecordMovesALoneChunksEnd() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 10, 10, offset(0, 0), offset(0, 50));
        builder.moveEndOfLastRecord(offset(0, 100));
        Assert.assertEquals(
                binsOf(builder.build(1).getReference(0)).get(SMALLEST_BIN_0),
                List.of(new Chunk(offset(0, 0), offset(0, 100))));
    }

    @Test
    public void testMoveEndOfLastRecordMovesAJoinedChunksEnd() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 10, 10, offset(0, 0), offset(0, 50));
        builder.add(0, 20, 20, offset(0, 50), offset(0, 100));
        builder.moveEndOfLastRecord(offset(1, 0));
        Assert.assertEquals(
                binsOf(builder.build(1).getReference(0)).get(SMALLEST_BIN_0),
                List.of(new Chunk(offset(0, 0), offset(1, 0))));
    }

    @Test
    public void testMoveEndOfLastRecordUpdatesMetadataLastOffset() {
        final BinningIndex.Builder builder = baiBuilder().reportingRecordCounts();
        builder.add(0, 10, 10, offset(0, 0), offset(0, 50));
        builder.addRecordCounts(1, 0);
        builder.moveEndOfLastRecord(offset(5, 0));
        final ReferenceBins.Metadata metadata =
                builder.build(1).getReference(0).getMetadata().orElseThrow();
        Assert.assertEquals(metadata.lastOffset(), offset(5, 0));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testMoveEndOfLastRecordBeforeAnyAddIsRejected() {
        baiBuilder().moveEndOfLastRecord(offset(0, 100));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMoveEndOfLastRecordToAnEarlierOffsetIsRejected() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 10, 10, offset(0, 0), offset(0, 50));
        builder.moveEndOfLastRecord(offset(0, 40));
    }
}
