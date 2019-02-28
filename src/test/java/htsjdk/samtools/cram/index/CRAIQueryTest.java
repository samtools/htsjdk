package htsjdk.samtools.cram.index;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.ref.ReferenceContext;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CRAIQueryTest extends HtsjdkTest {
    @DataProvider(name = "intersectionTestCases")
    public Object[][] intersectionTestCases() {
        final CRAIQuery basic = new CRAIQuery(1, 1, 10);
        final CRAIQuery overlapBasic = new CRAIQuery(1, 5, 10);
        final CRAIQuery insideBasic = new CRAIQuery(1, 3, 5);

        final CRAIQuery otherSeq1 = new CRAIQuery(2, 1, 10);
        final CRAIQuery otherSeq2 = new CRAIQuery(2, 2, 10);

        final CRAIQuery zerospan = new CRAIQuery(1, 1, 0);

        // start and span values are invalid here: show that they are ignored
        final CRAIQuery unmapped = new CRAIQuery(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, 1, 2);
        final CRAIQuery multi = new CRAIQuery(ReferenceContext.MULTIPLE_REFERENCE_ID, 1, 2);

        return new Object[][]{
                {basic, basic, true},
                {basic, overlapBasic, true},
                {basic, insideBasic, true},

                {basic, otherSeq1, false},
                {basic, otherSeq2, false},
                {otherSeq1, otherSeq2, true},

                {basic, zerospan, false},
                {zerospan, zerospan, false},

                // intersections with Unmapped or Multiref entries are always false, even with themselves

                {basic, unmapped, false},
                {basic, multi, false},
                {unmapped, multi, false},
                {unmapped, unmapped, false},
                {multi, multi, false},
        };
    }

    @Test(dataProvider = "intersectionTestCases")
    public void testIntersect(final CRAIQuery a, final CRAIQuery b, final boolean expectation) {
        Assert.assertEquals(a.intersect(b), expectation);
        Assert.assertEquals(b.intersect(a), expectation);
    }
}
