package htsjdk.samtools.cram.structure;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.utils.ValidationUtils;
import org.testng.Assert;

import java.util.*;
import java.util.stream.Stream;

public class CRAMStructureTestHelper {
    public static final int REFERENCE_CONTIG_LENGTH = 10000;
    public static final int READ_LENGTH = 20;
    public static final int REFERENCE_SEQUENCE_ZERO = 0;
    public static final int REFERENCE_SEQUENCE_ONE = 1;

    public static final CRAMEncodingStrategy ENCODING_STRATEGY = new CRAMEncodingStrategy();
    public static final Map<String, Integer> READ_GROUP_MAP = new HashMap();

    public static final SAMFileHeader SAM_FILE_HEADER = getSAMFileHeader();
    public static final CRAMReferenceSource REFERENCE_SOURCE = new ReferenceSource(getReferenceFile());

    // create a SINGLE container (throws if less than or more than one container is produced)
    public static Container createContainer(
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
        final Container singleContainer = containerFactory.getFinalContainer(byteOffset);
        Assert.assertNotNull(singleContainer);
        return singleContainer;
    }

    // create one or more containers based on records
    public static List<Container> createContainers(
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

    public Slice createSingleReferenceSlice(final int recordCount, final int referenceIndex) {
        ValidationUtils.validateArg(referenceIndex == REFERENCE_SEQUENCE_ZERO || referenceIndex == REFERENCE_SEQUENCE_ONE,
                "invalid reference index");
        final ContainerFactory containerFactory = new ContainerFactory(SAM_FILE_HEADER, new CRAMEncodingStrategy(), REFERENCE_SOURCE);
        final Container container = createContainer(containerFactory, createSAMRecordsMapped(recordCount, referenceIndex), 10);
        Assert.assertEquals(container.getSlices().size(), 1);
        final Slice slice = container.getSlices().get(0);
        Assert.assertEquals(
                slice.getAlignmentContext().getReferenceContext(),
                new ReferenceContext(referenceIndex),
                "bad slice reference context");
        return slice;
    }

    public Slice createMultiReferenceSlice() {
        final CRAMEncodingStrategy cramEncodingStrategy = new CRAMEncodingStrategy();
        final ContainerFactory containerFactory = new ContainerFactory(SAM_FILE_HEADER, cramEncodingStrategy, REFERENCE_SOURCE);
        final List<SAMRecord> samRecords = new ArrayList<>();
        // create a small number of mapped, and one unmapped
        Stream.of(
                createSAMRecordsMapped(1, REFERENCE_SEQUENCE_ZERO),
                createSAMRecordsUnmapped(1));
        final Container container = createContainer(containerFactory, samRecords, 10);
        Assert.assertEquals(container.getSlices().size(), 1);
        final Slice slice = container.getSlices().get(0);
        Assert.assertEquals(
                slice.getAlignmentContext().getReferenceContext(),
                ReferenceContext.MULTIPLE_REFERENCE_CONTEXT,
                "slice should have a multi reference context");
        return slice;

    }

    public static Slice createSliceWithSingleMappedRecord() {
        //to get a fully rendered slice containing valid CRAMRecords, it must be created in the
        // context of a container with a valid compression header, so...
        final ContainerFactory containerFactory = new ContainerFactory(SAM_FILE_HEADER, new CRAMEncodingStrategy(), REFERENCE_SOURCE);
        Container container = containerFactory.getNextContainer(
                createSAMRecordMapped(REFERENCE_SEQUENCE_ZERO,1), 0);
        Assert.assertNull(container, "shouldn't get a container from one record");
        container = containerFactory.getFinalContainer(0);
        Assert.assertEquals(container.getSlices().size(), 1);
        return container.getSlices().get(0);
    }

    public static SAMRecord createSAMRecordMapped(final int referenceIndex, final int intForNameAndStart) {
        // reference index must be valid for out header
        ValidationUtils.validateArg(referenceIndex == REFERENCE_SEQUENCE_ZERO || referenceIndex == REFERENCE_SEQUENCE_ONE,
                "invalid reference index");
        ValidationUtils.validateArg(intForNameAndStart > 0,
                "invalid alignment start for a mapped record");

        final SAMRecord samRecord = new SAMRecord(SAM_FILE_HEADER);
        samRecord.setReferenceIndex(referenceIndex);
        samRecord.setAlignmentStart(intForNameAndStart);
        samRecord.setReadName(Integer.toString(intForNameAndStart));
        addBasesAndQualities(samRecord);

        samRecord.setCigarString(String.format("%dM", READ_LENGTH));

        Assert.assertFalse(samRecord.getReadUnmappedFlag(), "read should be mapped");
        Assert.assertTrue(samRecord.getReferenceIndex() >= 0, "read should have valid ref index");
        Assert.assertTrue(samRecord.getAlignmentStart() > 0, "read should have a valid alignment start");

        return samRecord;
    }

    public static List<SAMRecord> createSAMRecordsMapped(final int count, final int referenceIndex) {
        // reference index must be valid for out header
        ValidationUtils.validateArg(referenceIndex == REFERENCE_SEQUENCE_ZERO || referenceIndex == REFERENCE_SEQUENCE_ONE,
                "invalid reference index");

        final List<SAMRecord> samRecords = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            samRecords.add(createSAMRecordMapped(referenceIndex, i));
        }
        return samRecords;
    }

