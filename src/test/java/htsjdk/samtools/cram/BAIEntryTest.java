package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.AlignmentContext;
import htsjdk.samtools.cram.structure.AlignmentSpan;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BAIEntryTest extends HtsjdkTest {

    @Test(expectedExceptions = CRAMException.class)
    public void testRejectMultiRefBAIEntry() {
        // A BAIEntry should never be made from a MULTI_REF reference context, because for a BAI index
        // MUTLI_REF slices need to be resolved down to constituent BAIEntry(s), one for each reference
        // container reference context, including unmapped
        new BAIEntry(
                ReferenceContext.MULTIPLE_REFERENCE_CONTEXT,
                new AlignmentSpan(
                        new AlignmentContext(new ReferenceContext(1), 1, 1),
                        1,
                        0,
                        0
                ),
                0,
                0L,
                0);
    }

    @DataProvider(name="badUnmappedUnplacedBAIEntry")
    public Object[][] getBadUnmappedUnplacedBAIEntry() {
        return new Object[][] {
                {
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        new AlignmentSpan(
                                new AlignmentContext(new ReferenceContext(1),
                                        2, // alignment start must be 0 or -1
                                        1),
                                1,
                                0,
                                0), 0, 0L, 0
                },
                {
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        new AlignmentSpan(
                                new AlignmentContext(new ReferenceContext(1),
                                        0,
                                        2), // alignment start must be 0 or 1
                                1,
                                0,
                                0), 0, 0L, 0
                },
        };
    }

    @Test(dataProvider = "badUnmappedUnplacedBAIEntry", expectedExceptions = CRAMException.class)
    public void testRejectBadUnmappedUnplacedBAIEntry(
        final ReferenceContext referenceContext,
        final AlignmentSpan alignmentSpan,
        final long containerOffset,
        final long sliceHeaderBlockByteOffset,
        final int landmarkIndex) {
        new BAIEntry(referenceContext, alignmentSpan, containerOffset, sliceHeaderBlockByteOffset, landmarkIndex);
    }
}
