package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.ref.ReferenceContext;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ContainerTest extends HtsjdkTest {
    private static final int TEST_RECORD_COUNT = 10;
    private static final int READ_LENGTH_FOR_TEST_RECORDS = CRAMStructureTestUtil.READ_LENGTH_FOR_TEST_RECORDS;

    private static final ContainerFactory FACTORY = new ContainerFactory(CRAMStructureTestUtil.getSAMFileHeaderForTests(), TEST_RECORD_COUNT);

    @DataProvider(name = "containerStateTestCases")
    private Object[][] containerStateTestCases() {
        final ReferenceContext mappedReferenceContext = new ReferenceContext(5); // arbitrary

        final int slice1AlignmentStart = 10;
        final int slice1AlignmentSpan = 15;
        final Slice mappedSlice1 = new Slice(mappedReferenceContext);
        mappedSlice1.alignmentStart = slice1AlignmentStart;
        mappedSlice1.alignmentSpan = slice1AlignmentSpan;

        final int slice2AlignmentStart = 20;
        final int slice2AlignmentSpan = 20;
        final Slice mappedSlice2 = new Slice(mappedReferenceContext);
        mappedSlice2.alignmentStart = slice2AlignmentStart;
        mappedSlice2.alignmentSpan = slice2AlignmentSpan;
        final int expectedSpan = slice2AlignmentStart + slice2AlignmentSpan - slice1AlignmentStart;

        return new Object[][] {
                {
                    Arrays.asList(mappedSlice1), mappedReferenceContext, slice1AlignmentStart, slice1AlignmentSpan
                },
                {
                    Arrays.asList(mappedSlice1, mappedSlice2), mappedReferenceContext, slice1AlignmentStart, expectedSpan
                },
                {
                    Arrays.asList(new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT),
                            new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT)),
                    ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },
                {
                    Arrays.asList(new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT),
                            new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)),
                    ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },
        };
    }

    @Test(dataProvider = "containerStateTestCases")
    public void initializeFromSlicesTest(final List<Slice> slices,
                                         final ReferenceContext expectedReferenceContext,
                                         final int expectedAlignmentStart,
                                         final int expectedAlignmentSpan) {
        final Container container = Container.initializeFromSlices(slices);
        CRAMStructureTestUtil.assertContainerState(container, expectedReferenceContext, expectedAlignmentStart, expectedAlignmentSpan);
    }

    @DataProvider(name = "illegalCombinationTestCases")
    private Object[][] illegalCombinationTestCases() {
        return new Object[][] {
                {
                        new Slice(new ReferenceContext(0)),
                        new Slice(new ReferenceContext(1))
                },
                {
                        new Slice(new ReferenceContext(0)),
                        new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        new Slice(new ReferenceContext(0)),
                        new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT)
                },
                {
                        new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT),
                        new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT)
                },
        };
    }

    @Test(dataProvider = "illegalCombinationTestCases", expectedExceptions = CRAMException.class)
    public static void illegalCombinationsStateTest(final Slice one, final Slice another) {
        Container.initializeFromSlices(Arrays.asList(one, another));
    }

    @DataProvider(name = "getSpansTestCases")
    private Object[][] getSpansTestCases() {
        final int mappedSequenceId = 0;  // arbitrary
        final ReferenceContext mappedRefContext = new ReferenceContext(mappedSequenceId);
        final int expectedMappedSpan = READ_LENGTH_FOR_TEST_RECORDS + TEST_RECORD_COUNT - 1;

        // getSingleRefRecords() sets half of its records to mapped, half to unmapped
        final int halfOfRecordCount = TEST_RECORD_COUNT / 2;

        return new Object[][]{
                {
                        CRAMStructureTestUtil.getSingleRefRecords(TEST_RECORD_COUNT, mappedSequenceId),
                        mappedRefContext,
                        new AlignmentSpan(1, expectedMappedSpan, halfOfRecordCount, halfOfRecordCount)
                },
                {
                        CRAMStructureTestUtil.getUnplacedRecords(TEST_RECORD_COUNT),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        AlignmentSpan.UNPLACED_SPAN
                },

                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

                {
                        CRAMStructureTestUtil.getUnplacedRecords(TEST_RECORD_COUNT),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        AlignmentSpan.UNPLACED_SPAN
                },

                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

                {
                        CRAMStructureTestUtil.getHalfUnmappedNoRefRecords(TEST_RECORD_COUNT),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        AlignmentSpan.UNPLACED_SPAN
                },
                {
                        CRAMStructureTestUtil.getHalfUnmappedNoStartRecords(TEST_RECORD_COUNT, mappedSequenceId),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        AlignmentSpan.UNPLACED_SPAN
                },
        };
    }

    @Test(dataProvider = "getSpansTestCases")
    public void getSpansTest(final List<CramCompressionRecord> records,
                             final ReferenceContext expectedReferenceContext,
                             final AlignmentSpan expectedAlignmentSpan) {
        final Container container = FACTORY.buildContainer(records);

        final Map<ReferenceContext, AlignmentSpan> spanMap = container.getSpans(ValidationStringency.STRICT);
        Assert.assertEquals(spanMap.size(), 1);
        Assert.assertTrue(spanMap.containsKey(expectedReferenceContext));
        Assert.assertEquals(spanMap.get(expectedReferenceContext), expectedAlignmentSpan);
    }

}