    public static SAMRecord createSAMRecordUnmapped(final int intForNameAndStart) {
        final SAMRecord samRecord = new SAMRecord(SAM_FILE_HEADER);
        samRecord.setReadName(Integer.toString(intForNameAndStart));
        samRecord.setReadUnmappedFlag(true);
        addBasesAndQualities(samRecord);

        samRecord.setCigarString("*");

        Assert.assertTrue(samRecord.getReadUnmappedFlag(), "read should be unmapped");
        Assert.assertFalse(samRecord.getReferenceIndex() >= 0, "read should have valid ref index");
        Assert.assertFalse(samRecord.getAlignmentStart() >= 1, "read should not have an alignment start");

        return samRecord;
    }

    public static List<SAMRecord> createSAMRecordsUnmapped(final int recordCount) {
        final List<SAMRecord> samRecords = new ArrayList<>(recordCount);
        for (int i = 1; i <= recordCount; i++) {
            samRecords.add(createSAMRecordUnmapped(i));
        }
        return samRecords;
    }

    // this record is not quite valid - its only half placed
    public static SAMRecord createSAMRecordUnmappedWithAlignmentStart(final int intForNameAndStart) {
        // create an unmapped record with no reference index, but with an alignment start
        final SAMRecord samRecord = createSAMRecordUnmapped(intForNameAndStart);
        samRecord.setAlignmentStart(intForNameAndStart);

        Assert.assertTrue(samRecord.getReadUnmappedFlag(), "read should be unmapped");
        Assert.assertTrue(samRecord.getReferenceIndex()== SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, "read should have no ref index");
        Assert.assertTrue(samRecord.getAlignmentStart() >= 0, "read should have a valid alignment start");

        return samRecord;
    }

    // these records are not quite valid - they're only half placed
    public static List<SAMRecord> createSAMRecordsUnmappedWithAlignmentStart(final int recordCount) {
        final List<SAMRecord> records = new ArrayList<>(recordCount);
        for (int i = 1; i <= recordCount; i++) {
            // create an unmapped record with no reference index, but with an alignment start
            records.add(createSAMRecordUnmappedWithAlignmentStart(i));
        }
        return records;
    }

    // this record is not quite valid - its only half placed
    public static SAMRecord createSAMRecordUnmappedWithReferenceIndex(final int referenceIndex, final int intForNameAndStart) {
        // reference index must be valid for out header
        ValidationUtils.validateArg(referenceIndex == REFERENCE_SEQUENCE_ZERO || referenceIndex == REFERENCE_SEQUENCE_ONE,
                "invalid reference index");

        // create an unmapped record with a reference index, but no alignment start
        final SAMRecord samRecord = createSAMRecordUnmapped(intForNameAndStart);
        samRecord.setReferenceIndex(referenceIndex);

        Assert.assertTrue(samRecord.getReadUnmappedFlag(), "read should be unmapped");
        Assert.assertTrue(samRecord.getReferenceIndex() != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, "read should have no ref index");
        Assert.assertTrue(samRecord.getAlignmentStart()== 0, "read should have no alignment start");

        return samRecord;
    }

