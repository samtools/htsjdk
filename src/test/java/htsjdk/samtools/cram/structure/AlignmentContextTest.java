package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.build.CramIO;
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
                // context, expectedType, alignmentStart, alignmentSpan
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
                        AlignmentContext.UNMAPPED_UNPLACED_CONTEXT,
                        ReferenceContextType.UNMAPPED_UNPLACED_TYPE,
                        AlignmentContext.NO_ALIGNMENT_START,
                        AlignmentContext.NO_ALIGNMENT_SPAN
                },

                // special case for EOF Containers
                {
                        AlignmentContext.EOF_CONTAINER_CONTEXT,
                        ReferenceContextType.UNMAPPED_UNPLACED_TYPE,
                        CramIO.EOF_ALIGNMENT_START,
                        CramIO.EOF_ALIGNMENT_SPAN
                },

                // EOF Container Context created manually
                {
                        new AlignmentContext(
                                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                                CramIO.EOF_ALIGNMENT_START,
                                CramIO.EOF_ALIGNMENT_SPAN),
                        ReferenceContextType.UNMAPPED_UNPLACED_TYPE,
                        CramIO.EOF_ALIGNMENT_START,
                        CramIO.EOF_ALIGNMENT_SPAN
                },
        };
    }

    @Test(dataProvider = "validAlignmentContexts")
    public void validAlignmentContextTest(
            final AlignmentContext alnContext,
            final ReferenceContextType expectedRefContextType,
            final int expectedStart,
            final int expectedSpan) {
        Assert.assertEquals(alnContext.getReferenceContext().getType(), expectedRefContextType);
        Assert.assertEquals(alnContext.getAlignmentStart(), expectedStart);
        Assert.assertEquals(alnContext.getAlignmentSpan(), expectedSpan);
    }

    // TODO: for now, accept anything until CRAMBAIndexer fake slices are eliminated
//    @DataProvider(name = "invalidAlignmentContexts")
//    private static Object[][] invalidAlignmentContexts() {
//        return new Object[][] {
//                // referenceContext, start, span
//                { ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, 5, 6 },
//                // does not actually check for span for EOF Containers
//                // so we can use an arbitrary span value
//                { ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, CramIO.EOF_ALIGNMENT_START, 10 },
//                { ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, 7, 8 }
//        };
//    }
//
//    @Test(dataProvider = "invalidAlignmentContexts", expectedExceptions = IllegalArgumentException.class)
//    public void invalidAlignmentContextTest(final ReferenceContext refContext, final int start, final int span) {
//        new AlignmentContext(refContext, start, span);
//    }

}
