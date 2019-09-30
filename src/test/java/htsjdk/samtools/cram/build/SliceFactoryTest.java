package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.cram.structure.CRAMStructureTestHelper;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SliceFactoryTest  extends HtsjdkTest {
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
        final SliceFactory sliceFactory = new SliceFactory(
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE,
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                0L);
        Assert.assertEquals(
                sliceFactory.shouldEmitSlice(currentReferenceContext, nextReferenceContext, nRecordsSeen),
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
        final SliceFactory sliceFactory = new SliceFactory(
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE,
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                0L);
        sliceFactory.shouldEmitSlice(currentReferenceContext, nextReferenceContext, nRecordsSeen);
    }

    @DataProvider(name="emitSliceQuerynameSortedPositive")
    private Object[][] getEmitSliceQuerynameSortedPositive() {
        final int MAPPED_REFERENCE_INDEX = 1;

        return new Object[][] {
                //We generally want to try to minimize the number of multi-ref slices we emit, since they confer
                // MULTI_REF-ness on the containing container, and they aren't efficient because they don't use
                // refernce compression. But for non-cord sorted, we're likely to emit lots of MULTI_REF slices
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
        final SAMFileHeader querySortedSAMFileHeader = CRAMStructureTestHelper.SAM_FILE_HEADER.clone();
        querySortedSAMFileHeader.setSortOrder(SAMFileHeader.SortOrder.queryname);
        final SliceFactory sliceFactory = new SliceFactory(
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE,
                querySortedSAMFileHeader,
                0L);
        Assert.assertEquals(
                sliceFactory.shouldEmitSlice(currentReferenceContext, nextReferenceContext, nRecordsSeen),
                expectedUpdatedReferenceContext
        );
    }

}
