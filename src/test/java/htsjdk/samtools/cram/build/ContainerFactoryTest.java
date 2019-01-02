package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.slice.SliceHeader;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by vadim on 15/12/2015.
 */
public class ContainerFactoryTest extends HtsjdkTest {

    @Test
    public void testUnmapped() {
        SAMFileHeader header = new SAMFileHeader();

        int recordsPerContainer = 10;
        ContainerFactory factory = new ContainerFactory(header, recordsPerContainer);

        List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < recordsPerContainer; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.setSegmentUnmapped(true);
            record.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
            record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            record.readBases = record.qualityScores = "ACGTN".getBytes();
            record.readName = Integer.toString(i);

            records.add(record);
        }

        final Container container = factory.buildContainer(records, null, 0);
        Assert.assertNotNull(container);
        Assert.assertEquals(container.nofRecords, records.size());

        assertContainerAlignmentBoundaries(container, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, SliceHeader.NO_ALIGNMENT_START, SliceHeader.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testMapped() {
        InMemoryReferenceSequenceFile refFile = new InMemoryReferenceSequenceFile();
        String refName = "1";
        String refString = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        refFile.add(refName, refString.getBytes());
        ReferenceSource source = new ReferenceSource(refFile);
        SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord(refName, refString.length()));
        int sequenceId = header.getSequenceIndex(refName);

        int recordsPerContainer = 10;
        byte[] bases = "AAAAA".getBytes();
        int readLength = bases.length;
        int alignmentStartOffset = 3;
        ContainerFactory factory = new ContainerFactory(header, recordsPerContainer);

        List<CramCompressionRecord> records = new ArrayList<>();
        int span = 0;
        for (int i = 0; i < recordsPerContainer; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.setSegmentUnmapped(false);
            record.sequenceId = sequenceId;
            record.alignmentStart = alignmentStartOffset + i;
            record.readBases = record.qualityScores = bases;
            record.readName = Integer.toString(i);
            record.readLength = readLength;
            record.readFeatures = Collections.emptyList();

            records.add(record);
            span = record.alignmentStart + readLength - alignmentStartOffset;
        }

        final Container container = factory.buildContainer(records, null, 0);
        Assert.assertNotNull(container);
        Assert.assertEquals(container.nofRecords, records.size());

        assertContainerAlignmentBoundaries(container, sequenceId, alignmentStartOffset, span);
    }

    @Test
    public void testMultiref() {
        SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("1", 100));
        header.addSequence(new SAMSequenceRecord("2", 200));

        int recordsPerContainer = 10;
        byte[] bases = "AAAAA".getBytes();
        int readLength = bases.length;
        int alignmentStartOffset = 3;
        ContainerFactory factory = new ContainerFactory(header, recordsPerContainer);

        List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < recordsPerContainer; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.setSegmentUnmapped(false);
            record.sequenceId = i % 2;
            record.alignmentStart = alignmentStartOffset + i;
            record.readBases = record.qualityScores = bases;
            record.readName = Integer.toString(i);
            record.readLength = readLength;
            record.readFeatures = Collections.emptyList();

            records.add(record);
        }

        final Container container = factory.buildContainer(records, null, 0);
        Assert.assertNotNull(container);
        Assert.assertEquals(container.nofRecords, records.size());

        assertContainerAlignmentBoundaries(container, SliceHeader.REFERENCE_INDEX_MULTI, SliceHeader.NO_ALIGNMENT_START, SliceHeader.NO_ALIGNMENT_SPAN);
    }


    private void assertContainerAlignmentBoundaries(Container container, int sequenceId, int alignmentStart, int alignmentSpan) {
        Assert.assertEquals(container.sequenceId, sequenceId);
        Assert.assertEquals(container.alignmentStart, alignmentStart);
        Assert.assertEquals(container.alignmentSpan, alignmentSpan);

        if (sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX || sequenceId == SliceHeader.REFERENCE_INDEX_MULTI) {
            Assert.assertEquals(container.alignmentStart, SliceHeader.NO_ALIGNMENT_START);
            Assert.assertEquals(container.alignmentSpan, SliceHeader.NO_ALIGNMENT_SPAN);
        }
    }
}
