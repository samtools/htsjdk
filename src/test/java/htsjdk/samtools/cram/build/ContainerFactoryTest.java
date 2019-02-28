package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.Slice;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by vadim on 15/12/2015.
 */
public class ContainerFactoryTest extends HtsjdkTest {
    static final int TEST_RECORD_COUNT = 10;
    static final int READ_LENGTH_FOR_TEST_RECORDS = 123;

    private static final SAMFileHeader header = initializeSAMFileHeaderForTests();

    private static SAMFileHeader initializeSAMFileHeaderForTests() {
        final SAMFileHeader header = new SAMFileHeader();

        // arbitrary names and length.  Just ensure we have 10 different valid refs.

        header.addSequence(new SAMSequenceRecord("0", 10));
        header.addSequence(new SAMSequenceRecord("1", 10));
        header.addSequence(new SAMSequenceRecord("2", 10));
        header.addSequence(new SAMSequenceRecord("3", 10));
        header.addSequence(new SAMSequenceRecord("4", 10));
        header.addSequence(new SAMSequenceRecord("5", 10));
        header.addSequence(new SAMSequenceRecord("6", 10));
        header.addSequence(new SAMSequenceRecord("7", 10));
        header.addSequence(new SAMSequenceRecord("8", 10));
        header.addSequence(new SAMSequenceRecord("9", 10));

        return header;
    }

    static SAMFileHeader getSAMFileHeaderForTests() {
        return header;
    }

    static List<CramCompressionRecord> getSingleRefRecords(final int recordCount) {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < recordCount; i++) {
            final CramCompressionRecord record = createMappedRecord(i);

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
            }

