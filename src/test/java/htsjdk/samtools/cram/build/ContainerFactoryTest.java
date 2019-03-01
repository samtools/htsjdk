package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.CramCompressionRecordUtil;
import htsjdk.samtools.cram.structure.Slice;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Created by vadim on 15/12/2015.
 */
public class ContainerFactoryTest extends HtsjdkTest {
    private static final int TEST_RECORD_COUNT = 10;

    @DataProvider(name = "containerStateTests")
    private Object[][] containerStateTests() {
        final int mappedSequenceId = 0;  // arbitrary
        final ReferenceContext mappedRefContext = new ReferenceContext(mappedSequenceId);
        final int mappedAlignmentStart = 1;
        final int mappedAlignmentSpan = CramCompressionRecordUtil.READ_LENGTH_FOR_TEST_RECORDS + TEST_RECORD_COUNT - 1;

        return new Object[][]{
                {
                        CramCompressionRecordUtil.getSingleRefRecords(TEST_RECORD_COUNT, mappedSequenceId),
                        mappedRefContext, mappedAlignmentStart, mappedAlignmentSpan
                },
                {
                        CramCompressionRecordUtil.getMultiRefRecords(TEST_RECORD_COUNT),
                        ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },
                {
                        CramCompressionRecordUtil.getUnmappedRecords(TEST_RECORD_COUNT),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },

                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

                {
                        CramCompressionRecordUtil.getHalfUnmappedNoRefRecords(TEST_RECORD_COUNT),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },
                {
                        CramCompressionRecordUtil.getHalfUnmappedNoStartRecords(TEST_RECORD_COUNT, mappedSequenceId),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },

                // show that unmapped-unplaced reads cause a single ref slice/container to become multiref

                {
                        CramCompressionRecordUtil.getSingleRefRecordsWithOneUnmapped(TEST_RECORD_COUNT, mappedSequenceId),
                        ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },

                // show that unmapped-unplaced reads don't change the state of a multi-ref slice/container

                {
                        CramCompressionRecordUtil.getMultiRefRecordsWithOneUnmapped(TEST_RECORD_COUNT),
                        ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },
        };
    }

    @Test(dataProvider = "containerStateTests")
    public void testContainerState(final List<CramCompressionRecord> records,
                                   final ReferenceContext expectedReferenceContext,
                                   final int expectedAlignmentStart,
                                   final int expectedAlignmentSpan) {
        final Container container = buildFromNewFactory(records);
        assertContainerState(container, expectedReferenceContext, expectedAlignmentStart, expectedAlignmentSpan);
    }

    @Test
    public void testMultiRefWithStateTransitions() {
        final List<Container> containers = CramCompressionRecordUtil.getMultiRefContainersForStateTest();

        // first container is single-ref

        final ReferenceContext refContext = new ReferenceContext(0);
        final int alignmentStart = 1;
        final int alignmentSpan = CramCompressionRecordUtil.READ_LENGTH_FOR_TEST_RECORDS;
        int recordCount = 1;
        int globalRecordCount = 0; // first container - no records yet
        assertContainerState(containers.get(0), refContext, alignmentStart, alignmentSpan,
                globalRecordCount, recordCount, CramCompressionRecordUtil.READ_LENGTH_FOR_TEST_RECORDS * recordCount);

        // when other refs are added, subsequent containers are multiref

        recordCount++;  // this container has 2 records
        globalRecordCount = containers.get(0).nofRecords;   // we've seen 1 record before this container
        assertContainerState(containers.get(1), ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN,
                globalRecordCount, recordCount, CramCompressionRecordUtil.READ_LENGTH_FOR_TEST_RECORDS * recordCount);

        recordCount++;  // this container has 3 records
        globalRecordCount = containers.get(0).nofRecords + containers.get(1).nofRecords;    // we've seen 3 records before this container
        assertContainerState(containers.get(2), ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN,
                globalRecordCount, recordCount, CramCompressionRecordUtil.READ_LENGTH_FOR_TEST_RECORDS * recordCount);
    }

    private Container buildFromNewFactory(final List<CramCompressionRecord> records) {
        final ContainerFactory factory = new ContainerFactory(CramCompressionRecordUtil.getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
        return factory.buildContainer(records);
    }

    private void assertContainerState(final Container container,
                                      final ReferenceContext referenceContext,
                                      final int alignmentStart,
                                      final int alignmentSpan) {
        final int globalRecordCounter = 0; // first Container
        final int baseCount = TEST_RECORD_COUNT * CramCompressionRecordUtil.READ_LENGTH_FOR_TEST_RECORDS;

        assertContainerState(container, referenceContext, alignmentStart, alignmentSpan, globalRecordCounter, TEST_RECORD_COUNT, baseCount);
    }

    private static void assertContainerState(final Container container,
                                             final ReferenceContext referenceContext,
                                             final int alignmentStart,
                                             final int alignmentSpan,
                                             final int globalRecordCounter,
                                             final int recordCount,
                                             final int baseCount) {
        Assert.assertNotNull(container);
        Assert.assertEquals(container.getReferenceContext(), referenceContext);
        Assert.assertEquals(container.alignmentStart, alignmentStart);
        Assert.assertEquals(container.alignmentSpan, alignmentSpan);
        Assert.assertEquals(container.nofRecords, recordCount);
        Assert.assertEquals(container.globalRecordCounter, globalRecordCounter);
        Assert.assertEquals(container.bases, baseCount);

        Assert.assertEquals(container.slices.length, 1);

        // verify the underlying slice too

        final Slice slice = container.slices[0];
        Assert.assertEquals(slice.getReferenceContext(), container.getReferenceContext());
        Assert.assertEquals(slice.globalRecordCounter, globalRecordCounter);
        Assert.assertEquals(slice.alignmentStart, alignmentStart);
        Assert.assertEquals(slice.alignmentSpan, alignmentSpan);
        Assert.assertEquals(slice.nofRecords, recordCount);
        Assert.assertEquals(slice.globalRecordCounter, globalRecordCounter);
        Assert.assertEquals(slice.bases, baseCount);
    }
}
