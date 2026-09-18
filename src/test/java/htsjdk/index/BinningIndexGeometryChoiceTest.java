package htsjdk.index;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link BinningIndex#csiGeometry}: the scheme tabix would pick for a given longest sequence. */
public class BinningIndexGeometryChoiceTest extends HtsjdkTest {

    @Test
    public void testHumanSizedSequenceGetsSixLevels() {
        Assert.assertEquals(BinningIndex.csiGeometry(14, 249_000_000), new BinningIndex.Geometry(14, 6));
    }

    @Test
    public void testWheatSizedSequenceStillFitsSixLevels() {
        Assert.assertEquals(BinningIndex.csiGeometry(14, 830_000_000), new BinningIndex.Geometry(14, 6));
    }

    @Test
    public void testSequenceJustUnderFourGigabasesFitsSixLevels() {
        Assert.assertEquals(BinningIndex.csiGeometry(14, (1L << 32) - 256), new BinningIndex.Geometry(14, 6));
    }

    @Test
    public void testSequenceAtFourGigabasesNeedsASeventhLevel() {
        Assert.assertEquals(BinningIndex.csiGeometry(14, 1L << 32), new BinningIndex.Geometry(14, 7));
    }

    @Test
    public void testSequenceBeyondNineLevelsWidensTheSmallestBins() {
        Assert.assertEquals(BinningIndex.csiGeometry(14, 1L << 41), new BinningIndex.Geometry(15, 9));
    }

    @Test
    public void testUnknownLengthGetsEightLevelsAtTheDefaultShift() {
        Assert.assertEquals(BinningIndex.csiGeometry(14, 0), new BinningIndex.Geometry(14, 8));
    }

    @Test
    public void testUnknownLengthGetsNineLevelsForSmallShifts() {
        Assert.assertEquals(BinningIndex.csiGeometry(9, 0), new BinningIndex.Geometry(9, 9));
    }

    @Test
    public void testUnknownLengthGetsFourLevelsForLargeShifts() {
        Assert.assertEquals(BinningIndex.csiGeometry(25, 0), new BinningIndex.Geometry(25, 4));
    }

    @Test
    public void testChosenSchemeIsAlwaysUsable() {
        for (int shift = 1; shift <= 30; shift++) {
            for (final long length : new long[] {0, 1_000, 1L << 29, 1L << 33}) {
                final BinningIndex.Geometry geometry = BinningIndex.csiGeometry(shift, length);
                new BinningIndex.Builder(geometry.minShift(), geometry.depth()); // validates
            }
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testShiftBelowOneIsRejected() {
        BinningIndex.csiGeometry(0, 1_000);
    }
}
