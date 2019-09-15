package htsjdk.samtools.cram.structure;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.utils.ValidationUtils;

import java.util.*;
import java.util.function.Consumer;

public class CRAMStructureTestHelper {
    public static final int REFERENCE_CONTIG_LENGTH = 10000;
    public static final int READ_LENGTH = 20;
    public static final int REFERENCE_SEQUENCE_ZERO = 0;
    public static final int REFERENCE_SEQUENCE_ONE = 1;

    public static final CRAMEncodingStrategy ENCODING_STRATEGY = new CRAMEncodingStrategy();
    public static final Map<String, Integer> READ_GROUP_MAP = new HashMap();

    public static final SAMFileHeader SAM_FILE_HEADER = getSAMFileHeader();
    public static final CRAMReferenceSource REFERENCE_SOURCE = new ReferenceSource(getReferenceFile());

    // create a SINGLE container (throws if more than one container is produced)
    public static Container getSingleContainerFromRecords(
            final ContainerFactory containerFactory,
            final List<SAMRecord> samRecords,
            final long byteOffset) {
        for (int i = 0; i < samRecords.size(); i++) {
            final Container container = containerFactory.getNextContainer(samRecords.get(i), byteOffset);
            if (container != null) {
                if (i == samRecords.size() - 1) {
                    return container;
                } else {
                    throw new IllegalArgumentException(String.format(
                            "Container created after only %d records (of %d presented) were consumed",
                            i,
                            samRecords.size()));
                }
            }
        }
        return containerFactory.getFinalContainer(byteOffset);
    }

    // create a SINGLE container, and throw if more than one is produced
    public static List<Container> getAllContainersFromRecords(
            final ContainerFactory containerFactory,
            final List<SAMRecord> samRecords) {
        final List<Container> containers = new ArrayList<>();
        for (int i = 0; i < samRecords.size(); i++) {
            final Container container = containerFactory.getNextContainer(samRecords.get(i), 0);
            if (container != null) {
                containers.add(container);
            }
        }
        final Container finalContainer = containerFactory.getFinalContainer(0);
        if (finalContainer != null) {
            containers.add(finalContainer);
        }
        return containers;
    }

    public static SAMRecord createMappedSAMRecord(final int referenceIndex, final int intForNameAndStart) {
        // reference index must be valid for out header
        ValidationUtils.validateArg(referenceIndex == REFERENCE_SEQUENCE_ZERO || referenceIndex == REFERENCE_SEQUENCE_ONE,
                "invalid reference index");

        final SAMRecord samRecord = new SAMRecord(SAM_FILE_HEADER);
        samRecord.setReferenceIndex(referenceIndex);
        samRecord.setAlignmentStart(intForNameAndStart);
        samRecord.setReadName(Integer.toString(intForNameAndStart));
        addBasesAndQualities(samRecord);

        ValidationUtils.validateArg(samRecord.getReadUnmappedFlag() == false, "read should be mapped");
        return samRecord;
    }

