package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO: test that the RI series is used for MULTI_REF slices

public class ContainerFactoryTest extends HtsjdkTest {

    // Coordinate sorted input, 1 slice/container
    @DataProvider(name="shouldEmitSliceSingleContainerCoordinatePositive")
    private Object[][] getShouldEmitSliceSingleContainerCoordinatePositive() {
        final int MAPPED_REFERENCE_INDEX = 1;

        return new Object[][] {
                // currentRefContextID, nextRecordRefContextID, numberOfRecordsSeen, updatedRefContextID

                // uninitialized state
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        0, ReferenceContext.UNMAPPED_UNPLACED_ID },
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID, MAPPED_REFERENCE_INDEX, 0, MAPPED_REFERENCE_INDEX },
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID, MAPPED_REFERENCE_INDEX + 1, 0, MAPPED_REFERENCE_INDEX + 1 },

                // singled mapped reference state

                // 1 record seen, must be transition to either unmapped, or same mapped
                //TODO: we currently force a container to be single ref in the coord-sorted case, since otherwise index queries fail
                { MAPPED_REFERENCE_INDEX, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, 1, ReferenceContext.UNINITIALIZED_REFERENCE_ID },
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX, 1, MAPPED_REFERENCE_INDEX },
                //TODO: we currently force a container to be single ref in the coord-sorted case, since otherwise index queries fail
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX + 1, 1, ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                // MIN_SINGLE_REF_RECORDS seen
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        MAPPED_REFERENCE_INDEX},
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD - 1,
                        MAPPED_REFERENCE_INDEX},
                // once we're over the minimum, we emit a single-ref slice
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX + 1,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { MAPPED_REFERENCE_INDEX, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD - 1,
                        ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { MAPPED_REFERENCE_INDEX, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                // > MIN_SINGLE_REF_RECORDS, but < DEFAULT_READS_PER_SLICE records seen
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1, MAPPED_REFERENCE_INDEX},
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX + 1,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { MAPPED_REFERENCE_INDEX, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1, ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                // DEFAULT_READS_PER_SLICE records seen
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,
                        ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX + 1,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { MAPPED_REFERENCE_INDEX, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                // unmapped unplaced state - for coord sorted we really can only stay in unmapped unplaced state,
                // or got to uninitialized
                { ReferenceContext.UNMAPPED_UNPLACED_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        1, ReferenceContext.UNMAPPED_UNPLACED_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD - 1, ReferenceContext.UNMAPPED_UNPLACED_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD + 1, ReferenceContext.UNMAPPED_UNPLACED_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1, ReferenceContext.UNMAPPED_UNPLACED_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE + 1, ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                // multiple reference state
                { ReferenceContext.MULTIPLE_REFERENCE_ID, MAPPED_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, MAPPED_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, MAPPED_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
        };
    }

    @Test(dataProvider = "shouldEmitSliceSingleContainerCoordinatePositive")
    private void testShouldEmitSliceSingleContainerCoordinatePositive(
            final int currentReferenceContext,
            final int nextReferenceContext,
            final int nRecordsSeen,
            final int expectedUpdatedReferenceContext) {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        samFileHeader.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        final ContainerFactory containerFactory = new ContainerFactory(
                samFileHeader,
                new CRAMEncodingStrategy(), CRAMStructureTestHelper.REFERENCE_SOURCE);
        Assert.assertEquals(
                containerFactory.shouldEmitSlice(currentReferenceContext, nextReferenceContext, nRecordsSeen),
                expectedUpdatedReferenceContext
        );
    }

    @DataProvider(name="shouldEmitSliceSingleContainerCoordinateNegative")
    private Object[][] getShouldEmitSliceSingleContainerCoordinateNegative() {
        // cases that throw because they represent illegal state that we expect to never see
        return new Object[][] {
                // numberOfRecordsSeen, currentRefContextID, nextRecordRefContextID

                // cases where record count is non-zero and we're still uninitialized
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, 1 },
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, 100 },
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX },

                // coord sorted, but mapped records show up after unmapped records
                { ReferenceContext.UNMAPPED_UNPLACED_ID, 1, 1 },
                { ReferenceContext.UNMAPPED_UNPLACED_ID, 1, ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD },
                { ReferenceContext.UNMAPPED_UNPLACED_ID, 1, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE },
        };
    }

    @Test(dataProvider = "shouldEmitSliceSingleContainerCoordinateNegative", expectedExceptions = CRAMException.class)
    private void testShouldEmitSliceSingleContainerCoordinateNegative(
            final int currentReferenceContext,
            final int nextReferenceContext,
            final int nRecordsSeen) {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        samFileHeader.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        final ContainerFactory containerFactory = new ContainerFactory(
                samFileHeader,
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE);
        containerFactory.shouldEmitSlice(currentReferenceContext, nextReferenceContext, nRecordsSeen);
    }

    // Coordinate sorted input, 1 slice/container
    @DataProvider(name="shouldEmitSliceSingleContainerQuerynamePositive")
    private Object[][] getShouldEmitSliceSingleContainerQuerynamePositive() {
        final int MAPPED_REFERENCE_INDEX = 1;

        return new Object[][]{
                // currentRefContextID, nextRecordRefContextID, numberOfRecordsSeen, updatedRefContextID

                // uninitialized state
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, 0, ReferenceContext.UNMAPPED_UNPLACED_ID },
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID, MAPPED_REFERENCE_INDEX, 0, MAPPED_REFERENCE_INDEX },
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID, MAPPED_REFERENCE_INDEX + 1, 0, MAPPED_REFERENCE_INDEX + 1 },

                // singled mapped reference state

                // 1 record seen, must be transition to either unmapped, or same mapped
                // this one differs from coord sorted, we go to multi-ref
                { MAPPED_REFERENCE_INDEX, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, 1, ReferenceContext.MULTIPLE_REFERENCE_ID },
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX, 1, MAPPED_REFERENCE_INDEX },
                // this one differs from coord sorted, we go to multi-ref
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX + 1, 1, ReferenceContext.MULTIPLE_REFERENCE_ID}, // differs from coord

                // MIN_SINGLE_REF_RECORDS seen
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        MAPPED_REFERENCE_INDEX},
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX + 1,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { MAPPED_REFERENCE_INDEX, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                // > MIN_SINGLE_REF_RECORDS, but < DEFAULT_READS_PER_SLICE records seen
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1, MAPPED_REFERENCE_INDEX},
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX + 1, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1,
                        ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { MAPPED_REFERENCE_INDEX, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE -1, ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                // DEFAULT_READS_PER_SLICE records seen
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,
                        ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX + 1,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { MAPPED_REFERENCE_INDEX, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                // unmapped unplaced state - for coord sorted we really can only stay in unmapped unplaced state,
                // or got to uninitialized
                { ReferenceContext.UNMAPPED_UNPLACED_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        1, ReferenceContext.UNMAPPED_UNPLACED_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD - 1, ReferenceContext.UNMAPPED_UNPLACED_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD, ReferenceContext.UNMAPPED_UNPLACED_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD + 1, ReferenceContext.UNMAPPED_UNPLACED_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1, ReferenceContext.UNMAPPED_UNPLACED_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE, ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                { ReferenceContext.UNMAPPED_UNPLACED_ID, 1,
                        1, ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, 1,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD - 1, ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, 1,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD, ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, 1,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD + 1, ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, 1,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1, ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, 1,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE, ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                // multiple reference state
                { ReferenceContext.MULTIPLE_REFERENCE_ID, MAPPED_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1, ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1,
                        ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, MAPPED_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,
                        ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, MAPPED_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, MAPPED_REFERENCE_INDEX,
                        ContainerFactory.MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD + 1,
                        ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, MAPPED_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1,
                        ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, MAPPED_REFERENCE_INDEX,
                        CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE + 1,
                        ReferenceContext.UNINITIALIZED_REFERENCE_ID},
        };
    }

    //TODO: Are there any queryName Negative test cases ?
    @Test(dataProvider = "shouldEmitSliceSingleContainerQuerynamePositive")
    private void testShouldEmitSliceSingleContainerQuerynamePositive(
            final int currentReferenceContext,
            final int nextReferenceContext,
            final int nRecordsSeen,
            final int expectedUpdatedReferenceContext) {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        samFileHeader.setSortOrder(SAMFileHeader.SortOrder.queryname);
        final ContainerFactory containerFactory = new ContainerFactory(
                samFileHeader,
                new CRAMEncodingStrategy(), CRAMStructureTestHelper.REFERENCE_SOURCE);
        Assert.assertEquals(
                containerFactory.shouldEmitSlice(currentReferenceContext, nextReferenceContext, nRecordsSeen),
                expectedUpdatedReferenceContext
        );
    }

    @DataProvider(name="singleContainerSlicePartitioning")
    private Object[][] getSingleContainerSlicePartitioning() {
        final int RECORDS_PER_SLICE = 100;
        return new Object[][] {
                // List<SAMRecord>, records/slice, slices/container, expected slice count, expected record count for each slice
                {
                        // 1 full single-ref slice with 1 rec
                        CRAMStructureTestHelper.createMappedSAMRecords(1, 0),
                        RECORDS_PER_SLICE, 1,
                        1, Arrays.asList(1)
                },
                {
                        // 1 full single-ref slice with 1 rec, but allow > 1 slices/container
                        CRAMStructureTestHelper.createMappedSAMRecords(1, 0),
                        RECORDS_PER_SLICE, 2,
                        1, Arrays.asList(1)
                },
                {
                        // 1 full single-ref slice with RECORDS_PER_SLICE - 1 records
                        CRAMStructureTestHelper.createMappedSAMRecords(RECORDS_PER_SLICE - 1, 0),
                        RECORDS_PER_SLICE, 1,
                        1, Arrays.asList(RECORDS_PER_SLICE - 1)
                },
                {
                        // 1 full single-ref slice with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createMappedSAMRecords(RECORDS_PER_SLICE, 0),
                        RECORDS_PER_SLICE, 1,
                        1, Arrays.asList(RECORDS_PER_SLICE)
                },
                {
                        // 2 single-ref slices, one with RECORDS_PER_SLICE records, one with 1 record
                        CRAMStructureTestHelper.createMappedSAMRecords(RECORDS_PER_SLICE + 1, 0),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE, 1)
                },
                {
                        // 2 full single-ref slices, each with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createMappedSAMRecords(RECORDS_PER_SLICE * 2, 0),
                        RECORDS_PER_SLICE, 2, 2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE)
                },
                {
                        // 3 full single-ref slices, each with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createMappedSAMRecords(RECORDS_PER_SLICE * 3, 0),
                        RECORDS_PER_SLICE, 3, 3, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE, RECORDS_PER_SLICE)
                },

                // now repeat the tests, but using unmapped records
                {
                        // 1 full single-ref slice with 1 rec
                        CRAMStructureTestHelper.createUnmappedSAMRecords(1),
                        RECORDS_PER_SLICE, 1,
                        1, Arrays.asList(1)
                },
                {
                        // 1 full single-ref slice with 1 rec, but allow > 1 slices/container
                        CRAMStructureTestHelper.createUnmappedSAMRecords(1),
                        RECORDS_PER_SLICE, 2,
                        1, Arrays.asList(1)
                },
                {
                        // 1 full single-ref slice with RECORDS_PER_SLICE - 1 records
                        CRAMStructureTestHelper.createUnmappedSAMRecords(RECORDS_PER_SLICE - 1),
                        RECORDS_PER_SLICE, 1,
                        1, Arrays.asList(RECORDS_PER_SLICE - 1)
                },
                {
                        // 1 full single-ref slice with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createUnmappedSAMRecords(RECORDS_PER_SLICE),
                        RECORDS_PER_SLICE, 1,
                        1, Arrays.asList(RECORDS_PER_SLICE)
                },
                {
                        // 2 single-ref slices, one with RECORDS_PER_SLICE records, one with 1 record
                        CRAMStructureTestHelper.createUnmappedSAMRecords(RECORDS_PER_SLICE + 1),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE, 1)
                },
                {
                        // 2 full single-ref slices, each with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createUnmappedSAMRecords(RECORDS_PER_SLICE * 2),
                        RECORDS_PER_SLICE, 2, 2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE)
                },
                {
                        // 3 full single-ref slices, each with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createUnmappedSAMRecords(RECORDS_PER_SLICE * 3),
                        RECORDS_PER_SLICE, 3, 3, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE, RECORDS_PER_SLICE)
                },
        };
    }

    @Test(dataProvider = "singleContainerSlicePartitioning")
    public void testSingleContainerSlicePartitioning(
            final List<SAMRecord> samRecords,
            final int recordsPerSlice,
            final int slicesPerContainer,
            final int expectedSliceCount,
            final List<Integer> expectedSliceRecordCounts) {
        final CRAMEncodingStrategy cramEncodingStrategy =
                new CRAMEncodingStrategy().setRecordsPerSlice(recordsPerSlice).setSlicesPerContainer(slicesPerContainer);
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                cramEncodingStrategy,
                CRAMStructureTestHelper.REFERENCE_SOURCE);

        final Container container = CRAMStructureTestHelper.getSingleContainerFromRecords(containerFactory, samRecords, 0);
        Assert.assertNotNull(container);
        final List<Slice> slices = container.getSlices();
        Assert.assertEquals(slices.size(), expectedSliceCount);
        for (int i = 0; i < slices.size(); i++) {
            Assert.assertEquals(
                    (Integer) slices.get(i).getNumberOfRecords(),
                    expectedSliceRecordCounts.get(i));
        }
    }

    @DataProvider(name="multipleContainerSlicePartitioning")
    private Object[][] getMultipleContainerSlicePartitioning() {
        final int RECORDS_PER_SLICE = 100;
        return new Object[][]{
                // List<SAMRecord>, records/slice, slices/container, expected container count, expected record count for each container
                {
                        // this generates two containers since it has two containers worth of records mapped to a single ref
                        CRAMStructureTestHelper.createMappedSAMRecords(RECORDS_PER_SLICE * 2, 0),
                        RECORDS_PER_SLICE, 1,
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE)
                },
                {
                        CRAMStructureTestHelper.createMappedSAMRecords(RECORDS_PER_SLICE * 3 + 1, 0),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE * 2, RECORDS_PER_SLICE+ 1)
                },
                {
                        CRAMStructureTestHelper.createMappedSAMRecords(RECORDS_PER_SLICE * 4, 0),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE * 2, RECORDS_PER_SLICE * 2)
                },
                {
                        // this generates two containers since it has two mapped ref indexes
                        Stream.of(
                                CRAMStructureTestHelper.createMappedSAMRecords(RECORDS_PER_SLICE - 1, 0),
                                CRAMStructureTestHelper.createMappedSAMRecords(1, 1))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 1,
                        2, Arrays.asList(RECORDS_PER_SLICE - 1, 1)
                },
                {
                        // this generates two containers since it has some mapped and one unmapped
                        Stream.of(
                                CRAMStructureTestHelper.createMappedSAMRecords(RECORDS_PER_SLICE - 1, 0),
                                CRAMStructureTestHelper.createUnmappedSAMRecords(1))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 1,
                        2, Arrays.asList(RECORDS_PER_SLICE - 1, 1)
                },
                {
                        // this generates two containers since it has some mapped and one unmapped
                        Stream.of(
                                CRAMStructureTestHelper.createMappedSAMRecords(RECORDS_PER_SLICE * 2, 0),
                                CRAMStructureTestHelper.createUnmappedSAMRecords(RECORDS_PER_SLICE))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE * 2, RECORDS_PER_SLICE)
                },
                {
                        // this generates two containers since it has some mapped and one unmapped
                        Stream.of(
                                CRAMStructureTestHelper.createMappedSAMRecords(RECORDS_PER_SLICE / 2, 0),
                                CRAMStructureTestHelper.createUnmappedSAMRecords(RECORDS_PER_SLICE))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 1,
                        2, Arrays.asList(RECORDS_PER_SLICE / 2, RECORDS_PER_SLICE)
                },
        };
    }

    @Test(dataProvider = "multipleContainerSlicePartitioning")
    public void testMultipleContainerRecordsPerContainer(
        final List<SAMRecord> samRecords,
        final int recordsPerSlice,
        final int slicesPerContainer,
        final int expectedContainerCount,
        final List<Integer> expectedContainerRecordCounts) {
        final CRAMEncodingStrategy cramEncodingStrategy =
                new CRAMEncodingStrategy().setRecordsPerSlice(recordsPerSlice).setSlicesPerContainer(slicesPerContainer);
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                cramEncodingStrategy,
                CRAMStructureTestHelper.REFERENCE_SOURCE);
        final List<Container> containers = CRAMStructureTestHelper.getAllContainersFromRecords(containerFactory, samRecords);
        Assert.assertEquals(containers.size(), expectedContainerCount);

        for (int i = 0; i < containers.size(); i++) {
            Assert.assertEquals(
                    (Integer) containers.get(i).getContainerHeader().getNumberOfRecords(),
                    expectedContainerRecordCounts.get(i));
        }
    }

}
