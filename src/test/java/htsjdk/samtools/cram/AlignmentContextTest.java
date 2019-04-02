package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.common.EOFConstants;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.ref.ReferenceContextType;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    // test that alignment contexts are sorted correctly:

    // first by numerical order of reference sequence ID, except:
    // - unmapped-unplaced comes last
    // - multiref throws an exception

    // next by alignment start, if the reference sequence ID is valid (placed single-ref)

    // alignmentSpan is irrelevant to index sorting

    @Test
    public void testCompareTo() {
        // unmapped contexts are not sortable, so they should remain in order 1, 2, 3

        final AlignmentContext unmapped1 = new AlignmentContext(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, 100, 100);
        final AlignmentContext unmapped2 = new AlignmentContext(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, 80, 3000);
        final AlignmentContext unmapped3 = new AlignmentContext(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, 80, 5);

        // these placed AlignmentContexts should sort per sequenceId as: 4, 2, 3, 1

        final ReferenceContext refContext0 = new ReferenceContext(0);
        final AlignmentContext placed1ForSeq0 = new AlignmentContext(refContext0, 30, 100);
        final AlignmentContext placed2ForSeq0 = new AlignmentContext(refContext0, 25, 200);
        final AlignmentContext placed3ForSeq0 = new AlignmentContext(refContext0, 25, 100);
        final AlignmentContext placed4ForSeq0 = new AlignmentContext(refContext0, 10, 400);

        final ReferenceContext refContext1 = new ReferenceContext(1);
        final AlignmentContext placed1ForSeq1 = new AlignmentContext(refContext1, 30, 100);
        final AlignmentContext placed2ForSeq1 = new AlignmentContext(refContext1, 25, 200);
        final AlignmentContext placed3ForSeq1 = new AlignmentContext(refContext1, 25, 100);
        final AlignmentContext placed4ForSeq1 = new AlignmentContext(refContext1, 10, 400);

        final List<AlignmentContext> testList = new ArrayList<AlignmentContext>() {{
            add(unmapped1);
            add(unmapped2);
            add(unmapped3);

            add(placed1ForSeq1);
            add(placed2ForSeq1);
            add(placed3ForSeq1);
            add(placed4ForSeq1);

            add(placed1ForSeq0);
            add(placed2ForSeq0);
            add(placed3ForSeq0);
            add(placed4ForSeq0);
        }};

        // ref ID 0, then ref ID 1, then unmapped
        // within valid ref ID = 4, 2, 3, 1 (see above)
        // within unmapped = 1, 2, 3 (no sorting)

        final List<AlignmentContext> expectedList = new ArrayList<AlignmentContext>() {{
            add(placed4ForSeq0);
            add(placed2ForSeq0);
            add(placed3ForSeq0);
            add(placed1ForSeq0);

            add(placed4ForSeq1);
            add(placed2ForSeq1);
            add(placed3ForSeq1);
            add(placed1ForSeq1);

            add(unmapped1);
            add(unmapped2);
            add(unmapped3);
        }};

        Collections.sort(testList);
        Assert.assertEquals(testList, expectedList);
    }

    @DataProvider(name = "compareToExceptions")
    private Object[][] compareToExceptions() {
        final AlignmentContext singleRefContext = new AlignmentContext(new ReferenceContext(0), 1, 1);
        return new Object[][] {
                {singleRefContext, AlignmentContext.MULTIPLE_REFERENCE_CONTEXT},
                {AlignmentContext.MULTIPLE_REFERENCE_CONTEXT, singleRefContext},
                {AlignmentContext.MULTIPLE_REFERENCE_CONTEXT, AlignmentContext.MULTIPLE_REFERENCE_CONTEXT},
        };
    }

    @Test(dataProvider = "compareToExceptions", expectedExceptions = CRAMException.class)
    public void compareToExceptionsTest(final AlignmentContext a, final AlignmentContext b) {
        a.compareTo(b);
    }
}
