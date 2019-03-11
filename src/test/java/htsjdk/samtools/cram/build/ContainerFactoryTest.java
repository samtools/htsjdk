package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.CRAMStructureTestUtil;
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
    private static final int READ_LENGTH_FOR_TEST_RECORDS = CRAMStructureTestUtil.READ_LENGTH_FOR_TEST_RECORDS;

    @Test
    public void recordsPerSliceTest() {
        final int recordsPerSlice = 100;
        final ContainerFactory factory = new ContainerFactory(CRAMStructureTestUtil.getSAMFileHeaderForTests(), recordsPerSlice);

        // build a container with the max records per slice

        final List<CramCompressionRecord> records = CRAMStructureTestUtil.getSingleRefRecords(recordsPerSlice, 0);
        final Container container = factory.buildContainer(records);

        Assert.assertEquals(container.nofRecords, recordsPerSlice);
        Assert.assertEquals(container.slices.length, 1);
        Assert.assertEquals(container.slices[0].nofRecords, recordsPerSlice);

        // build a container with 1 too many records to fit into a slice
        // 2 slices: recordsPerSlice records and 1 record

        records.add(CRAMStructureTestUtil.createMappedRecord(recordsPerSlice, 0, 1));
        final Container container2 = factory.buildContainer(records);

        Assert.assertEquals(container2.nofRecords, recordsPerSlice + 1);
        Assert.assertEquals(container2.slices.length, 2);
        Assert.assertEquals(container2.slices[0].nofRecords, recordsPerSlice);
        Assert.assertEquals(container2.slices[1].nofRecords, 1);
    }

    @DataProvider(name = "containerStateTests")
    private Object[][] containerStateTests() {
        final int mappedSequenceId = 0;  // arbitrary
        final ReferenceContext mappedRefContext = new ReferenceContext(mappedSequenceId);
        final int mappedAlignmentStart = 1;
        // record spans:
        // [1 to READ_LENGTH_FOR_TEST_RECORDS]
        // [2 to READ_LENGTH_FOR_TEST_RECORDS + 1]
        // up to [TEST_RECORD_COUNT to READ_LENGTH_FOR_TEST_RECORDS + TEST_RECORD_COUNT - 1]
        final int mappedAlignmentSpan = READ_LENGTH_FOR_TEST_RECORDS + TEST_RECORD_COUNT - 1;

        return new Object[][]{
                {
                        CRAMStructureTestUtil.getSingleRefRecords(TEST_RECORD_COUNT, mappedSequenceId),
                        mappedRefContext, mappedAlignmentStart, mappedAlignmentSpan
                },
                {
                        CRAMStructureTestUtil.getMultiRefRecords(TEST_RECORD_COUNT),
                        ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },
                {
                        CRAMStructureTestUtil.getUnplacedRecords(TEST_RECORD_COUNT),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },

                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

                {
                        CRAMStructureTestUtil.getHalfUnplacedNoRefRecords(TEST_RECORD_COUNT),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },
                {
                        CRAMStructureTestUtil.getHalfUnplacedNoStartRecords(TEST_RECORD_COUNT, mappedSequenceId),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },

                // show that unmapped-unplaced reads cause a single ref slice/container to become multiref

                {
                        CRAMStructureTestUtil.getSingleRefRecordsWithOneUnmapped(TEST_RECORD_COUNT, mappedSequenceId),
                        ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },

                // show that unmapped-unplaced reads don't change the state of a multi-ref slice/container

                {
                        CRAMStructureTestUtil.getMultiRefRecordsWithOneUnmapped(TEST_RECORD_COUNT),
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
        final int globalRecordCounter = 0; // first Container
        final int baseCount = TEST_RECORD_COUNT * READ_LENGTH_FOR_TEST_RECORDS;

        CRAMStructureTestUtil.assertContainerState(container, expectedReferenceContext,
                expectedAlignmentStart, expectedAlignmentSpan,
                TEST_RECORD_COUNT, baseCount, globalRecordCounter);
    }

    @Test
    public void testMultiRefWithStateTransitions() {
        final List<Container> containers = CRAMStructureTestUtil.getMultiRefContainersForStateTest();

        // first container is single-ref

        final ReferenceContext refContext = new ReferenceContext(0);
        final int alignmentStart = 1;
        final int alignmentSpan = READ_LENGTH_FOR_TEST_RECORDS;
        int recordCount = 1;
        int globalRecordCount = 0; // first container - no records yet
        CRAMStructureTestUtil.assertContainerState(containers.get(0), refContext, alignmentStart, alignmentSpan,
                recordCount, READ_LENGTH_FOR_TEST_RECORDS * recordCount, globalRecordCount);

        // when other refs are added, subsequent containers are multiref

        recordCount++;  // this container has 2 records
        globalRecordCount = containers.get(0).nofRecords;   // we've seen 1 record before this container
        CRAMStructureTestUtil.assertContainerState(containers.get(1), ReferenceContext.MULTIPLE_REFERENCE_CONTEXT,
                Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN,
                recordCount, READ_LENGTH_FOR_TEST_RECORDS * recordCount, globalRecordCount);

        recordCount++;  // this container has 3 records
        globalRecordCount = containers.get(0).nofRecords + containers.get(1).nofRecords;    // we've seen 3 records before this container
        CRAMStructureTestUtil.assertContainerState(containers.get(2), ReferenceContext.MULTIPLE_REFERENCE_CONTEXT,
                Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN,
                recordCount, READ_LENGTH_FOR_TEST_RECORDS * recordCount, globalRecordCount);
    }

    private Container buildFromNewFactory(final List<CramCompressionRecord> records) {
        final ContainerFactory factory = new ContainerFactory(CRAMStructureTestUtil.getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
        return factory.buildContainer(records);
    }
}