    // these records are not quite valid - they're only half placed
    public static List<SAMRecord> createSAMRecordsUnmappedWithReferenceIndex(final int recordCount, final int referenceIndex) {
        // reference index must be valid for out header
        ValidationUtils.validateArg(referenceIndex == REFERENCE_SEQUENCE_ZERO || referenceIndex == REFERENCE_SEQUENCE_ONE,
                "invalid reference index");

        // create unmapped records with a reference index, but no alignment start
        final List<SAMRecord> samRecords = new ArrayList<>(recordCount);
        for (int i = 1; i <= recordCount; i++) {
            samRecords.add(createSAMRecordUnmappedWithReferenceIndex(referenceIndex, i));
        }
        return samRecords;
    }

    public static List<CRAMRecord> createCRAMRecordsMapped(
            final int count,
            final int referenceIndex) {
        ValidationUtils.validateArg(referenceIndex == REFERENCE_SEQUENCE_ZERO || referenceIndex == REFERENCE_SEQUENCE_ONE,
                "invalid reference index");

        final List<CRAMRecord> cramRecords = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            final SAMRecord samRecord = createSAMRecordMapped(referenceIndex, i);
            final CRAMRecord cramRecord = toMappedCRAMRecord(samRecord, referenceIndex, i);

            Assert.assertEquals(
                    cramRecord.getBAMFlags() & SAMFlag.READ_UNMAPPED.intValue(),
                    0, "read should be mapped");
            Assert.assertTrue(cramRecord.getReferenceIndex() >= 0, "read should have valid ref index");
            Assert.assertTrue(cramRecord.getAlignmentStart() >= 0, "read should have a valid alignment start");

            cramRecords.add(cramRecord);
        }

