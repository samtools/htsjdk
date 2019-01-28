package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
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

    public static Container getSingleRefContainer(final SAMFileHeader samFileHeader) {
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = createMappedRecord(i);

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
            }

            records.add(record);
        }

        return factory.buildContainer(records);
    }

    public static Container getUnmappedNoRefContainer(final SAMFileHeader samFileHeader) {
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = createMappedRecord(i);
            record.setSegmentUnmapped(true);
            record.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
            records.add(record);
        }

        return factory.buildContainer(records);
    }

    public static Container getUnmappedNoStartContainer(final SAMFileHeader samFileHeader) {
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = createMappedRecord(i);
            record.setSegmentUnmapped(true);
            record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            record.setLastSegment(true);
            records.add(record);
        }

        return factory.buildContainer(records);
    }

    public static Container getMultiRefContainer(final SAMFileHeader samFileHeader) {
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = createMappedRecord(i);
            record.sequenceId = i;

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
            }

            records.add(record);
        }

        return factory.buildContainer(records);
    }

    public static List<Container> getMultiRefContainersForStateTest(final SAMFileHeader samFileHeader) {
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
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

    public static CramCompressionRecord createMappedRecord(int i) {
        final CramCompressionRecord record = new CramCompressionRecord();
        record.readBases = "AAA".getBytes();
        record.qualityScores = "!!!".getBytes();
        record.readLength = 3;
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
        final SAMFileHeader header = new SAMFileHeader();

        final int recordCount = 10;
        final int sequenceId = 0;
        final int alignmentStart = 1;
        final int alignmentSpan = 12;

        final Container container = getSingleRefContainer(header);
        assertContainerState(container, recordCount, sequenceId, alignmentStart, alignmentSpan);
    }

    @Test
    public void testUnmappedNoReferenceId() {
        final SAMFileHeader header = new SAMFileHeader();

        final Container container = getUnmappedNoRefContainer(header);
        assertContainerState(container, 10, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testUnmappedNoAlignmentStart() {
        final SAMFileHeader header = new SAMFileHeader();

        final Container container = getUnmappedNoStartContainer(header);
        assertContainerState(container, 10, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testMultiref() {
        final SAMFileHeader header = new SAMFileHeader();

        final Container container = getMultiRefContainer(header);
        assertContainerState(container, 10, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testMultirefWithStateTransitions() {
        final SAMFileHeader header = new SAMFileHeader();

        final List<Container> containers = getMultiRefContainersForStateTest(header);

        // first container is single-ref
        assertContainerState(containers.get(0), 1, 0, 1, 3);

        // when other refs are added, subsequent containers are multiref

        assertContainerState(containers.get(1), 2, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
        assertContainerState(containers.get(2), 3, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    // show that unmapped-unplaced reads cause a single ref slice/container to become multiref

    @Test
    public void singleRefWithUnmappedNoRef() {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = createMappedRecord(i);

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
                record.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
            }

            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        assertContainerState(container, 10, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void singleRefWithUnmappedNoStart() {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = createMappedRecord(i);

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
                record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            }

            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        assertContainerState(container, 10, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    // show that unmapped-unplaced reads don't change the state of a multi-ref slice/container

    @Test
    public void multiRefWithUnmappedNoRef() {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = createMappedRecord(i);

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
                record.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
            }
            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        assertContainerState(container, 10, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void multiRefWithUnmappedNoStart() {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = createMappedRecord(i);

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
                record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            }
            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        assertContainerState(container, 10, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    public static void assertContainerState(final Container container, 
                                            final int recordCount, 
                                            final int sequenceId, 
                                            final int alignmentStart, 
                                            final int alignmentSpan) {
        Assert.assertNotNull(container);
        Assert.assertEquals(container.nofRecords, recordCount);
        Assert.assertEquals(container.sequenceId, sequenceId);
        Assert.assertEquals(container.alignmentStart, alignmentStart);
        Assert.assertEquals(container.alignmentSpan, alignmentSpan);
        Assert.assertEquals(container.slices.length, 1);
        Assert.assertEquals(container.slices[0].sequenceId, container.sequenceId);
    }
}
