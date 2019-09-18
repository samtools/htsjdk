package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AlignmentSpanTest extends HtsjdkTest {
    @DataProvider(name = "combineTests")
    private Object[][] combineTests() {
        return new Object[][] {

                // same span, twice
                {
                        new AlignmentSpan(1, 10, 7, 9, 3),
                        new AlignmentSpan(1, 10, 7, 9, 3),
                        1, 10, 14, 18, 6
                },
                // overlapping spans: 1-5 and 3-9 -> 1-9
                {
                        new AlignmentSpan(1, 5, 100, 200, 4),
                        new AlignmentSpan(3, 7, 50, 70, 5),
                        1, 9, 150, 270, 9
                },
                // non-overlapping spans: 1-2 and 4-5 -> 1-5
                {
                        new AlignmentSpan(1, 2, 10, 20, 5),
                        new AlignmentSpan(4, 2, 5, 7, 6),
                        1, 5, 15, 27, 11
                }
        };
    }

    @Test(dataProvider = "combineTests")
    public void testCombine(final AlignmentSpan span1,
                        final AlignmentSpan span2,
                        final int expectedStart,
                        final int expectedSpan,
                        final int expectedMapped,
                        final int expectedUnmapped,
                        final int expectedUnmappedUnplaced) {
        final AlignmentSpan combined = AlignmentSpan.combine(span1, span2);

        Assert.assertEquals(combined.getAlignmentStart(), expectedStart);
        Assert.assertEquals(combined.getAlignmentSpan(), expectedSpan);
        Assert.assertEquals(combined.getMappedCount(), expectedMapped);
        Assert.assertEquals(combined.getUnmappedCount(), expectedUnmapped);
        Assert.assertEquals(combined.getUnmappedUnplacedCount(), expectedUnmappedUnplaced);

        final AlignmentSpan reverseCombined = AlignmentSpan.combine(span2, span1);
        Assert.assertEquals(reverseCombined, combined);
    }
}