    public static List<SAMRecord> createMappedSAMRecords(final int count, final int referenceIndex) {
        // reference index must be valid for out header
        ValidationUtils.validateArg(referenceIndex == REFERENCE_SEQUENCE_ZERO || referenceIndex == REFERENCE_SEQUENCE_ONE,
                "invalid reference index");

        final List<SAMRecord> samRecords = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            samRecords.add(createMappedSAMRecord(referenceIndex, i));
        }
        return samRecords;
    }

    public static List<SAMRecord> createMappedMultiRefSAMRecords(final int count) {
        final List<SAMRecord> samRecords = new ArrayList<>(count);
        for (int i = 1; i <= count/2; i++) {
            samRecords.add(createMappedSAMRecord(0, i));
        }
        for (int i = 1; i <= count/2; i++) {
            samRecords.add(createMappedSAMRecord(1, i));
        }
        return samRecords;
    }

    public static SAMRecord createUnmappedSAMRecord(final int intForNameAndStart) {
        final SAMRecord samRecord = new SAMRecord(SAM_FILE_HEADER);
        samRecord.setReadName(Integer.toString(intForNameAndStart));
        samRecord.setReadUnmappedFlag(true);
        addBasesAndQualities(samRecord);

        ValidationUtils.validateArg(samRecord.getReadUnmappedFlag() == true, "read should be unmapped");
        return samRecord;
    }

    public static List<SAMRecord> createUnmappedSAMRecords(final int recordCount) {
        final List<SAMRecord> samRecords = new ArrayList<>(recordCount);
        for (int i = 1; i <= recordCount; i++) {
            samRecords.add(createUnmappedSAMRecord(i));
        }
        return samRecords;
    }

    // this record is not quite valid - its only half placed
    public static SAMRecord createUnmappedWithAlignmentStartSAMRecord(final int intForNameAndStart) {
        // create an unmapped record with no reference index, but with an alignment start
        final SAMRecord samRecord = createUnmappedSAMRecord(intForNameAndStart);
        samRecord.setAlignmentStart(intForNameAndStart);
        ValidationUtils.validateArg(samRecord.getReadUnmappedFlag() == true, "read should be unmapped");

        return samRecord;
    }

    // these records are not quite valid - they're only half placed
    public static List<SAMRecord> createUnmappedWithAlignmentStartSAMRecords(final int recordCount) {
        final List<SAMRecord> records = new ArrayList<>(recordCount);
        for (int i = 1; i <= recordCount; i++) {
            // create an unmapped record with no reference index, but with an alignment start
            records.add(createUnmappedWithAlignmentStartSAMRecord(i));
        }
        return records;
    }

    // this record is not quite valid - its only half placed
    public static SAMRecord createUnmappedWithReferenceIndexRecord(final int referenceIndex, final int intForNameAndStart) {
        // reference index must be valid for out header
        ValidationUtils.validateArg(referenceIndex == REFERENCE_SEQUENCE_ZERO || referenceIndex == REFERENCE_SEQUENCE_ONE,
                "invalid reference index");

        // create an unmapped record with a reference index, but no alignment start
        final SAMRecord samRecord = createUnmappedSAMRecord(intForNameAndStart);
        samRecord.setReferenceIndex(referenceIndex);
        ValidationUtils.validateArg(samRecord.getReadUnmappedFlag() == true, "read should be unmapped");
        ValidationUtils.validateArg(samRecord.getReferenceIndex() == referenceIndex, "read should have a ref index");
        return samRecord;
    }

    // these records are not quite valid - they're only half placed
    public static List<SAMRecord> createUnmappedWithReferenceIndexRecords(final int recordCount, final int referenceIndex) {
        // reference index must be valid for out header
        ValidationUtils.validateArg(referenceIndex == REFERENCE_SEQUENCE_ZERO || referenceIndex == REFERENCE_SEQUENCE_ONE,
                "invalid reference index");

        // create unmapped records with a reference index, but no alignment start
        final List<SAMRecord> samRecords = new ArrayList<>(recordCount);
        for (int i = 1; i <= recordCount; i++) {
            samRecords.add(createUnmappedWithReferenceIndexRecord(referenceIndex, i));
        }
        return samRecords;
    }

    public static List<SAMRecord> createMappedPlusOneUnmappedSAMRecords(final int recordCount, final int referenceIndex) {
        // reference index must be valid for out header
        ValidationUtils.validateArg(referenceIndex == REFERENCE_SEQUENCE_ZERO || referenceIndex == REFERENCE_SEQUENCE_ONE,
                "invalid reference index");
        final List<SAMRecord> samRecords = new ArrayList<>(recordCount);
        for (int i = 1; i <= recordCount - 1; i++) {
            samRecords.add(createUnmappedWithReferenceIndexRecord(referenceIndex, i));
        }
        samRecords.add(createUnmappedSAMRecord(recordCount ));
        return samRecords;
    }

    public static List<SAMRecord> createMappedMultiRefPlusOneUnmappedSAMRecords(final int recordCount) {
        final List<SAMRecord> samRecords = createMappedMultiRefSAMRecords(recordCount);
        samRecords.add(createUnmappedSAMRecord(recordCount ));
        return samRecords;
    }

    // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
    // but not both.  We treat these weird edge cases as unplaced.
    public static List<CRAMRecord> createMappedCRAMRecords(
            final int count,
            final int referenceIndex,
            final Consumer<SAMRecord> samRecordMutator) {
        final List<CRAMRecord> cramRecords = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final SAMRecord samRecord = createMappedSAMRecord(referenceIndex, i);
            // give the caller a chance to transform the SAMRecord before we use it to create the CRAMRecord
            samRecordMutator.accept(samRecord);
            cramRecords.add(toMappedCRAMRecord(samRecord, i, referenceIndex));
        }

        return cramRecords;
    }

    public static List<CRAMRecord> createUnmappedCRAMRecords(
            final int count,
            final Consumer<SAMRecord> samRecordMutator) {
        final List<CRAMRecord> cramRecords = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final SAMRecord samRecord = createUnmappedSAMRecord(i);
            // give the caller a chance to transform the SAMRecord before we use it to create the CRAMRecord
            samRecordMutator.accept(samRecord);
            cramRecords.add(toUnmappedCRAMRecord(samRecord, i));
        }

        return cramRecords;
    }

    private static SAMFileHeader getSAMFileHeader() {
        final List<SAMSequenceRecord> sequenceRecords = new ArrayList<>();
        sequenceRecords.add(new SAMSequenceRecord("0", REFERENCE_CONTIG_LENGTH));
        sequenceRecords.add(new SAMSequenceRecord("1", REFERENCE_CONTIG_LENGTH));
        final SAMFileHeader header = new SAMFileHeader(new SAMSequenceDictionary(sequenceRecords));
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        return header;
    }

    private static final InMemoryReferenceSequenceFile getReferenceFile() {
        final InMemoryReferenceSequenceFile referenceFile = new InMemoryReferenceSequenceFile();
        byte[] bases = new byte[REFERENCE_CONTIG_LENGTH];
        Arrays.fill(bases, (byte) 'A');
        referenceFile.add("0", bases);

        bases = new byte[REFERENCE_CONTIG_LENGTH];
        Arrays.fill(bases, (byte) 'C');
        referenceFile.add("1", bases);

        return referenceFile;
    }

    private static CRAMRecord toMappedCRAMRecord(
            final SAMRecord samRecord,
            final int referenceIndex,
            final int intForNameAndIndex) {
        return new CRAMRecord(
                CramVersions.DEFAULT_CRAM_VERSION,
                ENCODING_STRATEGY,
                samRecord,
                REFERENCE_SOURCE.getReferenceBases(SAM_FILE_HEADER.getSequence(referenceIndex), false),
                intForNameAndIndex,
                READ_GROUP_MAP);
    }
    private static CRAMRecord toUnmappedCRAMRecord(
            final SAMRecord samRecord,
            final int intForNameAndIndex) {
        return new CRAMRecord(
                CramVersions.DEFAULT_CRAM_VERSION,
                ENCODING_STRATEGY,
                samRecord,
                null, // TODO: is null bases correct here ?
                intForNameAndIndex,
                READ_GROUP_MAP);
    }


    public static SAMRecord createUnmappedPlacedSAMRecord(final int referenceIndex, final int intForNameAndIndex) {
        final SAMRecord samRecord = createMappedSAMRecord(referenceIndex, intForNameAndIndex);
        samRecord.setReadUnmappedFlag(true);
        return samRecord;
    }

    private static SAMRecord addBasesAndQualities(final SAMRecord samRecord) {
        final byte bases[] = new byte[READ_LENGTH];
        Arrays.fill(bases, (byte) 'A');
        samRecord.setReadBases(bases);

        final byte[] baseQualities = new byte[READ_LENGTH];
        Arrays.fill(baseQualities, (byte) 50);
        samRecord.setBaseQualities(baseQualities);

        return samRecord;
    }

}
