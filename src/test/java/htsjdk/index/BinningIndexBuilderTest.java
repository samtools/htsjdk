package htsjdk.index;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.Chunk;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link BinningIndex.Builder}: how records become bins, chunks and linear-index entries. */
public class BinningIndexBuilderTest extends HtsjdkTest {
    private static final int SMALLEST_BIN_0 = 4681; // first smallest bin of the BAI scheme

    private static long offset(final long blockAddress, final int withinBlock) {
        return BlockCompressedFilePointerUtil.makeFilePointer(blockAddress, withinBlock);
    }

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
    public void testLinearIndexWindowsWithoutRecordsTakeThePrecedingOffset() {
        final BinningIndex.Builder builder = baiBuilder();
        builder.add(0, 20_000, 20_000, offset(5, 0), offset(5, 10)); // window 1
        builder.add(0, 70_000, 70_000, offset(9, 0), offset(9, 10)); // window 4
        Assert.assertEquals(
                builder.build(1).getReference(0).getLinearIndex(),
                new long[] {0, offset(5, 0), offset(5, 0), offset(5, 0), offset(9, 0)});
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
}
