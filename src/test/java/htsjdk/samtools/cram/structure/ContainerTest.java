package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.build.CRAMReferenceState;
import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.ref.ReferenceContext;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

// TODO: we need a test to verify that containerFactory rejects attempts lists of records that exceed the limit

public class ContainerTest extends HtsjdkTest {
    private static final int TEST_RECORD_COUNT = 2000;
    private static final long CONTAINER_BYTE_OFFSET = 536635;

    @DataProvider(name = "singleContainerAlignmentContextData")
    private Object[][] singleContainerAlignmentContextData() {
        return new Object[][]{
                {
                        CRAMStructureTestHelper.createSAMRecordsMapped(
                                TEST_RECORD_COUNT,
                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                        new AlignmentContext(
                                new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO), 1,
                                TEST_RECORD_COUNT + CRAMStructureTestHelper.READ_LENGTH - 1)
                },
                {
                        CRAMStructureTestHelper.createSAMRecordsMapped(
                                TEST_RECORD_COUNT,
                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE),
                        new AlignmentContext(
                                new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE), 1,
                                TEST_RECORD_COUNT + CRAMStructureTestHelper.READ_LENGTH - 1)
                },
                {
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(TEST_RECORD_COUNT),
                        AlignmentContext.UNMAPPED_UNPLACED_CONTEXT
                },
        };
    }

    @Test(dataProvider = "singleContainerAlignmentContextData")
    public void testSingleContainerAlignmentContext(
            final List<SAMRecord> samRecords,
            final AlignmentContext expectedAlignmentContext) {
        final CRAMEncodingStrategy encodingStrategy = new CRAMEncodingStrategy()
                // in order to set reads/slice to a small number, we must do the same for minimumSingleReferenceSliceSize
                .setMinimumSingleReferenceSliceSize(samRecords.size())
                .setReadsPerSlice(samRecords.size());
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                encodingStrategy,
                CRAMStructureTestHelper.REFERENCE_SOURCE);
        final Container container = CRAMStructureTestHelper.createContainer(containerFactory, samRecords, CONTAINER_BYTE_OFFSET);
        CRAMStructureTestHelper.assertContainerState(container, expectedAlignmentContext, CONTAINER_BYTE_OFFSET);
    }

    @DataProvider(name = "multiContainerAlignmentContextData")
    private Object[][] multiContainerAlignmentContextData() {

        final List<SAMRecord> bothReferenceSequenceRecords = new ArrayList<>();
        bothReferenceSequenceRecords.addAll(
                CRAMStructureTestHelper.createSAMRecordsMapped(
                        TEST_RECORD_COUNT,
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO)
        );
        bothReferenceSequenceRecords.addAll(
                CRAMStructureTestHelper.createSAMRecordsMapped(
                        TEST_RECORD_COUNT,
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE)
        );

        final List<SAMRecord> allRecords = new ArrayList<>();
        allRecords.addAll(bothReferenceSequenceRecords);
        allRecords.addAll(CRAMStructureTestHelper.createSAMRecordsUnmapped(TEST_RECORD_COUNT));

        return new Object[][]{
                { bothReferenceSequenceRecords,
                        Arrays.asList(
                            CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                            CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE) },
                { allRecords,
                        Arrays.asList(
                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE,
                                ReferenceContext.UNMAPPED_UNPLACED_ID) },
        };
    }

    @Test(dataProvider = "multiContainerAlignmentContextData")
    public void testMultiContainerAlignmentContext(
            final List<SAMRecord> samRecords,
            final List<ReferenceContext> referenceContexts) {
        final CRAMEncodingStrategy encodingStrategy = new CRAMEncodingStrategy()
                .setSlicesPerContainer(1)
                .setReadsPerSlice(samRecords.size());
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                encodingStrategy,
                CRAMStructureTestHelper.REFERENCE_SOURCE);
        final List<Container> containers = CRAMStructureTestHelper.createContainers(containerFactory, samRecords);
        Assert.assertEquals(containers.size(), referenceContexts.size());
        for (int i = 0; i < referenceContexts.size(); i++) {
            Assert.assertEquals(
                    containers.get(i).getAlignmentContext().getReferenceContext().getReferenceContextID(),
                    referenceContexts.get(i));
        }
    }

    @Test
    public void testExtentsForNCigarOperator() {
        final SAMRecord samRecord = new SAMRecord(CRAMStructureTestHelper.SAM_FILE_HEADER);
        samRecord.setReferenceIndex(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO);
        samRecord.setAlignmentStart(5);
        samRecord.setReadName("testRead");
        CRAMStructureTestHelper.addBasesAndQualities(samRecord);
        samRecord.setCigarString("10M1N10M");

        final int alignmentEnd = samRecord.getAlignmentEnd();
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE);
        final Container container = CRAMStructureTestHelper.createContainer(containerFactory, Collections.singletonList(samRecord), 10);
        Assert.assertEquals(
                container.getAlignmentContext(),
                new AlignmentContext(
                        new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                        5,
                        21));
    }

