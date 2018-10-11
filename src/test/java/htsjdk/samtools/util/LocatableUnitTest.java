package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class LocatableUnitTest extends HtsjdkTest {

  private static Locatable getLocatable(final String contig, final int start, final int end) {
    return new Locatable() {
      @Override
      public String getContig() {
        return contig;
      }

      @Override
      public int getStart() {
        return start;
      }

      @Override
      public int getEnd() {
        return end;
      }

      @Override
      public String toString() {
        return String.format("%s:%s-%s", contig, start, end);
      }
    };
  }

  @DataProvider(name = "IntervalSizeData")
  public Object[][] getIntervalSizeData() {
    // Intervals + expected sizes
    return new Object[][] {
      {getLocatable("1", 1, 1), 1},
      {getLocatable("1", 1, 2), 2},
      {getLocatable("1", 1, 10), 10},
      {getLocatable("1", 2, 10), 9},
      {getLocatable("1", 1, 0), 0}
    };
  }

  @Test(dataProvider = "IntervalSizeData")
  public void testGetSize(final Locatable interval, final int expectedSize) {
    Assert.assertEquals(
        interval.getLengthOnReference(), expectedSize, "size() incorrect for interval " + interval);
  }

  @DataProvider(name = "IntervalOverlapData")
  public static Object[][] getIntervalOverlapData() {
    final Locatable standardInterval = getLocatable("1", 10, 20);
    final Locatable oneBaseInterval = getLocatable("1", 10, 10);

    return new Object[][] {
      {standardInterval, getLocatable("2", 10, 20), false},
      {standardInterval, getLocatable("1", 1, 5), false},
      {standardInterval, getLocatable("1", 1, 9), false},
      {standardInterval, getLocatable("1", 1, 10), true},
      {standardInterval, getLocatable("1", 1, 15), true},
      {standardInterval, getLocatable("1", 10, 10), true},
      {standardInterval, getLocatable("1", 10, 15), true},
      {standardInterval, getLocatable("1", 10, 20), true},
      {standardInterval, getLocatable("1", 15, 20), true},
      {standardInterval, getLocatable("1", 15, 25), true},
      {standardInterval, getLocatable("1", 20, 20), true},
      {standardInterval, getLocatable("1", 20, 25), true},
      {standardInterval, getLocatable("1", 21, 25), false},
      {standardInterval, getLocatable("1", 25, 30), false},
      {oneBaseInterval, getLocatable("2", 10, 10), false},
      {oneBaseInterval, getLocatable("1", 1, 5), false},
      {oneBaseInterval, getLocatable("1", 1, 9), false},
      {oneBaseInterval, getLocatable("1", 1, 10), true},
      {oneBaseInterval, getLocatable("1", 10, 10), true},
      {oneBaseInterval, getLocatable("1", 10, 15), true},
      {oneBaseInterval, getLocatable("1", 11, 15), false},
      {oneBaseInterval, getLocatable("1", 15, 20), false},
      {standardInterval, null, false},
      {standardInterval, standardInterval, true},
    };
  }

  @Test(dataProvider = "IntervalOverlapData")
  public void testOverlap(
      final Locatable firstInterval,
      final Locatable secondInterval,
      final boolean expectedOverlapResult) {
    Assert.assertEquals(
        firstInterval.overlaps(secondInterval),
        expectedOverlapResult,
        "overlap() returned incorrect result for intervals "
            + firstInterval
            + " and "
            + secondInterval);
  }

  @DataProvider(name = "overlapsWithMargin")
  public Object[][] overlapsWithMargin() {
    final Locatable standardInterval = getLocatable("1", 10, 20);
    final Locatable middleInterval = getLocatable("1", 100, 200);
    final Locatable zeroLengthInterval = getLocatable("1", 1, 0);

    return new Object[][] {
      {standardInterval, getLocatable("2", 10, 20), 100, false},
      {standardInterval, getLocatable("1", 1, 15), 0, true},
      {standardInterval, getLocatable("1", 30, 50), 9, false},
      {standardInterval, getLocatable("1", 30, 50), 10, true},
      {middleInterval, getLocatable("1", 50, 99), 0, false},
      {middleInterval, getLocatable("1", 50, 90), 9, false},
      {middleInterval, getLocatable("1", 50, 90), 10, true},
      {middleInterval, getLocatable("1", 150, 149), 0, true},
      {middleInterval, getLocatable("1", 100, 99), 0, true},
      {middleInterval, getLocatable("1", 99, 98), 0, false},
      {standardInterval, getLocatable(null, 10, 20), 100, false}
    };
  }

  @Test(dataProvider = "overlapsWithMargin")
  public void testOverlapWithMargin(
      final Locatable firstInterval,
      final Locatable secondInterval,
      int margin,
      final boolean expectedOverlapResult) {
    Assert.assertEquals(
        firstInterval.withinDistanceOf(secondInterval, margin),
        expectedOverlapResult,
        "overlap() returned incorrect result for intervals "
            + firstInterval
            + " and "
            + secondInterval);
  }

  @DataProvider(name = "IntervalContainsData")
  public Object[][] getIntervalContainsData() {
    final Locatable containingInterval = getLocatable("1", 10, 20);
    final Locatable zeroLengthIntervalBetween9And10 = getLocatable("1", 10, 9);
    return new Object[][] {
      {containingInterval, getLocatable("2", 10, 20), false},
      {containingInterval, getLocatable("1", 1, 5), false},
      {containingInterval, getLocatable("1", 1, 10), false},
      {containingInterval, getLocatable("1", 5, 15), false},
      {containingInterval, getLocatable("1", 9, 10), false},
      {containingInterval, getLocatable("1", 9, 20), false},
      {containingInterval, getLocatable("1", 10, 10), true},
      {containingInterval, getLocatable("1", 10, 15), true},
      {containingInterval, getLocatable("1", 10, 20), true},
      {containingInterval, getLocatable("1", 10, 21), false},
      {containingInterval, getLocatable("1", 15, 25), false},
      {containingInterval, getLocatable("1", 20, 20), true},
      {containingInterval, getLocatable("1", 20, 21), false},
      {containingInterval, getLocatable("1", 20, 25), false},
      {containingInterval, getLocatable("1", 21, 25), false},
      {containingInterval, getLocatable("1", 25, 30), false},
      {containingInterval, null, false},
      {containingInterval, containingInterval, true},
      {containingInterval, getLocatable(null, 10, 20), false},
      {getLocatable(null, 10, 20), getLocatable(null, 10, 20), false},

      // 0 length intervals
      {containingInterval, zeroLengthIntervalBetween9And10, true},
      {containingInterval, getLocatable("1", 15, 14), true},
      {containingInterval, getLocatable("1", 21, 20), true},
      {containingInterval, getLocatable("1", 25, 24), false},
      {zeroLengthIntervalBetween9And10, getLocatable("1", 9, 8), false},
      {zeroLengthIntervalBetween9And10, getLocatable("1", 11, 10), false},

      // 0 length interval is considered to contain itself
      {zeroLengthIntervalBetween9And10, zeroLengthIntervalBetween9And10, true}
    };
  }

  @Test(dataProvider = "IntervalContainsData")
  public void testContains(
      final Locatable firstInterval,
      final Locatable secondInterval,
      final boolean expectedContainsResult) {
    Assert.assertEquals(
        firstInterval.contains(secondInterval),
        expectedContainsResult,
        "contains() returned incorrect result for intervals "
            + firstInterval
            + " and "
            + secondInterval);
  }
}
