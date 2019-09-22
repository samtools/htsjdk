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

    @DataProvider(name="emitSliceCoordinateSortedPositive")
    private Object[][] getEmitSliceCoordinateSortedPositive() {
        final int MAPPED_REFERENCE_INDEX = 1;

        // NOTE: These tests use, and assume, the default CRAMEncodingStrategy readsPerSlice and
        // minimumSingleSliceReferenceSize values

        return new Object[][] {
                // currentRefContextID, nextRecordRefContextID, numberOfRecordsSeen, updatedRefContextID

                // uninitialized state
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID, 0, MAPPED_REFERENCE_INDEX,  MAPPED_REFERENCE_INDEX },
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID, 0, MAPPED_REFERENCE_INDEX + 1,  MAPPED_REFERENCE_INDEX + 1 },
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID, 0,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.UNMAPPED_UNPLACED_ID },

                // singled mapped reference state
                { MAPPED_REFERENCE_INDEX, 1, MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX },
                { MAPPED_REFERENCE_INDEX, 1, MAPPED_REFERENCE_INDEX + 1, ReferenceContext.MULTIPLE_REFERENCE_ID },
                { MAPPED_REFERENCE_INDEX, 1, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.MULTIPLE_REFERENCE_ID },

                { MAPPED_REFERENCE_INDEX, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD - 1,
                        MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX },
                { MAPPED_REFERENCE_INDEX, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD - 1,
                        MAPPED_REFERENCE_INDEX + 1, ReferenceContext.MULTIPLE_REFERENCE_ID },
                { MAPPED_REFERENCE_INDEX, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD - 1,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.MULTIPLE_REFERENCE_ID },

                { MAPPED_REFERENCE_INDEX, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX },
                { MAPPED_REFERENCE_INDEX, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        MAPPED_REFERENCE_INDEX + 1, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { MAPPED_REFERENCE_INDEX, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                { MAPPED_REFERENCE_INDEX, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1,
                        MAPPED_REFERENCE_INDEX, MAPPED_REFERENCE_INDEX},
                { MAPPED_REFERENCE_INDEX, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1,
                        MAPPED_REFERENCE_INDEX + 1, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { MAPPED_REFERENCE_INDEX, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                { MAPPED_REFERENCE_INDEX, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,
                        MAPPED_REFERENCE_INDEX, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { MAPPED_REFERENCE_INDEX, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,
                        MAPPED_REFERENCE_INDEX + 1, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { MAPPED_REFERENCE_INDEX, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                // multiple reference state
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD - 1,
                        MAPPED_REFERENCE_INDEX, ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD - 1,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.MULTIPLE_REFERENCE_ID},

                //We generally want to try to minimize the number of multi-ref slices we emit, since they confer
                // MULTI_REF-ness on the containing container, and they aren't efficient because they're disable
                // reference-compression. So for coordinate-sorted inputs, the current policy emits a MULTI_REF
                // slice once we've accumulated MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD reads, in order to keep
                // the size of multi-ref slices to a minimum, on the optimistic theory that for coord-sorted,
                // most likely doing so will put the next slice back on track for single-ref(mapped or unmapped).
                // These next six test cases validate that policy.
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        MAPPED_REFERENCE_INDEX, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        MAPPED_REFERENCE_INDEX + 1, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1,
                        MAPPED_REFERENCE_INDEX, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1,
                        MAPPED_REFERENCE_INDEX + 1, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,
                        MAPPED_REFERENCE_INDEX, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,
                        MAPPED_REFERENCE_INDEX + 1, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.UNINITIALIZED_REFERENCE_ID},

                // unmapped unplaced state - for coord sorted we really can only stay in unmapped/unplaced state,
                // or got to uninitialized. We should never to to multiple, since that would require seeing
                // a mapped record AFTER seeing unmapped records, which should never happen in coordinate
                // sorted inputs.
                { ReferenceContext.UNMAPPED_UNPLACED_ID, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD - 1,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.UNMAPPED_UNPLACED_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.UNMAPPED_UNPLACED_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.UNMAPPED_UNPLACED_ID},
                { ReferenceContext.UNMAPPED_UNPLACED_ID, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.UNINITIALIZED_REFERENCE_ID},
        };
    }

    @Test(dataProvider = "emitSliceCoordinateSortedPositive")
    private void testEmitSliceCoordinateSortedPositive(
            final int currentReferenceContext,
            final int nRecordsSeen,
            final int nextReferenceContext,
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

    @DataProvider(name="emitSliceCoordinateSortedNegative")
    private Object[][] getEmitSliceCoordinateSortedNegative() {
        // cases that throw because they represent illegal state that we expect to never see
        return new Object[][] {
                // numberOfRecordsSeen, currentRefContextID, nextRecordRefContextID

                // cases where record count is non-zero and we're still uninitialized
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, 1 },
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, 100 },
                { ReferenceContext.UNINITIALIZED_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX },

                // coord sorted, but mapped records show up after unmapped records
                { ReferenceContext.UNMAPPED_UNPLACED_ID, 1, 1 },
                { ReferenceContext.UNMAPPED_UNPLACED_ID, 1, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD },
                { ReferenceContext.UNMAPPED_UNPLACED_ID, 1, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE },
        };
    }

    @Test(dataProvider = "emitSliceCoordinateSortedNegative", expectedExceptions = CRAMException.class)
    private void testEmitSliceCoordinateSortedNegative(
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

    @DataProvider(name="emitSliceQuerynameSortedPositive")
    private Object[][] getEmitSliceQuerynameSortedPositive() {
        final int MAPPED_REFERENCE_INDEX = 1;

        return new Object[][] {
                //We generally want to try to minimize the number of multi-ref slices we emit, since they confer
                // MULTI_REF-ness on the containing container, and they aren't efficient because they're disable
                // reference-compression. But for non-cord sorted, we're likely to emit lots of MULTI_REF slices
                // anyway (since we're less likely to accumulate a stream of reads mapped to the same contig).
                //
                // These tests are the cases that are handled differently for non-coord sorted inputs. Specifically,
                // for non-coordinate sorted, when in multi-ref context, we continue to accumulate reads until we
                // achieve a full slice of records, rather than emitting at MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD.
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        MAPPED_REFERENCE_INDEX, ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        MAPPED_REFERENCE_INDEX + 1, ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1,
                        MAPPED_REFERENCE_INDEX, ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1,
                        MAPPED_REFERENCE_INDEX + 1, ReferenceContext.MULTIPLE_REFERENCE_ID},
                { ReferenceContext.MULTIPLE_REFERENCE_ID, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE - 1,
                        SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, ReferenceContext.MULTIPLE_REFERENCE_ID},
        };
    }

    @Test(dataProvider = "emitSliceQuerynameSortedPositive")
    private void testEmitSliceQuerynameSortedPositive(
            final int currentReferenceContext,
            final int nRecordsSeen,
            final int nextReferenceContext,
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
                // samRecords, records/slice, slices/container, expected refContext id, expected slice count,
                // expected slice refContexts, expected record count/slice
                {
                        // 1 full single-ref slice with 1 rec
                        CRAMStructureTestHelper.createSAMRecordsMapped(1, 0),
                        RECORDS_PER_SLICE, 1,
                        new ReferenceContext(0),
                        1, Arrays.asList(1), Arrays.asList(new ReferenceContext(0))
                },
                {
                        // 1 full single-ref slice with 1 rec, but allow > 1 slices/container
                        CRAMStructureTestHelper.createSAMRecordsMapped(1, 0),
                        RECORDS_PER_SLICE, 2,
                        new ReferenceContext(0),
                        1, Arrays.asList(1), Arrays.asList(new ReferenceContext(0))
                },
                {
                        // 1 full single-ref slice with RECORDS_PER_SLICE - 1 records
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE - 1, 0),
                        RECORDS_PER_SLICE, 1,
                        new ReferenceContext(0),
                        1, Arrays.asList(RECORDS_PER_SLICE - 1), Arrays.asList(new ReferenceContext(0))
                },
                {
                        // 1 full single-ref slice with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE, 0),
                        RECORDS_PER_SLICE, 1,
                        new ReferenceContext(0),
                        1, Arrays.asList(RECORDS_PER_SLICE), Arrays.asList(new ReferenceContext(0))
                },
                {
                        // 2 single-ref slices, one with RECORDS_PER_SLICE records, one with 1 record
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE + 1, 0),
                        RECORDS_PER_SLICE, 2,
                        new ReferenceContext(0),
                        2, Arrays.asList(RECORDS_PER_SLICE, 1),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0))
                },
                {
                        // 2 full single-ref slices, each with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 2, 0),
                        RECORDS_PER_SLICE, 2,
                        new ReferenceContext(0),
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0))
                },
                {
                        // 3 full single-ref slices, each with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 3, 0),
                        RECORDS_PER_SLICE, 3,
                        new ReferenceContext(0),
                        3, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0), new ReferenceContext(0))
                },

                // now repeat the tests, but using unmapped records
                {
                        // 1 full single-ref (unmapped) slice with 1 rec
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(1),
                        RECORDS_PER_SLICE, 1,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        1, Arrays.asList(1), Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 1 full single-ref (unmapped) slice with 1 rec, but allow > 1 slices/container
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(1),
                        RECORDS_PER_SLICE, 2,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        1, Arrays.asList(1), Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 1 full single-ref (unmapped) slice with RECORDS_PER_SLICE - 1 records
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE - 1),
                        RECORDS_PER_SLICE, 1,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        1, Arrays.asList(RECORDS_PER_SLICE - 1), Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 1 full single-ref (unmapped) slice with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE),
                        RECORDS_PER_SLICE, 1,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        1, Arrays.asList(RECORDS_PER_SLICE), Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 2 single-ref (unmapped) slices, one with RECORDS_PER_SLICE records, one with 1 record
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE + 1),
                        RECORDS_PER_SLICE, 2,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        2, Arrays.asList(RECORDS_PER_SLICE, 1),
                        Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 2 full single-ref (unmapped) slices, each with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE * 2),
                        RECORDS_PER_SLICE, 2,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 3 full single-ref (unmapped) slices, each with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE * 3),
                        RECORDS_PER_SLICE, 3,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        3, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(
                                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
        };
    }

    @Test(dataProvider = "singleContainerSlicePartitioning")
    public void testSingleContainerSlicePartitioning(
            final List<SAMRecord> samRecords,
            final int readsPerSlice,
            final int slicesPerContainer,
            final ReferenceContext expectedContainerReferenceContext,
            final int expectedSliceCount,
            final List<Integer> expectedSliceRecordCounts,
            final List<ReferenceContext> expectedSliceReferenceContexts) {
        final CRAMEncodingStrategy cramEncodingStrategy =
                new CRAMEncodingStrategy()
                        .setMinimumSingleReferenceSliceSize(readsPerSlice)
                        .setReadsPerSlice(readsPerSlice)
                        .setSlicesPerContainer(slicesPerContainer);
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                cramEncodingStrategy,
                CRAMStructureTestHelper.REFERENCE_SOURCE);

        final Container container = CRAMStructureTestHelper.createContainer(containerFactory, samRecords, 0);
        Assert.assertNotNull(container);
        Assert.assertEquals(container.getAlignmentContext().getReferenceContext(), expectedContainerReferenceContext);

        final List<Slice> slices = container.getSlices();
        Assert.assertEquals(slices.size(), expectedSliceCount);
        for (int i = 0; i < slices.size(); i++) {
            Assert.assertEquals(
                    (Integer) slices.get(i).getNumberOfRecords(),
                    expectedSliceRecordCounts.get(i));
            Assert.assertEquals(
                    slices.get(i).getAlignmentContext().getReferenceContext(),
                    expectedSliceReferenceContexts.get(i));
        }
    }

    @DataProvider(name="multipleContainerSlicePartitioning")
    private Object[][] getMultipleContainerSlicePartitioning() {
        final int RECORDS_PER_SLICE = 100;
        return new Object[][] {
                //TODO: add expected container reference contexts!!!!!!
                // samRecords, records/slice, slices/container, expected container count, expected record count for each container
                {
                        // this generates two containers since it has two containers worth of records mapped to a single ref
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 2, 0),
                        RECORDS_PER_SLICE, 1,
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE)
                },
                {
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 3 + 1, 0),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE * 2, RECORDS_PER_SLICE+ 1)
                },
                {
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 4, 0),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE * 2, RECORDS_PER_SLICE * 2)
                },
                {
                        // this generates two containers since it has two mapped ref indexes
                        Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE - 1, 0),
                                CRAMStructureTestHelper.createSAMRecordsMapped(1, 1))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 1,
                        1, Arrays.asList(RECORDS_PER_SLICE)
                },
                {
                        // this generates two containers since it has some mapped and one unmapped
                        Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE - 1, 0),
                                CRAMStructureTestHelper.createSAMRecordsUnmapped(1))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 1,
                        1, Arrays.asList(RECORDS_PER_SLICE)
                },
                {
                        // this generates two containers since it has some mapped and one unmapped
                        Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 2, 0),
                                CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE * 2, RECORDS_PER_SLICE)
                },
                {
                        // this generates two containers since it has some mapped and one unmapped
                        Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE / 2, 0),
                                CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 1,
                        // first container is multi ref and contains half of the records from the first set of mapped
                        // records; the remaining go into the second container
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE / 2)
                },
                {
                        // this generates two containers since it has some mapped and one unmapped
                        // TODO: even though we allow 2 slices/container; because we won't emit a second
                        // single-ref slice into the first container once we've emitted the multi-ref slice!
                        Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE / 2, 0),
                                CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 2,
                        // first container is multi ref and contains half of the records from the first set of mapped
                        // records; the remaining go into the second container
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE / 2)
                },
        };
    }

    @Test(dataProvider = "multipleContainerSlicePartitioning")
    public void testMultipleContainerRecordsPerContainer(
        final List<SAMRecord> samRecords,
        final int readsPerSlice,
        final int slicesPerContainer,
        final int expectedContainerCount,
        final List<Integer> expectedContainerRecordCounts) {
        final CRAMEncodingStrategy cramEncodingStrategy =
                new CRAMEncodingStrategy()
                    .setMinimumSingleReferenceSliceSize(readsPerSlice)
                    .setReadsPerSlice(readsPerSlice)
                    .setSlicesPerContainer(slicesPerContainer);
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                cramEncodingStrategy,
                CRAMStructureTestHelper.REFERENCE_SOURCE);
        final List<Container> containers = CRAMStructureTestHelper.createContainers(containerFactory, samRecords);
        Assert.assertEquals(containers.size(), expectedContainerCount);

        for (int i = 0; i < containers.size(); i++) {
            Assert.assertEquals(
                    (Integer) containers.get(i).getContainerHeader().getNumberOfRecords(),
                    expectedContainerRecordCounts.get(i));
        }
    }

}