//    @Test
//    public static void distributeIndexingParametersToSlicesOneSlice() {
//        // this container starts 100,000 bytes into the CRAM stream
//        final int containerStreamByteOffset = 100000;
//
//        // this Container consists of:
//        // a header (size irrelevant)
//        // a Compression Header of size 7552 bytes
//        // a Slice of size 6262 bytes
//
//        final int compressionHeaderSize = 7552;
//        final int sliceSize = 6262;
//
//        final Container container = createOneSliceContainer(containerStreamByteOffset, sliceSize, compressionHeaderSize);
//
//        assertSliceIndexingParams(container.getSlices().get(0), 0, containerStreamByteOffset, sliceSize, compressionHeaderSize);
//    }
//
//    // two slices
//
//    @Test
//    public static void distributeIndexingParametersToSlicesTwoSlices() {
//        // this container starts 200,000 bytes into the CRAM stream
//        final int containerStreamByteOffset = 200000;
//
//        // this Container consists of:
//        // a header (size irrelevant)
//        // a Compression Header of size 64343 bytes
//        // a Slice of size 7890 bytes
//        // a Slice of size 5555 bytes
//
//        final int compressionHeaderSize = 64343;
//        final int slice0size = 7890;
//        final int slice1size = 5555;
//
//        final Container container = createTwoSliceContainer(containerStreamByteOffset, slice0size, slice1size, compressionHeaderSize);
//
//        assertSliceIndexingParams(container.getSlices().get(0), 0, containerStreamByteOffset, slice0size, compressionHeaderSize);
//        assertSliceIndexingParams(container.getSlices().get(1), 1, containerStreamByteOffset, slice1size, compressionHeaderSize + slice0size);
//    }
//
//    @DataProvider(name = "containerDistributeNegative")
//    private Object[][] containerDistributeNegative() {
//        final ReferenceContext refContext = new ReferenceContext(0);
//
////        final Container nullLandmarks = new Container(refContext);
////        nullLandmarks.containerBlocksByteSize = 6789;
////        nullLandmarks.landmarks = null;
//
//        final ContainerHeader nullLandmarks = new ContainerHeader(
//                6789,
//                refContext,
//                0,
//                0,
//                0,
//                0L,
//                0L,
//                0,
//                null, // null landmarks
//                0);
//        //nullLandmarks.setSlicesAndByteOffset(Collections.singletonList(new Slice(refContext)), 999);
//
//        final Container tooManyLandmarks = new Container(refContext);
//        tooManyLandmarks.containerBlocksByteSize = 111;
//        tooManyLandmarks.landmarks = new int[]{ 1, 2, 3, 4, 5 };
//
//        final ContainerHeader tooManyLandmarks = new ContainerHeader(
//                12345,
//                refContext,
//                0,
//                0,
//                0,
//                0L,
//                0L,
//                0,
//                new int[]{ 1, 2, 3, 4, 5 }, // too many landmarks
//                0);
//        //tooManyLandmarks.setSlicesAndByteOffset(Collections.singletonList(new Slice(refContext)), 12345);
//
//        final Container tooManySlices = new Container(refContext);
//        tooManySlices.containerBlocksByteSize = 675345389;
//        tooManySlices.landmarks = new int[]{ 1 };
//        tooManySlices.setSlicesAndByteOffset(Arrays.asList(new Slice(refContext), new Slice(refContext)), 12345);
//
//        final Container noByteSize = new Container(refContext);
//        noByteSize.landmarks = new int[]{ 1, 2 };
//        noByteSize.setSlicesAndByteOffset(Arrays.asList(new Slice(refContext), new Slice(refContext)), 12345);
//
//        return new Object[][] {
//                { nullLandmarks },
//                { tooManyLandmarks },
//                //{ tooManySlices },
//                //{ noByteSize },
//        };
//    }
//
//    @Test(expectedExceptions = CRAMException.class, dataProvider = "containerDistributeNegative")
//    public static void distributeIndexingParametersToSlicesNegative(final Container container) {
//        container.distributeIndexingParametersToSlices();
//    }
//
//    private static Container createOneSliceContainer(final int containerStreamByteOffset,
//                                                     final int slice0size,
//                                                     final int compressionHeaderSize) {
//        final ReferenceContext refContext = new ReferenceContext(0);
//
//        final Container oneSliceContainer = new Container(
//                COMPRESSION_HEADER,
//                Collections.singletonList(new Slice(refContext)),
//                1234,
//                0,
//                0,
//                0);
//
//        oneSliceContainer.containerBlocksByteSize = compressionHeaderSize + slice0size;
//        oneSliceContainer.landmarks = new int[]{
//                compressionHeaderSize,                // beginning of slice
//        };
//
//        oneSliceContainer.setSlicesAndByteOffset(Collections.singletonList(new Slice(refContext)), containerStreamByteOffset);
//        oneSliceContainer.distributeIndexingParametersToSlices();
//        return oneSliceContainer;
//    }
//
//    private static Container createTwoSliceContainer(final int containerStreamByteOffset,
//                                                     final int slice0size,
//                                                     final int slice1size,
//                                                     final int compressionHeaderSize) {
//        final int containerDataSize = compressionHeaderSize + slice0size + slice1size;
//
//        final ReferenceContext refContext = new ReferenceContext(0);
//
//        final Container container = new Container(refContext);
//        container.containerBlocksByteSize = containerDataSize;
//        container.landmarks = new int[]{
//                compressionHeaderSize,                // beginning of slice 1
//                compressionHeaderSize + slice0size    // beginning of slice 2
//        };
//
//        container.setSlicesAndByteOffset(Arrays.asList(new Slice(refContext), new Slice(refContext)), containerStreamByteOffset);
//        container.distributeIndexingParametersToSlices();
//        return container;
//    }

