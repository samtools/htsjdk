package htsjdk.index;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.BAMFileSpan;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link RenumberedReferenceBinsSource}: an index asked under another numbering of its references. */
public class RenumberedReferenceBinsSourceTest extends HtsjdkTest {
    private static final int MIN_SHIFT = BinningIndex.BAI_MIN_SHIFT;
    private static final int DEPTH = BinningIndex.BAI_DEPTH;

    /** An index of two references, the first with records near position 1,000 and the second near 50,000. */
    private static BinningIndex twoReferences() {
        return new IndexedRecords()
                .add(0, 1_000, 1_100)
                .add(0, 1_200, 1_300)
                .add(1, 50_000, 50_100)
                .index(MIN_SHIFT, DEPTH, 2);
    }

    @Test
    public void testAReferenceIsFoundUnderItsNewNumber() {
        final BinningIndex index = twoReferences();
        final RenumberedReferenceBinsSource renumbered =
                new RenumberedReferenceBinsSource(index, new int[] {-1, 0, -1, 1});

        Assert.assertEquals(renumbered.getReferenceCount(), 4);
        Assert.assertEquals(renumbered.getReference(1), index.getReference(0));
        Assert.assertEquals(renumbered.getReference(3), index.getReference(1));
        final BAMFileSpan span = (BAMFileSpan) renumbered.getSpanOverlapping(3, 49_000, 51_000);
        Assert.assertEquals(
                span.getChunks(), index.getSpanOverlapping(1, 49_000, 51_000).getChunks());
        Assert.assertFalse(span.isEmpty());
    }

    @Test
    public void testAReferenceTheIndexLacksIsEmpty() {
        final RenumberedReferenceBinsSource renumbered =
                new RenumberedReferenceBinsSource(twoReferences(), new int[] {-1, 0, -1, 1});

        Assert.assertTrue(renumbered.getReference(0).isEmpty());
        Assert.assertTrue(renumbered.getSpanOverlapping(0, 1, 100_000).isEmpty());
        Assert.assertTrue(renumbered.getSpanOverlapping(2, 1, 100_000).isEmpty());
    }

    @Test
    public void testAReferenceBeyondTheNumberingIsEmpty() {
        final RenumberedReferenceBinsSource renumbered =
                new RenumberedReferenceBinsSource(twoReferences(), new int[] {1, 0});

        Assert.assertTrue(renumbered.getSpanOverlapping(2, 1, 100_000).isEmpty());
        Assert.assertTrue(renumbered.getSpanOverlapping(-1, 1, 100_000).isEmpty());
    }

    @Test
    public void testLoadAllGivesTheWholeIndexUnderTheNewNumbering() {
        final BinningIndex index = twoReferences();
        final BinningIndex loaded = new RenumberedReferenceBinsSource(index, new int[] {1, -1, 0}).loadAll();

        Assert.assertEquals(loaded.getReferenceCount(), 3);
        Assert.assertEquals(loaded.getReference(0), index.getReference(1));
        Assert.assertTrue(loaded.getReference(1).isEmpty());
        Assert.assertEquals(loaded.getReference(2), index.getReference(0));
        Assert.assertEquals(loaded.getMinShift(), MIN_SHIFT);
        Assert.assertEquals(loaded.getDepth(), DEPTH);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testANumberTheIndexDoesNotHaveIsRejected() {
        new RenumberedReferenceBinsSource(twoReferences(), new int[] {0, 2});
    }
}
