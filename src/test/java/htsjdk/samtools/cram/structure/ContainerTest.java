package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
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
        final CRAMEncodingStrategy encodingStrategy = new CRAMEncodingStrategy().setRecordsPerSlice(samRecords.size());
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
                .setRecordsPerSlice(samRecords.size());
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

//    @DataProvider(name = "getSpansTestCases")
//    private Object[][] getSpansTestCases() {
//        final ReferenceContext mappedRefContext = new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE);
//
//        //final int expectedMappedSpan = READ_LENGTH_FOR_TEST_RECORDS + TEST_RECORD_COUNT - 1;
//
//        // getSingleRefRecords() sets half of its records to mapped, half to unmapped
//        //final int halfOfRecordCount = TEST_RECORD_COUNT / 2;
//
//        return new Object[][]{
//                {
//                        CRAMStructureTestHelper.createMappedSAMRecords(
//                                TEST_RECORD_COUNT,
//                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE),
//                        mappedRefContext,
//                        new AlignmentSpan(
//                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE,
//                                expectedMappedSpan, halfOfRecordCount, halfOfRecordCount)
//                },
//                {
//                        CRAMStructureTestUtil.getUnplacedRecords(TEST_RECORD_COUNT),
//                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
//                        AlignmentSpan.UNPLACED_SPAN
//                },
//
//                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
//                // but not both.  We treat these weird edge cases as unplaced.
//                {
//                        CRAMStructureTestUtil.getUnplacedRecords(TEST_RECORD_COUNT),
//                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
//                        AlignmentSpan.UNPLACED_SPAN
//                },
//
//                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
//                // but not both.  We treat these weird edge cases as unplaced.
//                {
//                        CRAMStructureTestUtil.getHalfUnplacedNoRefRecords(TEST_RECORD_COUNT),
//                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
//                        AlignmentSpan.UNPLACED_SPAN
//                },
//                {
//                        CRAMStructureTestUtil.getHalfUnplacedNoStartRecords(TEST_RECORD_COUNT, mappedSequenceId),
//                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
//                        AlignmentSpan.UNPLACED_SPAN
//                },
//        };
//    }
//
//    @Test(dataProvider = "getSpansTestCases")
//    public void getSpansTest(final List<SAMRecord> samRecords,
//                             final ReferenceContext expectedReferenceContext,
//                             final AlignmentSpan expectedAlignmentSpan) {
//        final Container container = containerFactory.buildContainer(
//                samRecords,
//                CRAMStructureTestHelper.REFERENCE_SOURCE,
//                CONTAINER_BYTE_OFFSET,
//                10);
//
//        final Map<ReferenceContext, AlignmentSpan> spanMap = container.getSpans(ValidationStringency.STRICT);
//        Assert.assertEquals(spanMap.size(), 1);
//        Assert.assertTrue(spanMap.containsKey(expectedReferenceContext));
//        Assert.assertEquals(spanMap.get(expectedReferenceContext), expectedAlignmentSpan);
//    }

    // show that we can populate all of the slice indexing fields from the
    // values in the container's header

    // this is part of the serialization/deserialization process, and supports index creation

    // single slice

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

    private static void assertSliceIndexingParams(final Slice slice,
                                                  final int expectedIndex,
                                                  final int expectedContainerOffset,
                                                  final int expectedSize,
                                                  final int expectedOffset) {
        Assert.assertEquals(slice.getLandmarkIndex(), expectedIndex);
        Assert.assertEquals(slice.getByteOffsetOfContainer(), expectedContainerOffset);
        Assert.assertEquals(slice.getByteSizeOfSliceBlocks(), expectedSize);
        Assert.assertEquals(slice.getByteOffsetOfSliceHeaderBlock(), expectedOffset);
    }

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
            CramIO.issueEOF(version, baos);
            eofBytes = baos.toByteArray();
        }

        try (final ByteArrayInputStream bais = new ByteArrayInputStream(eofBytes);
             final CountingInputStream inputStream = new CountingInputStream(bais)) {
            final Container container = new Container(version, inputStream, inputStream.getCount());
            Assert.assertTrue(container.isEOF());
            Assert.assertTrue(container.getCRAMRecords(ValidationStringency.STRICT, new CompressorCache()).isEmpty());
        }
    }

    @DataProvider(name = "getRecordsTestCases")
    private Object[][] getRecordsTestCases() {
        final int mappedSequenceId = 0;  // arbitrary

        return new Object[][]{
                {
                        CRAMStructureTestHelper.createSAMRecordsMapped(TEST_RECORD_COUNT, mappedSequenceId),
                },
                {
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(TEST_RECORD_COUNT),
                },

                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

                {
                        CRAMStructureTestHelper.createSAMRecordsUnmappedWithReferenceIndex(
                                TEST_RECORD_COUNT,
                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                },
                {
                        CRAMStructureTestHelper.createSAMRecordsUnmappedWithAlignmentStart(TEST_RECORD_COUNT),
                },
        };
    }

    @Test(dataProvider = "getRecordsTestCases")
    public void getRecordsTest(final List<SAMRecord> records) {
        final long dummyByteOffset = 0;
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE);

        final Container container = CRAMStructureTestHelper.createContainer(containerFactory, records, dummyByteOffset);

        final List<CRAMRecord> roundTripRecords = container.getCRAMRecords(ValidationStringency.STRICT, new CompressorCache());
        Assert.assertEquals(roundTripRecords.size(), TEST_RECORD_COUNT);

        // TODO this fails.  return to this when refactoring Container and CramCompressionRecord
        // Container round-trips CRAM records,so perhaps these tests should use CRAM records, and
        // there should be a CRAMormalizer test for round-tripping SAMRecords
        // Assert.assertEquals(roundTripRecords, records);
    }