        return cramRecords;
    }

    public static List<CRAMRecord> createCRAMRecordsUnmapped(final int count) {
        final List<CRAMRecord> cramRecords = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final SAMRecord samRecord = createSAMRecordUnmapped(i);
            cramRecords.add(toUnmappedCRAMRecord(samRecord, i));
        }

        return cramRecords;
    }

    public static CRAMRecord createCRAMRecordUnmappedPlaced(
            final int referenceIndex,
            final int alignmentStart) {
       final CRAMRecord cramRecord = createCRAMRecord(1, referenceIndex, alignmentStart, SAMFlag.READ_UNMAPPED.intValue());
        Assert.assertEquals(
                cramRecord.getBAMFlags() & SAMFlag.READ_UNMAPPED.intValue(),
                SAMFlag.READ_UNMAPPED.intValue(), "read should be unmapped");
       return cramRecord;
    }

    public static List<CRAMRecord> createCRAMRecordsMappedThenUnmapped(final int recordCount) {
        Assert.assertTrue(recordCount > 2);
        final List<CRAMRecord> records = new ArrayList<>();
        records.addAll(createCRAMRecordsMapped(recordCount/2, REFERENCE_SEQUENCE_ZERO));
        records.addAll(createCRAMRecordsUnmapped(recordCount/2));
        return records;
    }

    // this is unmapped/"half placed"
    public static List<CRAMRecord> createCRAMRecordsUnmappedWithAlignmentStart(
            final int count,
            final int alignmentStart) {
        final List<CRAMRecord> cramRecords = new ArrayList(count);
        for (int i = 0; i < count; i++) {
            final CRAMRecord cramRecord = createCRAMRecord(i, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, alignmentStart, SAMFlag.READ_UNMAPPED.intValue());

            Assert.assertEquals(
                    cramRecord.getBAMFlags() & SAMFlag.READ_UNMAPPED.intValue(),
                    SAMFlag.READ_UNMAPPED.intValue(), "read should be unmapped");
            Assert.assertFalse(cramRecord.getReferenceIndex() >= 0, "read should have valid ref index");
            Assert.assertTrue(cramRecord.getAlignmentStart() >= 0, "read should have a valid alignment start");

            cramRecords.add(cramRecord);
        }
        return cramRecords;
    }

    // this is unmapped/"half placed"
    public static List<CRAMRecord> createCRAMRecordsUnmappedWithReferenceIndex(final int count, final int referenceIndex) {
        ValidationUtils.validateArg(referenceIndex == REFERENCE_SEQUENCE_ZERO || referenceIndex == REFERENCE_SEQUENCE_ONE,
                "invalid reference index");
        final List<CRAMRecord> cramRecords = new ArrayList(count);
        for (int i = 0; i < count; i++) {
            cramRecords.add(createCRAMRecord(i, referenceIndex, SAMRecord.NO_ALIGNMENT_START, SAMFlag.READ_UNMAPPED.intValue()));
        }
        return cramRecords;
    }

    public static CRAMRecord createCRAMRecord(
            final int index,
            final int referenceIndex,
            final int alignmentStart,
            final int samFlag) {
        ValidationUtils.validateArg(
                referenceIndex == REFERENCE_SEQUENCE_ZERO ||
                        referenceIndex == REFERENCE_SEQUENCE_ONE ||
                referenceIndex == ReferenceContext.UNMAPPED_UNPLACED_ID,
                "invalid reference index");
        return new CRAMRecord(
                index,
                samFlag,
                0,
                "A READ NAME",
                READ_LENGTH,
                referenceIndex,
                alignmentStart,
                0,
                30,
                "!!!".getBytes(),
                "AAA".getBytes(),
                null,
                null,
                1,
                0,
                //TODO: are these the correct states for mates ?
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                SAMRecord.NO_ALIGNMENT_START,
                -1);
    }

   // assert that slices and containers have values equal to what the caller expects
    public static void assertSliceState(final Slice slice,
                                        final AlignmentContext expectedAlignmentContext,
                                        final int expectedRecordCount,
                                        final int expectedBaseCount,
                                        final long expectedGlobalRecordCounter) {
        Assert.assertEquals(slice.getAlignmentContext(), expectedAlignmentContext);
        Assert.assertEquals(slice.getNumberOfRecords(), expectedRecordCount);
        Assert.assertEquals(slice.getBaseCount(), expectedBaseCount);
        Assert.assertEquals(slice.getGlobalRecordCounter(), expectedGlobalRecordCounter);
    }

    public static void assertContainerState(final Container container,
                                            final AlignmentContext expectedAlignmentContext,
                                            final long expectedByteOffset) {
        Assert.assertEquals(container.getAlignmentContext(), expectedAlignmentContext);
        Assert.assertEquals(container.getContainerByteOffset(), expectedByteOffset);
    }

    public static void assertContainerState(final Container container,
                                            final AlignmentContext expectedAlignmentContext,
                                            final int expectedRecordCount,
                                            final int expectedBaseCount,
                                            final long expectedGlobalRecordCounter,
                                            final long expectedByteOffset) {
        Assert.assertEquals(container.getAlignmentContext(), expectedAlignmentContext);
        Assert.assertEquals(container.getContainerByteOffset(), expectedByteOffset);

        Assert.assertEquals(container.getContainerHeader().getNumberOfRecords(), expectedRecordCount);
        Assert.assertEquals(container.getContainerHeader().getBaseCount(), expectedBaseCount);
        Assert.assertEquals(container.getContainerHeader().getGlobalRecordCounter(), expectedGlobalRecordCounter);

        //TODO: this assumes a single slice
        // verify the underlying slice too
        Assert.assertEquals(container.getSlices().size(), 1);
        assertSliceState(
                container.getSlices().get(0),
                expectedAlignmentContext,
                expectedRecordCount,
                expectedBaseCount,
                expectedGlobalRecordCounter);
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
