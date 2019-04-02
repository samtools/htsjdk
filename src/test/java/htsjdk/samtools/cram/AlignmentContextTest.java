package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.common.EOFConstants;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.ref.ReferenceContextType;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AlignmentContextTest extends HtsjdkTest {

    // test constructors and basic identity methods

    @DataProvider(name = "validAlignmentContexts")
    private static Object[][] validAlignmentContexts() {
        return new Object[][] {
                //context, expectedType, alignmentStart, alignmentSpan
                {
                        new AlignmentContext(new ReferenceContext(0), 1, 2),
                        ReferenceContextType.SINGLE_REFERENCE_TYPE,
                        1, 2
                },
                {
                        new AlignmentContext(new ReferenceContext(1), 3, 4),
                        ReferenceContextType.SINGLE_REFERENCE_TYPE,
                        3, 4
                },
                {
                        new AlignmentContext(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, 5, 6),
                        ReferenceContextType.UNMAPPED_UNPLACED_TYPE,
                        AlignmentContext.UNINITIALIZED, AlignmentContext.UNINITIALIZED
                },
                {
                        AlignmentContext.UNMAPPED_UNPLACED_CONTEXT,
                        ReferenceContextType.UNMAPPED_UNPLACED_TYPE,
                        AlignmentContext.UNINITIALIZED, AlignmentContext.UNINITIALIZED
                },

                // special case for EOF Containers

                {
                        AlignmentContext.EOF_CONTAINER_CONTEXT,
                        ReferenceContextType.UNMAPPED_UNPLACED_TYPE,
                        EOFConstants.EOF_ALIGNMENT_START, EOFConstants.EOF_ALIGNMENT_SPAN
                },

                // EOF Container Context created manually

                {
                        new AlignmentContext(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, EOFConstants.EOF_ALIGNMENT_START, EOFConstants.EOF_ALIGNMENT_SPAN),
                        ReferenceContextType.UNMAPPED_UNPLACED_TYPE,
                        EOFConstants.EOF_ALIGNMENT_START, EOFConstants.EOF_ALIGNMENT_SPAN
                },

                // does not actually check for span for EOF Containers
                // so we can use an arbitrary span value

                {
                        new AlignmentContext(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, EOFConstants.EOF_ALIGNMENT_START, 10),
                        ReferenceContextType.UNMAPPED_UNPLACED_TYPE,
                        EOFConstants.EOF_ALIGNMENT_START, EOFConstants.EOF_ALIGNMENT_SPAN
                },

                {
                        new AlignmentContext(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, 7, 8),
                        ReferenceContextType.MULTIPLE_REFERENCE_TYPE,
                        AlignmentContext.UNINITIALIZED, AlignmentContext.UNINITIALIZED
                },
                {
                        AlignmentContext.MULTIPLE_REFERENCE_CONTEXT,
                        ReferenceContextType.MULTIPLE_REFERENCE_TYPE,
                        AlignmentContext.UNINITIALIZED, AlignmentContext.UNINITIALIZED
                },
        };
    }

    @Test(dataProvider = "validAlignmentContexts")
    public void alignmentContextTest(
            final AlignmentContext alnContext,
            final ReferenceContextType expectedRefContextType,
            final int expectedStart,
            final int expectedSpan) {
        Assert.assertEquals(alnContext.getReferenceContext().getType(), expectedRefContextType);
        Assert.assertEquals(alnContext.getAlignmentStart(), expectedStart);
        Assert.assertEquals(alnContext.getAlignmentSpan(), expectedSpan);
    }
}
