package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.CRAMException;
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
            final CramCompressionRecord record = new CramCompressionRecord();
            record.readBases = "AAA".getBytes();
            record.qualityScores = "!!!".getBytes();
            record.readLength = 3;
            record.readName = "" + i;
            record.sequenceId = 0;
            record.alignmentStart = i + 1;
            record.setLastSegment(true);
            record.readFeatures = Collections.emptyList();

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
            } else {
                record.setSegmentUnmapped(false);
            }

            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        Assert.assertEquals(container.nofRecords, 10);
        Assert.assertEquals(container.sequenceId, 0);
        Assert.assertEquals(container.slices.length, 1);
        Assert.assertEquals(container.slices[0].sequenceId, container.sequenceId);
        return container;
    }

    public static Container getUnmappedNoRefContainer(final SAMFileHeader samFileHeader) {
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.readBases = "AAA".getBytes();
            record.qualityScores = "!!!".getBytes();
            record.setSegmentUnmapped(true);
            record.readName = "" + i;
            record.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
            record.alignmentStart = i;
            record.setLastSegment(true);

            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        Assert.assertEquals(container.nofRecords, 10);
        Assert.assertEquals(container.sequenceId, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        Assert.assertEquals(container.slices.length, 1);
        Assert.assertEquals(container.slices[0].sequenceId, container.sequenceId);
        return container;
    }

    public static Container getUnmappedNoStartContainer(final SAMFileHeader samFileHeader) {
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.readBases = "AAA".getBytes();
            record.qualityScores = "!!!".getBytes();
            record.setSegmentUnmapped(true);
            record.readName = "" + i;
            record.sequenceId = 0;
            record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;

            record.setLastSegment(true);

            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        Assert.assertEquals(container.nofRecords, 10);
        Assert.assertEquals(container.sequenceId, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        Assert.assertEquals(container.slices.length, 1);
        Assert.assertEquals(container.slices[0].sequenceId, container.sequenceId);
        return container;
    }

    public static Container getMultiRefContainer(final SAMFileHeader samFileHeader) {
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.readBases = "AAA".getBytes();
            record.qualityScores = "!!!".getBytes();
            record.readName = "" + i;
            record.alignmentStart = i + 1;
            record.readLength = 3;
            record.sequenceId = i;
            record.readFeatures = Collections.emptyList();

            record.setMultiFragment(false);
            if (i % 2 == 0) {
                record.setSegmentUnmapped(false);
            } else {
                record.setSegmentUnmapped(true);
            }
            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        Assert.assertEquals(container.nofRecords, 10);
        Assert.assertEquals(container.sequenceId, Slice.MULTI_REFERENCE);
        Assert.assertEquals(container.slices.length, 1);
        Assert.assertEquals(container.slices[0].sequenceId, container.sequenceId);
        return container;
    }

    public static List<Container> getMultiRefContainersForStateTest(final SAMFileHeader samFileHeader) {
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<Container> testContainers = new ArrayList<>(3);

        final List<CramCompressionRecord> records = new ArrayList<>();

        final CramCompressionRecord record0 = new CramCompressionRecord();
        record0.readBases = "AAA".getBytes();
        record0.qualityScores = "!!!".getBytes();
        record0.readName = "0";
        record0.alignmentStart = 1;
        record0.readLength = 3;
        record0.sequenceId = 0;
        record0.readFeatures = Collections.emptyList();
        record0.setMultiFragment(false);
        record0.setSegmentUnmapped(false);

        records.add(record0);

        final Container container0 = factory.buildContainer(records);
        Assert.assertEquals(container0.nofRecords, 1);
        Assert.assertEquals(container0.sequenceId, 0);
        Assert.assertEquals(container0.slices.length, 1);
        Assert.assertEquals(container0.slices[0].sequenceId, container0.sequenceId);
        testContainers.add(container0);

        final CramCompressionRecord record1 = new CramCompressionRecord();
        record1.readBases = "AAA".getBytes();
        record1.qualityScores = "!!!".getBytes();
        record1.readName = "1";
        record1.alignmentStart = 2;
        record1.readLength = 3;
        record1.sequenceId = 1;
        record1.readFeatures = Collections.emptyList();
        record1.setMultiFragment(false);
        record1.setSegmentUnmapped(false);

        records.add(record1);

        final Container container1 = factory.buildContainer(records);
        Assert.assertEquals(container1.nofRecords, 2);
        Assert.assertEquals(container1.sequenceId, Slice.MULTI_REFERENCE);
        Assert.assertEquals(container1.slices.length, 1);
        Assert.assertEquals(container1.slices[0].sequenceId, container1.sequenceId);
        testContainers.add(container1);

        final CramCompressionRecord unmapped = new CramCompressionRecord();
        unmapped.readBases = "AAA".getBytes();
        unmapped.qualityScores = "!!!".getBytes();
        unmapped.readName = "0";
        unmapped.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        unmapped.readLength = 3;
        unmapped.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        unmapped.readFeatures = Collections.emptyList();
        unmapped.setMultiFragment(false);
        unmapped.setSegmentUnmapped(true);

        records.add(unmapped);

        final Container container2 = factory.buildContainer(records);
        Assert.assertEquals(container2.nofRecords, 3);
        Assert.assertEquals(container2.sequenceId, Slice.MULTI_REFERENCE);
        Assert.assertEquals(container2.slices.length, 1);
        Assert.assertEquals(container2.slices[0].sequenceId, container2.sequenceId);
        testContainers.add(container2);
        return testContainers;
    }

    @Test
    public void singleRefSliceBoundariesTest() {
        final Slice slice = new Slice();
        slice.sequenceId = 5;
        slice.alignmentStart = 10;
        slice.alignmentSpan = 15;

        final Container container = new Container();
        container.slices = new Slice[]{slice};

        ContainerFactory.calculateAlignmentBoundaries(container);
        Assert.assertEquals(container.sequenceId, slice.sequenceId);
        Assert.assertEquals(container.alignmentStart, slice.alignmentStart);
        Assert.assertEquals(container.alignmentSpan, slice.alignmentSpan);
    }

    @Test
    public void singleRefSliceMultipleBoundariesTest() {
        final Slice slice1 = new Slice();
        slice1.sequenceId = 5;
        slice1.alignmentStart = 10;
        slice1.alignmentSpan = 15;

        final Slice slice2 = new Slice();
        slice2.sequenceId = slice1.sequenceId;
        slice2.alignmentStart = 20;
        slice2.alignmentSpan = 20;

        final Container container = new Container();
        container.slices = new Slice[]{slice1, slice2};

        ContainerFactory.calculateAlignmentBoundaries(container);
        Assert.assertEquals(container.sequenceId, slice1.sequenceId);
        Assert.assertEquals(container.alignmentStart, 10);
        Assert.assertEquals(container.alignmentSpan, 30);      // 20 + 20 - 10
    }

    @Test
    public void multiRefSliceBoundariesTest() {
        final Slice slice1 = new Slice();
        slice1.sequenceId = Slice.MULTI_REFERENCE;
        slice1.alignmentStart = Slice.NO_ALIGNMENT_START;
        slice1.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Slice slice2 = new Slice();
        slice2.sequenceId = Slice.MULTI_REFERENCE;
        slice2.alignmentStart = Slice.NO_ALIGNMENT_START;
        slice2.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Container container = new Container();
        container.slices = new Slice[]{slice1, slice2};

        ContainerFactory.calculateAlignmentBoundaries(container);
        Assert.assertEquals(container.sequenceId, slice1.sequenceId);
        Assert.assertEquals(container.alignmentStart, slice1.alignmentStart);
        Assert.assertEquals(container.alignmentSpan, slice1.alignmentSpan);
    }

    @Test
    public void unmappedSliceBoundariesTest() {
        final Slice slice1 = new Slice();
        slice1.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        slice1.alignmentStart = Slice.NO_ALIGNMENT_START;
        slice1.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Slice slice2 = new Slice();
        slice2.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        slice2.alignmentStart = Slice.NO_ALIGNMENT_START;
        slice2.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Container container = new Container();
        container.slices = new Slice[]{slice1, slice2};

        ContainerFactory.calculateAlignmentBoundaries(container);
        Assert.assertEquals(container.sequenceId, slice1.sequenceId);
        Assert.assertEquals(container.alignmentStart, slice1.alignmentStart);
        Assert.assertEquals(container.alignmentSpan, slice1.alignmentSpan);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void differentReferencesBoundariesTest() {
        final Slice one = new Slice();
        one.sequenceId = 5;
        one.alignmentStart = 10;
        one.alignmentSpan = 15;

        final Slice another = new Slice();
        another.sequenceId = 2;
        another.alignmentStart = 1;
        another.alignmentSpan = 10;

        final Container container = new Container();
        container.slices = new Slice[]{one, another};

        ContainerFactory.calculateAlignmentBoundaries(container);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void singleAndUnmappedBoundariesTest() {
        final Slice single = new Slice();
        single.sequenceId = 5;
        single.alignmentStart = 10;
        single.alignmentSpan = 15;

        final Slice unmapped = new Slice();
        unmapped.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        unmapped.alignmentStart = Slice.NO_ALIGNMENT_START;
        unmapped.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Container container = new Container();
        container.slices = new Slice[]{single, unmapped};

        ContainerFactory.calculateAlignmentBoundaries(container);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void multiAndSingleBoundariesTest() {
        final Slice multi = new Slice();
        multi.sequenceId = Slice.MULTI_REFERENCE;
        multi.alignmentStart = Slice.NO_ALIGNMENT_START;
        multi.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Slice single = new Slice();
        single.sequenceId = 5;
        single.alignmentStart = 10;
        single.alignmentSpan = 15;

        final Container container = new Container();
        container.slices = new Slice[]{multi, single};

        ContainerFactory.calculateAlignmentBoundaries(container);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void multiAndUnmappedBoundariesTest() {
        final Slice multi = new Slice();
        multi.sequenceId = Slice.MULTI_REFERENCE;
        multi.alignmentStart = Slice.NO_ALIGNMENT_START;
        multi.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Slice unmapped = new Slice();
        unmapped.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        unmapped.alignmentStart = Slice.NO_ALIGNMENT_START;
        unmapped.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Container container = new Container();
        container.slices = new Slice[]{multi, unmapped};

        ContainerFactory.calculateAlignmentBoundaries(container);
    }

    @Test
    public void testMapped() {
        final SAMFileHeader header = new SAMFileHeader();
        final int sequenceId = 0;
        final int alignmentStart = 1;
        final int alignmentSpan = 12;

        final Container container = getSingleRefContainer(header);
        Assert.assertNotNull(container);
        assertContainerAlignmentBoundaries(container, sequenceId, alignmentStart, alignmentSpan);
    }

    @Test
    public void testUnmappedNoReferenceId() {
        final SAMFileHeader header = new SAMFileHeader();

        final Container container = getUnmappedNoRefContainer(header);
        Assert.assertNotNull(container);
        assertContainerAlignmentBoundaries(container, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testUnmappedNoAlignmentStart() {
        final SAMFileHeader header = new SAMFileHeader();

        final Container container = getUnmappedNoStartContainer(header);
        Assert.assertNotNull(container);
        assertContainerAlignmentBoundaries(container, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testMultiref() {
        final SAMFileHeader header = new SAMFileHeader();

        final Container container = getMultiRefContainer(header);
        Assert.assertNotNull(container);
        assertContainerAlignmentBoundaries(container, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testMultirefWithStateTransitions() {
        final SAMFileHeader header = new SAMFileHeader();

        final List<Container> containers = getMultiRefContainersForStateTest(header);

        // first container is single-ref

        Assert.assertNotNull(containers.get(0));
        assertContainerAlignmentBoundaries(containers.get(0), 0, 1, 3);

        // when other refs are added, subsequent containers are multiref

        Assert.assertNotNull(containers.get(1));
        assertContainerAlignmentBoundaries(containers.get(1), Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);

        Assert.assertNotNull(containers.get(2));
        assertContainerAlignmentBoundaries(containers.get(2), Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    // show that unmapped-unplaced reads cause a single ref slice/container to become multiref

    @Test
    public void singleRefWithUnmappedNoRef() {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.readBases = "AAA".getBytes();
            record.qualityScores = "!!!".getBytes();
            record.readName = "" + i;
            record.sequenceId = 0;
            record.setLastSegment(true);
            record.readFeatures = Collections.emptyList();
            record.alignmentStart = i + 1;

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
                record.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
            } else {
                record.setSegmentUnmapped(false);
            }

            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        Assert.assertNotNull(container);
        assertContainerAlignmentBoundaries(container, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void singleRefWithUnmappedNoStart() {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.readBases = "AAA".getBytes();
            record.qualityScores = "!!!".getBytes();
            record.readName = "" + i;
            record.sequenceId = 0;
            record.setLastSegment(true);
            record.readFeatures = Collections.emptyList();
            record.alignmentStart = i + 1;

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
                record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            } else {
                record.setSegmentUnmapped(false);
            }

            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        Assert.assertNotNull(container);
        assertContainerAlignmentBoundaries(container, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    // show that unmapped-unplaced reads don't change the state of a multi-ref slice/container

    @Test
    public void multiRefWithUnmappedNoRef() {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.readBases = "AAA".getBytes();
            record.qualityScores = "!!!".getBytes();
            record.readName = "" + i;
            record.alignmentStart = i + 1;
            record.readLength = 3;
            record.sequenceId = i;
            record.readFeatures = Collections.emptyList();

            record.setMultiFragment(false);
            if (i % 2 == 0) {
                record.setSegmentUnmapped(false);
            } else {
                record.setSegmentUnmapped(true);
                record.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
            }
            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        Assert.assertNotNull(container);
        assertContainerAlignmentBoundaries(container, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void multiRefWithUnmappedNoStart() {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.readBases = "AAA".getBytes();
            record.qualityScores = "!!!".getBytes();
            record.readName = "" + i;
            record.alignmentStart = i + 1;
            record.readLength = 3;
            record.sequenceId = i;
            record.readFeatures = Collections.emptyList();

            record.setMultiFragment(false);
            if (i % 2 == 0) {
                record.setSegmentUnmapped(false);
            } else {
                record.setSegmentUnmapped(true);
                record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            }
            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        Assert.assertNotNull(container);
        assertContainerAlignmentBoundaries(container, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    private void assertContainerAlignmentBoundaries(Container container, int sequenceId, int alignmentStart, int alignmentSpan) {
        Assert.assertEquals(container.sequenceId, sequenceId);
        Assert.assertEquals(container.alignmentStart, alignmentStart);
        Assert.assertEquals(container.alignmentSpan, alignmentSpan);

        if (sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX || sequenceId == Slice.MULTI_REFERENCE) {
            Assert.assertEquals(container.alignmentStart, Slice.NO_ALIGNMENT_START);
            Assert.assertEquals(container.alignmentSpan, Slice.NO_ALIGNMENT_SPAN);
        }
    }
}
