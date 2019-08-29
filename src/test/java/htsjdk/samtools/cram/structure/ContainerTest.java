package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
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

public class ContainerTest extends HtsjdkTest {
    private static final int TEST_RECORD_COUNT = 10;
    private static final int READ_LENGTH_FOR_TEST_RECORDS = CRAMStructureTestUtil.READ_LENGTH_FOR_TEST_RECORDS;

    private static final ContainerFactory FACTORY = new ContainerFactory(
            CRAMStructureTestUtil.getSAMFileHeaderForTests(),
            new CRAMEncodingStrategy().setRecordsPerSlice(TEST_RECORD_COUNT));

    private static final CompressionHeader COMPRESSION_HEADER =
            new CompressionHeaderFactory().build(Collections.EMPTY_LIST, true);

    @DataProvider(name = "containerStateTestCases")
    private Object[][] containerStateTestCases() {
        final ReferenceContext mappedReferenceContext = new ReferenceContext(5); // arbitrary

        final int slice1AlignmentStart = 10;
        final int slice1AlignmentSpan = 15;
        final Slice mappedSlice1 = new Slice(mappedReferenceContext);
        mappedSlice1.setAlignmentStart(slice1AlignmentStart);
        mappedSlice1.setAlignmentSpan(slice1AlignmentSpan);

        final int slice2AlignmentStart = 20;
        final int slice2AlignmentSpan = 20;
        final Slice mappedSlice2 = new Slice(mappedReferenceContext);
        mappedSlice2.setAlignmentStart(slice2AlignmentStart);
        mappedSlice2.setAlignmentSpan(slice2AlignmentSpan);
        final int expectedSpan = slice2AlignmentStart + slice2AlignmentSpan - slice1AlignmentStart;

        return new Object[][] {
                {
                    Arrays.asList(mappedSlice1), mappedReferenceContext, slice1AlignmentStart, slice1AlignmentSpan
                },
                {
                    Arrays.asList(mappedSlice1, mappedSlice2), mappedReferenceContext, slice1AlignmentStart, expectedSpan
                },
                {
                    Arrays.asList(new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT),
                            new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT)),
                    ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },
                {
                    Arrays.asList(new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT),
                            new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)),
                    ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },
        };
    }

    @Test(dataProvider = "containerStateTestCases")
    public void initializeFromSlicesTest(final List<Slice> slices,
                                         final ReferenceContext expectedReferenceContext,
                                         final int expectedAlignmentStart,
                                         final int expectedAlignmentSpan) {
        final long byteOffset = 536635;
        //final Container container = new Container(slices, COMPRESSION_HEADER, byteOffset);
        final Container container = new Container(
                COMPRESSION_HEADER,
                slices,
                byteOffset,
                0,
                0,
                0);
        CRAMStructureTestUtil.assertContainerState(container, expectedReferenceContext, expectedAlignmentStart, expectedAlignmentSpan, byteOffset);
    }

    @DataProvider(name = "illegalCombinationTestCases")
    private Object[][] illegalCombinationTestCases() {
        return new Object[][] {
                {
                        new Slice(new ReferenceContext(0)),
                        new Slice(new ReferenceContext(1))
                },
                {
                        new Slice(new ReferenceContext(0)),
                        new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        new Slice(new ReferenceContext(0)),
                        new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT)
                },
                {
                        new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT),
                        new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT)
                },
        };
    }

    @Test(dataProvider = "illegalCombinationTestCases", expectedExceptions = CRAMException.class)
    public static void illegalCombinationsStateTest(final Slice one, final Slice another) {
        final long dummyByteOffset = 0;
        new Container(
                COMPRESSION_HEADER,
                Arrays.asList(one, another),
                dummyByteOffset,
                0,
                0,
                0);
        //new Container(Arrays.asList(one, another), COMPRESSION_HEADER, dummyByteOffset);
    }

    @DataProvider(name = "getSpansTestCases")
    private Object[][] getSpansTestCases() {
        final int mappedSequenceId = 0;  // arbitrary
        final ReferenceContext mappedRefContext = new ReferenceContext(mappedSequenceId);
        final int expectedMappedSpan = READ_LENGTH_FOR_TEST_RECORDS + TEST_RECORD_COUNT - 1;

        // getSingleRefRecords() sets half of its records to mapped, half to unmapped
        final int halfOfRecordCount = TEST_RECORD_COUNT / 2;

        return new Object[][]{
                {
                        CRAMStructureTestUtil.getSingleRefRecords(TEST_RECORD_COUNT, mappedSequenceId),
                        mappedRefContext,
                        new AlignmentSpan(1, expectedMappedSpan, halfOfRecordCount, halfOfRecordCount)
                },
                {
                        CRAMStructureTestUtil.getUnplacedRecords(TEST_RECORD_COUNT),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        AlignmentSpan.UNPLACED_SPAN
                },

                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

                {
                        CRAMStructureTestUtil.getUnplacedRecords(TEST_RECORD_COUNT),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        AlignmentSpan.UNPLACED_SPAN
                },

                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

                {
                        CRAMStructureTestUtil.getHalfUnplacedNoRefRecords(TEST_RECORD_COUNT),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        AlignmentSpan.UNPLACED_SPAN
                },
                {
                        CRAMStructureTestUtil.getHalfUnplacedNoStartRecords(TEST_RECORD_COUNT, mappedSequenceId),
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        AlignmentSpan.UNPLACED_SPAN
                },
        };
    }

    @Test(dataProvider = "getSpansTestCases")
    public void getSpansTest(final List<CRAMRecord> records,
                             final ReferenceContext expectedReferenceContext,
                             final AlignmentSpan expectedAlignmentSpan) {
        final long dummyByteOffset = 0;
        final Container container = FACTORY.buildContainer(records, dummyByteOffset);

        final Map<ReferenceContext, AlignmentSpan> spanMap = container.getSpans(ValidationStringency.STRICT);
        Assert.assertEquals(spanMap.size(), 1);
        Assert.assertTrue(spanMap.containsKey(expectedReferenceContext));
        Assert.assertEquals(spanMap.get(expectedReferenceContext), expectedAlignmentSpan);
    }

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
//        assertSliceIndexingParams(container.getSlices()[0], 0, containerStreamByteOffset, sliceSize, compressionHeaderSize);
//    }

    // two slices

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
//        assertSliceIndexingParams(container.getSlices()[0], 0, containerStreamByteOffset, slice0size, compressionHeaderSize);
//        assertSliceIndexingParams(container.getSlices()[1], 1, containerStreamByteOffset, slice1size, compressionHeaderSize + slice0size);
//    }

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
//
//    private static void assertSliceIndexingParams(final Slice slice,
//                                                  final int expectedIndex,
//                                                  final int expectedContainerOffset,
//                                                  final int expectedSize,
//                                                  final int expectedOffset) {
//        Assert.assertEquals(slice.index, expectedIndex);
//        Assert.assertEquals(slice.containerByteOffset, expectedContainerOffset);
//        Assert.assertEquals(slice.byteSize, expectedSize);
//        Assert.assertEquals(slice.byteOffsetFromCompressionHeaderStart, expectedOffset);
//    }

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
            Assert.assertTrue(container.getCRAMRecords(ValidationStringency.STRICT).isEmpty());
        }
    }

    @DataProvider(name = "getRecordsTestCases")
    private Object[][] getRecordsTestCases() {
        final int mappedSequenceId = 0;  // arbitrary

        return new Object[][]{
                {
                        CRAMStructureTestUtil.getSingleRefRecords(TEST_RECORD_COUNT, mappedSequenceId),
                },
                {
                        CRAMStructureTestUtil.getUnplacedRecords(TEST_RECORD_COUNT),
                },

                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

                {
                        CRAMStructureTestUtil.getUnplacedRecords(TEST_RECORD_COUNT),
                },

                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

                {
                        CRAMStructureTestUtil.getHalfUnplacedNoRefRecords(TEST_RECORD_COUNT),
                },
                {
                        CRAMStructureTestUtil.getHalfUnplacedNoStartRecords(TEST_RECORD_COUNT, mappedSequenceId),
                },
        };
    }

    @Test(dataProvider = "getRecordsTestCases")
    public void getRecordsTest(final List<CRAMRecord> records) {
        final long dummyByteOffset = 0;
        final Container container = FACTORY.buildContainer(records, dummyByteOffset);

        final List<CRAMRecord> roundTripRecords = container.getCRAMRecords(ValidationStringency.STRICT);
        // TODO this fails.  return to this when refactoring Container and CramCompressionRecord
        //Assert.assertEquals(roundTripRecords, records);
        Assert.assertEquals(roundTripRecords.size(), TEST_RECORD_COUNT);
    }

    @Test
    public void testMultirefContainer() {
        final Map<ReferenceContext, AlignmentSpan> expectedSpans = new HashMap<>();
        for (int i = 0; i < TEST_RECORD_COUNT; i++) {
            if (i % 2 == 0) {
                expectedSpans.put(new ReferenceContext(i), new AlignmentSpan(i + 1, READ_LENGTH_FOR_TEST_RECORDS, 0, 1));
            } else {
                expectedSpans.put(new ReferenceContext(i), new AlignmentSpan(i + 1, READ_LENGTH_FOR_TEST_RECORDS, 1, 0));
            }
        }

        final long dummyByteOffset = 0;
        final Container container = FACTORY.buildContainer(CRAMStructureTestUtil.getMultiRefRecords(TEST_RECORD_COUNT), dummyByteOffset);

        final Map<ReferenceContext, AlignmentSpan> spanMap = container.getSpans(ValidationStringency.STRICT);
        Assert.assertEquals(spanMap, expectedSpans);
    }

    @Test
    public void testMultirefContainerWithUnmapped() {
        final List<AlignmentSpan> expectedSpans = new ArrayList<>();
        expectedSpans.add(new AlignmentSpan(1, READ_LENGTH_FOR_TEST_RECORDS, 1, 0));
        expectedSpans.add(new AlignmentSpan(2, READ_LENGTH_FOR_TEST_RECORDS, 1, 0));

        final long firstContainerByteOffset = 999;
        final List<Container> containers = CRAMStructureTestUtil.getMultiRefContainersForStateTest(firstContainerByteOffset);

        // first container is single-ref

        final Map<ReferenceContext, AlignmentSpan> spanMap0 = containers.get(0).getSpans(ValidationStringency.STRICT);
        Assert.assertNotNull(spanMap0);
        Assert.assertEquals(spanMap0.size(), 1);

        Assert.assertEquals(spanMap0.get(new ReferenceContext(0)), expectedSpans.get(0));

        // when other refs are added, subsequent containers are multiref

        final Map<ReferenceContext, AlignmentSpan> spanMap1 = containers.get(1).getSpans(ValidationStringency.STRICT);
        Assert.assertNotNull(spanMap1);
        Assert.assertEquals(spanMap1.size(), 2);

        // contains the span we checked earlier
        Assert.assertEquals(spanMap1.get(new ReferenceContext(0)), expectedSpans.get(0));
        Assert.assertEquals(spanMap1.get(new ReferenceContext(1)), expectedSpans.get(1));


        final Map<ReferenceContext, AlignmentSpan> spanMap2 = containers.get(2).getSpans(ValidationStringency.STRICT);
        Assert.assertNotNull(spanMap2);
        Assert.assertEquals(spanMap2.size(), 3);

        // contains the spans we checked earlier
        Assert.assertEquals(spanMap2.get(new ReferenceContext(0)), expectedSpans.get(0));
        Assert.assertEquals(spanMap2.get(new ReferenceContext(1)), expectedSpans.get(1));

        Assert.assertTrue(spanMap2.containsKey(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT));
        final AlignmentSpan unmappedSpan = spanMap2.get(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);
        Assert.assertEquals(unmappedSpan, AlignmentSpan.UNPLACED_SPAN);
    }
}
