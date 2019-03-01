package htsjdk.samtools.cram.structure;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.build.ContainerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CramCompressionRecordUtil {
    public static final int READ_LENGTH_FOR_TEST_RECORDS = 123;

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

    public static SAMFileHeader getSAMFileHeaderForTests() {
        return header;
    }

    private static CramCompressionRecord createMappedRecord(final int alignmentStart, final int sequenceId) {
        final CramCompressionRecord record = new CramCompressionRecord();
        record.readBases = "AAA".getBytes();
        record.qualityScores = "!!!".getBytes();
        record.readLength = READ_LENGTH_FOR_TEST_RECORDS;
        record.readName = "A READ NAME";
        record.sequenceId = sequenceId;
        record.alignmentStart = alignmentStart + 1;
        record.setLastSegment(true);
        record.setSegmentUnmapped(false);
        record.readFeatures = Collections.emptyList();
        return record;
    }

    public static List<CramCompressionRecord> getSingleRefRecords(final int recordCount, final int sequenceId) {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < recordCount; i++) {
            final CramCompressionRecord record = createMappedRecord(i, sequenceId);

            // set half unmapped-but-placed, to show that it does not make a difference
            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
            }

            records.add(record);
        }
        return records;
    }

    public static List<CramCompressionRecord> getMultiRefRecords(final int recordCount) {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < recordCount; i++) {
            final CramCompressionRecord record = createMappedRecord(i, i);

            // set half unmapped-but-placed, to show that it does not make a difference
            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
            }

            records.add(record);
        }
        return records;
    }

    public static List<CramCompressionRecord> getUnmappedRecords(final int recordCount) {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < recordCount; i++) {
            final CramCompressionRecord record = createMappedRecord(i, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
            record.setSegmentUnmapped(true);
            record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            records.add(record);
        }
        return records;
    }

    // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
    // but not both.  We treat these weird edge cases as unplaced.

    public static List<CramCompressionRecord> getHalfUnmappedNoRefRecords(final int recordCount) {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < recordCount; i++) {
            final CramCompressionRecord record = createMappedRecord(i, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
            record.setSegmentUnmapped(true);
            records.add(record);
        }
        return records;
    }

    public static List<CramCompressionRecord> getHalfUnmappedNoStartRecords(final int recordCount, final int sequenceId) {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < recordCount; i++) {
            final CramCompressionRecord record = createMappedRecord(i, sequenceId);
            record.setSegmentUnmapped(true);
            record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            record.setLastSegment(true);
            records.add(record);
        }
        return records;
    }

    public static List<CramCompressionRecord> getSingleRefRecordsWithOneUnmapped(final int testRecordCount, final int mappedSequenceId) {
        final List<CramCompressionRecord> retval = getSingleRefRecords(testRecordCount, mappedSequenceId);
        retval.get(0).setSegmentUnmapped(true);
        retval.get(0).alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        retval.get(0).sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        return retval;
    }

    public static List<CramCompressionRecord> getMultiRefRecordsWithOneUnmapped(final int testRecordCount) {
        final List<CramCompressionRecord> retval = getMultiRefRecords(testRecordCount);
        retval.get(0).setSegmentUnmapped(true);
        retval.get(0).alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        retval.get(0).sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        return retval;
    }

    public static List<Container> getMultiRefContainersForStateTest() {
        final ContainerFactory factory = new ContainerFactory(getSAMFileHeaderForTests(), 10);
        final List<Container> testContainers = new ArrayList<>(3);

        final List<CramCompressionRecord> records = new ArrayList<>();

        final CramCompressionRecord record0 = createMappedRecord(0, 0);
        records.add(record0);
        final Container container0 = factory.buildContainer(records);

        final CramCompressionRecord record1 = createMappedRecord(1, 1);
        records.add(record1);
        final Container container1 = factory.buildContainer(records);

        final CramCompressionRecord unmapped = createMappedRecord(2, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        unmapped.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        unmapped.setSegmentUnmapped(true);
        records.add(unmapped);
        final Container container2 = factory.buildContainer(records);

        testContainers.add(container0);
        testContainers.add(container1);
        testContainers.add(container2);
        return testContainers;
    }
}