//    @Test
//    public void testMultirefContainer() {
//        final Map<ReferenceContext, AlignmentSpan> expectedSpans = new HashMap<>();
//        for (int i = 0; i < TEST_RECORD_COUNT; i++) {
//            if (i % 2 == 0) {
//                expectedSpans.put(new ReferenceContext(i), new AlignmentSpan(i + 1, READ_LENGTH_FOR_TEST_RECORDS, 0, 1));
//            } else {
//                expectedSpans.put(new ReferenceContext(i), new AlignmentSpan(i + 1, READ_LENGTH_FOR_TEST_RECORDS, 1, 0));
//            }
//        }
//
//        final long dummyByteOffset = 0;
//        final Container container = FACTORY.buildContainer(CRAMStructureTestUtil.getMultiRefRecords(TEST_RECORD_COUNT), dummyByteOffset);
//
//        final Map<ReferenceContext, AlignmentSpan> spanMap = container.getSpans(ValidationStringency.STRICT);
//        Assert.assertEquals(spanMap, expectedSpans);
//    }
//
//    @Test
//    public void testMultirefContainerWithUnmapped() {
//        final List<AlignmentSpan> expectedSpans = new ArrayList<>();
//        expectedSpans.add(new AlignmentSpan(1, READ_LENGTH_FOR_TEST_RECORDS, 1, 0));
//        expectedSpans.add(new AlignmentSpan(2, READ_LENGTH_FOR_TEST_RECORDS, 1, 0));
//
//        final long firstContainerByteOffset = 999;
//        final List<Container> containers = CRAMStructureTestUtil.getMultiRefContainersForStateTest(firstContainerByteOffset);
//
//        // first container is single-ref
//
//        final Map<ReferenceContext, AlignmentSpan> spanMap0 = containers.get(0).getSpans(ValidationStringency.STRICT);
//        Assert.assertNotNull(spanMap0);
//        Assert.assertEquals(spanMap0.size(), 1);
//
//        Assert.assertEquals(spanMap0.get(new ReferenceContext(0)), expectedSpans.get(0));
//
//        // when other refs are added, subsequent containers are multiref
//
//        final Map<ReferenceContext, AlignmentSpan> spanMap1 = containers.get(1).getSpans(ValidationStringency.STRICT);
//        Assert.assertNotNull(spanMap1);
//        Assert.assertEquals(spanMap1.size(), 2);
//
//        // contains the span we checked earlier
//        Assert.assertEquals(spanMap1.get(new ReferenceContext(0)), expectedSpans.get(0));
//        Assert.assertEquals(spanMap1.get(new ReferenceContext(1)), expectedSpans.get(1));
//
//
//        final Map<ReferenceContext, AlignmentSpan> spanMap2 = containers.get(2).getSpans(ValidationStringency.STRICT);
//        Assert.assertNotNull(spanMap2);
//        Assert.assertEquals(spanMap2.size(), 3);
//
//        // contains the spans we checked earlier
//        Assert.assertEquals(spanMap2.get(new ReferenceContext(0)), expectedSpans.get(0));
//        Assert.assertEquals(spanMap2.get(new ReferenceContext(1)), expectedSpans.get(1));
//
//        Assert.assertTrue(spanMap2.containsKey(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT));
//        final AlignmentSpan unmappedSpan = spanMap2.get(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);
//        Assert.assertEquals(unmappedSpan, AlignmentSpan.UNPLACED_SPAN);
//    }
}