//    private static void assertSliceIndexingParams(final Slice slice,
//                                                  final int expectedIndex,
//                                                  final int expectedContainerOffset,
//                                                  final int expectedSize,
//                                                  final int expectedOffset) {
//        Assert.assertEquals(slice.getLandmarkIndex(), expectedIndex);
//        Assert.assertEquals(slice.getByteOffsetOfContainer(), expectedContainerOffset);
//        Assert.assertEquals(slice.getByteSizeOfSliceBlocks(), expectedSize);
//        Assert.assertEquals(slice.getByteOffsetOfSliceHeaderBlock(), expectedOffset);
//    }
//
    @DataProvider(name = "cramVersions")
    private Object[][] cramVersions() {
        return new Object[][] {
                {CramVersions.CRAM_v2_1},
                {CramVersions.CRAM_v3}
        };
    }

    @Test(dataProvider = "cramVersions")
    public void testEOF(final Version version) throws IOException {
        byte[] eofBytes;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CramIO.writeCRAMEOF(version, baos);
            eofBytes = baos.toByteArray();
        }

        try (final ByteArrayInputStream bais = new ByteArrayInputStream(eofBytes);
             final CountingInputStream inputStream = new CountingInputStream(bais)) {
            final Container container = new Container(version, inputStream, inputStream.getCount());
            Assert.assertTrue(container.isEOF());
            Assert.assertTrue(container.getCRAMRecords(ValidationStringency.STRICT, new CompressorCache()).isEmpty());
        }
    }

    //TODO: this needs more tests, using both coord-sorted and non-coord-sorted inputs

    @DataProvider(name = "getRecordsTestCases")
    private Object[][] getRecordsTestCases() {

        return new Object[][]{
                {
                        CRAMStructureTestHelper.createSAMRecordsMapped(TEST_RECORD_COUNT,
                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                },
                {
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(TEST_RECORD_COUNT),
                },

                // The records in these next two tests are unmapped but only "half" placed: they have either
                // a valid reference index, or a valid start position, but not both.
                // The first kind (valid reference but no start) lose their reference index when round-tripping;
                // the second kind lose their alignment start.
                //{
                //        CRAMStructureTestHelper.createSAMRecordsUnmappedWithReferenceIndex(
                //                TEST_RECORD_COUNT,
                //                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                //},
                //{
                //        CRAMStructureTestHelper.createSAMRecordsUnmappedWithAlignmentStart(TEST_RECORD_COUNT),
                //},
        };
    }

    @Test(dataProvider = "getRecordsTestCases")
    public void getRecordsTest(final List<SAMRecord> originalRecords) {
        final long dummyByteOffset = 0;
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE);

        final Container container = CRAMStructureTestHelper.createContainer(containerFactory, originalRecords, dummyByteOffset);

        final List<SAMRecord> roundTripRecords = container.getSAMRecords(
                ValidationStringency.STRICT,
                new CRAMReferenceState(CRAMStructureTestHelper.REFERENCE_SOURCE, CRAMStructureTestHelper.SAM_FILE_HEADER),
                new CompressorCache(),
                CRAMStructureTestHelper.SAM_FILE_HEADER
        );
        Assert.assertEquals(roundTripRecords.size(), TEST_RECORD_COUNT);

        // SAMRecords model referenceIndex and mateReferenceIndex using boxed integers. Semantically, null and -1
        // are equivalent, but SAMRecord.equals treats them as different, so we need to normalize the records
        // before we can compare them.
        for (final SAMRecord samRecord: originalRecords) {
            samRecord.setMateReferenceIndex(samRecord.getMateReferenceIndex());
        }

        // Container round-trips CRAM records,so perhaps these tests should use CRAM records, and
        // there should be a CRAMNormalizer test for round-tripping SAMRecords
        Assert.assertEquals(roundTripRecords, originalRecords);
    }

}
