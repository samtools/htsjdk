package htsjdk.index;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * {@link BinningIndex#csiGeometry} and {@link BinningIndex#shallowestCsiGeometry}: the schemes tabix and samtools
 * would pick for a given longest sequence.
 */
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

    @Test
    public void testSamtoolsGivesAHumanSizedSequenceTheBaiScheme() {
        Assert.assertEquals(BinningIndex.shallowestCsiGeometry(14, 249_000_000), new BinningIndex.Geometry(14, 5));
    }

    @Test
    public void testSamtoolsGivesAShortSequenceASingleLevel() {
        // what samtools index -c writes for a 100 kb reference
        Assert.assertEquals(BinningIndex.shallowestCsiGeometry(14, 100_000), new BinningIndex.Geometry(14, 1));
    }

    @Test
    public void testSamtoolsSchemeNeverHasFewerThanOneLevel() {
        Assert.assertEquals(BinningIndex.shallowestCsiGeometry(14, 1_000), new BinningIndex.Geometry(14, 1));
        Assert.assertEquals(BinningIndex.shallowestCsiGeometry(14, 0), new BinningIndex.Geometry(14, 1));
    }

    @Test
    public void testSamtoolsAddsASixthLevelBeyondTheBaiLimit() {
        Assert.assertEquals(BinningIndex.shallowestCsiGeometry(14, 1L << 29), new BinningIndex.Geometry(14, 6));
        Assert.assertEquals(BinningIndex.shallowestCsiGeometry(14, 830_000_000), new BinningIndex.Geometry(14, 6));
    }

    @Test
    public void testSamtoolsSlackTipsASequenceJustUnderTheLimitIntoTheNextLevel() {
        Assert.assertEquals(BinningIndex.shallowestCsiGeometry(14, (1L << 29) - 256), new BinningIndex.Geometry(14, 5));
        Assert.assertEquals(BinningIndex.shallowestCsiGeometry(14, (1L << 29) - 255), new BinningIndex.Geometry(14, 6));
    }

    @Test
    public void testSamtoolsSchemeHonoursTheRequestedShift() {
        Assert.assertEquals(BinningIndex.shallowestCsiGeometry(12, 249_000_000), new BinningIndex.Geometry(12, 6));
    }

    @Test
    public void testEveryShiftThatLeavesRoomForALevelGivesAUsableScheme() {
        for (int shift = 1; shift <= 59; shift++) {
            for (final long length : new long[] {0, 1_000, 1L << 29, 1L << 45, (1L << 62) - 256}) {
                for (final BinningIndex.Geometry geometry : new BinningIndex.Geometry[] {
                    BinningIndex.csiGeometry(shift, length), BinningIndex.shallowestCsiGeometry(shift, length)
                }) {
                    new BinningIndex.Builder(geometry.minShift(), geometry.depth()); // validates
                    Assert.assertTrue(
                            1L << geometry.minShift() + 3 * geometry.depth() >= length,
                            geometry + " does not reach " + length);
                }
            }
        }
    }

    @Test
    public void testWideSmallestBinsLeaveRoomForFewerLevels() {
        // eight levels are all a shift of 36 has room for, and with the slack 2^61 bases need a span of 2^62
        Assert.assertEquals(BinningIndex.shallowestCsiGeometry(36, 1L << 61), new BinningIndex.Geometry(38, 8));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testShiftThatLeavesNoRoomForALevelIsRejected() {
        BinningIndex.shallowestCsiGeometry(60, 1_000);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSequenceBeyondAnySchemeIsRejected() {
        BinningIndex.csiGeometry(14, 1L << 62);
    }
}