            records.add(record);
        }
        return records;
    }

    static List<CramCompressionRecord> getUnmappedRecords() {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < TEST_RECORD_COUNT; i++) {
            final CramCompressionRecord record = createMappedRecord(i);
            record.setSegmentUnmapped(true);
            record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            record.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
            records.add(record);
        }
        return records;
    }

    // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
    // but not both.  We treat these weird edge cases as unplaced.

    static List<CramCompressionRecord> getUnmappedNoRefRecords() {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < TEST_RECORD_COUNT; i++) {
            final CramCompressionRecord record = createMappedRecord(i);
            record.setSegmentUnmapped(true);
            record.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
            records.add(record);
        }
        return records;
    }

    static List<CramCompressionRecord> getUnmappedNoStartRecords() {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < TEST_RECORD_COUNT; i++) {
            final CramCompressionRecord record = createMappedRecord(i);
            record.setSegmentUnmapped(true);
            record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            record.setLastSegment(true);
            records.add(record);
        }
        return records;
    }

    static List<CramCompressionRecord> getMultiRefRecords() {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < TEST_RECORD_COUNT; i++) {
            final CramCompressionRecord record = createMappedRecord(i);
            record.sequenceId = i;

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
            }

            records.add(record);
        }
        return records;
    }

    static List<Container> getMultiRefContainersForStateTest() {
        final ContainerFactory factory = new ContainerFactory(getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
        final List<Container> testContainers = new ArrayList<>(3);

        final List<CramCompressionRecord> records = new ArrayList<>();

        final CramCompressionRecord record0 = createMappedRecord(0);
        record0.sequenceId = 0;
        records.add(record0);
        final Container container0 = factory.buildContainer(records);

        final CramCompressionRecord record1 = createMappedRecord(1);
        record1.sequenceId = 1;
        records.add(record1);
        final Container container1 = factory.buildContainer(records);

        final CramCompressionRecord unmapped = createMappedRecord(2);
        unmapped.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        unmapped.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        unmapped.setSegmentUnmapped(true);
        records.add(unmapped);
        final Container container2 = factory.buildContainer(records);

        testContainers.add(container0);
        testContainers.add(container1);
        testContainers.add(container2);
        return testContainers;
    }

    private static CramCompressionRecord createMappedRecord(int i) {
        final CramCompressionRecord record = new CramCompressionRecord();
        record.readBases = "AAA".getBytes();
        record.qualityScores = "!!!".getBytes();
        record.readLength = READ_LENGTH_FOR_TEST_RECORDS;
        record.readName = "" + i;
        record.sequenceId = 0;
        record.alignmentStart = i + 1;
        record.setLastSegment(true);
        record.setSegmentUnmapped(false);
        record.readFeatures = Collections.emptyList();
        return record;
    }

    @Test
    public void testMapped() {
        final ReferenceContext refContext = new ReferenceContext(0);
        final int alignmentStart = 1;
        final int alignmentSpan = READ_LENGTH_FOR_TEST_RECORDS + TEST_RECORD_COUNT - 1;

        final ContainerFactory factory = new ContainerFactory(getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
        final Container container = factory.buildContainer(getSingleRefRecords(TEST_RECORD_COUNT));
        assertContainerState(container, refContext, alignmentStart, alignmentSpan);
    }

    @Test
    public void testUnmapped() {
        final ContainerFactory factory = new ContainerFactory(getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
        final Container container = factory.buildContainer(getUnmappedRecords());
        assertContainerState(container, ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
    // but not both.  We treat these weird edge cases as unplaced.

    @Test
    public void testUnmappedNoReferenceId() {
        final ContainerFactory factory = new ContainerFactory(getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
        final Container container = factory.buildContainer(getUnmappedNoRefRecords());
        assertContainerState(container, ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testUnmappedNoAlignmentStart() {
        final ContainerFactory factory = new ContainerFactory(getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
        final Container container = factory.buildContainer(getUnmappedNoStartRecords());
        assertContainerState(container, ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testMultiref() {
        final ContainerFactory factory = new ContainerFactory(getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
        final Container container = factory.buildContainer(getMultiRefRecords());
        assertContainerState(container, ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testMultirefWithStateTransitions() {
        final List<Container> containers = getMultiRefContainersForStateTest();

        // first container is single-ref

        final ReferenceContext refContext = new ReferenceContext(0);
        final int alignmentStart = 1;
        final int alignmentSpan = READ_LENGTH_FOR_TEST_RECORDS;
        int recordCount = 1;
        int globalRecordCount = 0; // first container - no records yet
        assertContainerState(containers.get(0), refContext, alignmentStart, alignmentSpan,
                globalRecordCount, recordCount, READ_LENGTH_FOR_TEST_RECORDS * recordCount);

        // when other refs are added, subsequent containers are multiref

        recordCount++;  // this container has 2 records
        globalRecordCount = containers.get(0).nofRecords;   // we've seen 1 record before this container
        assertContainerState(containers.get(1), ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN,
                globalRecordCount, recordCount, READ_LENGTH_FOR_TEST_RECORDS * recordCount);

        recordCount++;  // this container has 3 records
        globalRecordCount = containers.get(0).nofRecords + containers.get(1).nofRecords;    // we've seen 3 records before this container
        assertContainerState(containers.get(2), ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN,
                globalRecordCount, recordCount, READ_LENGTH_FOR_TEST_RECORDS * recordCount);
    }

    // show that unmapped-unplaced reads cause a single ref slice/container to become multiref

    @Test
    public void singleRefWithUnmappedNoRef() {
        final ContainerFactory factory = new ContainerFactory(getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < TEST_RECORD_COUNT; i++) {
            final CramCompressionRecord record = createMappedRecord(i);

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
                record.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
            }

            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        assertContainerState(container, ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void singleRefWithUnmappedNoStart() {
        final ContainerFactory factory = new ContainerFactory(getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < TEST_RECORD_COUNT; i++) {
            final CramCompressionRecord record = createMappedRecord(i);

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
                record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            }

            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        assertContainerState(container, ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    // show that unmapped-unplaced reads don't change the state of a multi-ref slice/container

    @Test
    public void multiRefWithUnmappedNoRef() {
        final ContainerFactory factory = new ContainerFactory(getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < TEST_RECORD_COUNT; i++) {
            final CramCompressionRecord record = createMappedRecord(i);

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
                record.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
            }
            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        assertContainerState(container, ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void multiRefWithUnmappedNoStart() {
        final ContainerFactory factory = new ContainerFactory(getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < TEST_RECORD_COUNT; i++) {
            final CramCompressionRecord record = createMappedRecord(i);

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
                record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            }
            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        assertContainerState(container, ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    private void assertContainerState(final Container container,
                                      final ReferenceContext referenceContext,
                                      final int alignmentStart,
                                      final int alignmentSpan) {
        final int globalRecordCounter = 0; // first Container
        final int baseCount = TEST_RECORD_COUNT * READ_LENGTH_FOR_TEST_RECORDS;

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
