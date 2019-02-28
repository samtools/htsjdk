package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AlignmentSpanTest extends HtsjdkTest {
    @DataProvider(name = "addTests")
    private Object[][] addTests() {
        return new Object[][] {
                // same span, twice
                {
                        new AlignmentSpan(1, 10, 7, 9),
                        new AlignmentSpan(1, 10, 7, 9),
                        1, 10, 14, 18
                },
                // overlapping spans: 1-5 and 3-9 -> 1-9
                {
                        new AlignmentSpan(1, 5, 100, 200),
                        new AlignmentSpan(3, 7, 50, 70),
                        1, 9, 150, 270
                },
                // non-overlapping spans: 1-2 and 4-5 -> 1-5
                {
                        new AlignmentSpan(1, 2, 10, 20),
                        new AlignmentSpan(4, 2, 5, 7),
                        1, 5, 15, 27
                }
        };
    }

    @Test(dataProvider = "addTests")
    public void addTest(final AlignmentSpan span1,
                        final AlignmentSpan span2,
                        final int expectedStart,
                        final int expectedSpan,
                        final int expectedMapped,
                        final int expectedUnmapped) {
        final AlignmentSpan combined = AlignmentSpan.add(span1, span2);
        Assert.assertEquals(combined.getStart(), expectedStart);
        Assert.assertEquals(combined.getSpan(), expectedSpan);
        Assert.assertEquals(combined.getMappedCount(), expectedMapped);
        Assert.assertEquals(combined.getUnmappedCount(), expectedUnmapped);

        final AlignmentSpan reverseCombined = AlignmentSpan.add(span2, span1);
        Assert.assertEquals(reverseCombined, combined);
    }
}
