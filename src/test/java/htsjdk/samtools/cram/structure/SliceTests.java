package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

//TODO: validate mapped/unmapped/nocoord record counts
//TODO: need to round-trip CRAM/SAM records and compare

public class SliceTests extends HtsjdkTest {
    private static final int TEST_RECORD_COUNT = 10;

//    @Test
//    public void testUnmappedValidateRef() {
//        final Slice slice = new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);
//
//        Assert.assertTrue(slice.validateRefMD5(null));
//        Assert.assertTrue(slice.validateRefMD5(new byte[0]));
//        Assert.assertTrue(slice.validateRefMD5(new byte[1024]));
//    }
//
//    @Test
//    public void test_validateRef() {
//        byte[] ref = "AAAAA".getBytes();
//        final byte[] md5 = SequenceUtil.calculateMD5(ref, 0, Math.min(5, ref.length));
//        final Slice slice = new Slice(new ReferenceContext(0));
//        slice.setAlignmentSpan(5);
//        slice.setAlignmentStart(1);
//        slice.setRefMD5(ref);
//
//        Assert.assertEquals(slice.getRefMD5(), md5);
//        Assert.assertTrue(slice.validateRefMD5(ref));
//    }

    //TODO: this test should live at a higher level, although there should be slice-level test as well
    @Test(expectedExceptions = CRAMException.class)
    public void testFailsMD5Check() throws IOException {
        // auxf.alteredForMD5test.fa has been altered slightly from the original reference
        // to cause the CRAM md5 check to fail
        final File CRAMFile = new File("src/test/resources/htsjdk/samtools/cram/auxf#values.3.0.cram");
        final File refFile = new File("src/test/resources/htsjdk/samtools/cram/auxf.alteredForMD5test.fa");
        final ReferenceSource refSource = new ReferenceSource(refFile);
        try (final CRAMFileReader reader = new CRAMFileReader(
                    CRAMFile,
                    null,
                    refSource,
                    ValidationStringency.STRICT)) {
            final Iterator<SAMRecord> it = reader.getIterator();
            while (it.hasNext()) {
                it.next();
            }
        }
    }

    @DataProvider(name = "sliceStateTestCases")
    private Object[][] sliceStateTestCases() {
        final int mappedSequenceId = 0;  // arbitrary
        final ReferenceContext mappedRefContext = new ReferenceContext(mappedSequenceId);
        return new Object[][] {
                // CRAMRecords, coordinateSorted, expectedReferenceContext, expectedAlignmentStart, expectedAlignmentSpan
                {
                        CRAMStructureTestHelper.createCRAMRecordsMapped(TEST_RECORD_COUNT, mappedSequenceId),
                        true,
                        mappedRefContext,
                        1,
                        CRAMStructureTestHelper.READ_LENGTH + TEST_RECORD_COUNT - 1
                },
                {
                        CRAMStructureTestHelper.createCRAMRecordsMapped(TEST_RECORD_COUNT, mappedSequenceId),
                        false,
                        mappedRefContext,
                        1,
                        CRAMStructureTestHelper.READ_LENGTH + TEST_RECORD_COUNT - 1
                },
                {
                        CRAMStructureTestHelper.createCRAMRecordsMappedThenUnmapped(TEST_RECORD_COUNT),
                        true,
                        ReferenceContext.MULTIPLE_REFERENCE_CONTEXT,
                        0,
                        AlignmentContext.NO_ALIGNMENT_SPAN
                },
                {
                        CRAMStructureTestHelper.createCRAMRecordsMappedThenUnmapped(TEST_RECORD_COUNT),
                        false,
                        ReferenceContext.MULTIPLE_REFERENCE_CONTEXT,
                        0,
                        AlignmentContext.NO_ALIGNMENT_SPAN
                },
                {
                        CRAMStructureTestHelper.createCRAMRecordsUnmapped(TEST_RECORD_COUNT),
                        true,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        0,
                        AlignmentContext.NO_ALIGNMENT_SPAN
                },
                {
                        CRAMStructureTestHelper.createCRAMRecordsUnmapped(TEST_RECORD_COUNT),
                        false,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        0,
                        AlignmentContext.NO_ALIGNMENT_SPAN
                },

                // these sets of records are unmapped/"half" placed: they're unmapped, and have either a valid reference
                // index or valid alignment start position, but not both.  We treat these weird edge cases as unplaced.
                {
                        CRAMStructureTestHelper.createCRAMRecordsUnmappedWithAlignmentStart(TEST_RECORD_COUNT, 2),
                        true,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        0,
                        AlignmentContext.NO_ALIGNMENT_SPAN
                },
                {
                        CRAMStructureTestHelper.createCRAMRecordsUnmappedWithAlignmentStart(TEST_RECORD_COUNT, 2),
                        false,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        0,
                        AlignmentContext.NO_ALIGNMENT_SPAN
                },
                {
                        CRAMStructureTestHelper.createCRAMRecordsUnmappedWithReferenceIndex(
                                TEST_RECORD_COUNT,
                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE),
                        true,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        0,
                        AlignmentContext.NO_ALIGNMENT_SPAN
                },
                {
                        CRAMStructureTestHelper.createCRAMRecordsUnmappedWithReferenceIndex(
                                TEST_RECORD_COUNT,
                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE),
                        false,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        0,
                        AlignmentContext.NO_ALIGNMENT_SPAN
                },
        };
    }

    @Test(dataProvider = "sliceStateTestCases")
    public void sliceStateTest(final List<CRAMRecord> records,
                               final boolean coordinateSorted,
                               final ReferenceContext expectedReferenceContext,
                               final int expectedAlignmentStart,
                               final int expectedAlignmentSpan) {
        final CompressionHeader header = new CompressionHeaderFactory().build(records, coordinateSorted);
        final Slice slice = new Slice(records, header, 0L);
        final int expectedBaseCount = TEST_RECORD_COUNT * CRAMStructureTestHelper.READ_LENGTH;
        CRAMStructureTestHelper.assertSliceState(
                slice,
                new AlignmentContext(expectedReferenceContext, expectedAlignmentStart, expectedAlignmentSpan),
                TEST_RECORD_COUNT,
                expectedBaseCount,
                0L);
    }

    @Test
    public void testSingleAndUnmappedBuild() {
        final List<CRAMRecord> records = new ArrayList<>();
        final CRAMRecord single = CRAMStructureTestHelper.createCRAMRecordsMapped(1, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO).get(0);
        records.add(single);

        final CRAMRecord unmapped = CRAMStructureTestHelper.createCRAMRecordsUnmapped(1).get(0);
        records.add(unmapped);

        final CompressionHeader header = new CompressionHeaderFactory().build(records, true);

        final Slice slice = new Slice(records, header, 0);
        final int expectedBaseCount = single.getReadLength() + unmapped.getReadLength();
        CRAMStructureTestHelper.assertSliceState(
                slice,
                AlignmentContext.MULTIPLE_REFERENCE_CONTEXT,
                records.size(),
                expectedBaseCount,
                0);
    }

    private List<CRAMRecord> initAlignmentSpanRecords() {
        final List<CRAMRecord> cramRecords = new ArrayList<>();
        // mapped span 0:1,20
        cramRecords.add(CRAMStructureTestHelper.createCRAMRecordsMapped(1, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO).get(0));
        // unmapped but placed span 1:5,25
        cramRecords.add(CRAMStructureTestHelper.createCRAMRecordUnmappedPlaced(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE, 5));
        // mapped span 1:1,20
        cramRecords.add(CRAMStructureTestHelper.createCRAMRecordsMapped(1, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE).get(0));
        // unmapped span
        cramRecords.add(CRAMStructureTestHelper.createCRAMRecordsUnmapped(1).get(0));
        // span totals -> 0:1,20 and 1:1,20
        return cramRecords;
    }

    @Test
    public void testSpansCoordinateSorted() {
        final List<CRAMRecord> cramRecords = initAlignmentSpanRecords();

        // note for future refactoring
        // createHeader(records) calls CompressionHeaderBuilder.setTagIdDictionary(buildTagIdDictionary(records));
        // which is the only way to set a record's tagIdsIndex
        // which would otherwise be null

        // NOTE: multiref alignment spans are used for CRAI/BAI indexing, and only make sense when records are
        // coordinate sorted, so we only test with coordinateSorted = true;
        final CompressionHeader header = new CompressionHeaderFactory().build(cramRecords, true);
        final Slice slice = new Slice(cramRecords, header, 0L);
        final Map<ReferenceContext, AlignmentSpan> spans = slice.getMultiRefAlignmentSpans(
                new CompressorCache(),
                ValidationStringency.DEFAULT_STRINGENCY);

        Assert.assertEquals(spans.size(), 3);
        final ReferenceContext refContext0 = new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO);
        final ReferenceContext refContext1 = new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE);
        Assert.assertEquals(
                spans.get(refContext0),
                new AlignmentSpan(
                        new AlignmentContext(refContext0, 1 , 19),
                        1, 0, 0));
        Assert.assertEquals(
                spans.get(refContext1),
                new AlignmentSpan(
                        new AlignmentContext(refContext1, 1, 24),
                        1, 1, 0));
        Assert.assertEquals(
                spans.get(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT),
                new AlignmentSpan(new AlignmentContext(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, 0, 0),
                        0, 1, 1));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testRejectNonCoordinateSortedForSpans() {
        // any record will do for this test
        final List<CRAMRecord> cramRecords = CRAMStructureTestHelper.createCRAMRecordsMapped(
                10,
                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO);

        // NOTE: multiref alignment spans are used for CRAI/BAI indexing, and only make sense when records are
        // coordinate sorted, so test that we reject coordinateSorted = false;
        final CompressionHeader header = new CompressionHeaderFactory().build(cramRecords, false);
        final Slice slice = new Slice(cramRecords, header, 0L);
        slice.getMultiRefAlignmentSpans(new CompressorCache(), ValidationStringency.DEFAULT_STRINGENCY);
    }


//    @Test(dataProvider = "uninitializedBAIParameterTestCases", dataProviderClass = CRAMStructureTestUtil.class, expectedExceptions = CRAMException.class)
//    public void uninitializedBAIParameterTest(final Slice s) {
//        s.baiIndexInitializationCheck();
//    }
//
//    @Test(dataProvider = "uninitializedCRAIParameterTestCases", dataProviderClass = CRAMStructureTestUtil.class, expectedExceptions = CRAMException.class)
//    public void uninitializedCRAIParameterTest(final Slice s) {
//        s.craiIndexInitializationCheck();
//    }

    // Embedded reference block tests

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectEmbeddedReferenceBlockNoContentID() {
        final Slice slice = CRAMStructureTestHelper.createSliceWithSingleMappedRecord();

        // this test is a little bogus in that, per the spec, it shouldn't even be possible to create an external block
        // with contentID=0 in the first place, but we allow it due to  https://github.com/samtools/htsjdk/issues/1232,
        // and because we have lots of CRAM files floating around that were generated this way
        final Block block = Block.createExternalBlock(
                BlockCompressionMethod.GZIP,
                Slice.EMBEDDED_REFERENCE_ABSENT_CONTENT_ID,
                new byte[2],
                2);
        slice.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectNonExternalEmbeddedReferenceBlock() {
        final Slice slice = CRAMStructureTestHelper.createSliceWithSingleMappedRecord();
        final Block block = Block.createRawCoreDataBlock(new byte[2]);
        slice.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectEmbeddedReferenceBlockConflictsWithID() {
        final Slice slice = CRAMStructureTestHelper.createSliceWithSingleMappedRecord();
        final int embeddedReferenceBlockContentID = 27;
        slice.setEmbeddedReferenceContentID(embeddedReferenceBlockContentID);
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, embeddedReferenceBlockContentID + 1, new byte[2], 2);
        slice.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testRejectResetEmbeddedReferenceBlock() {
        final Slice slice = CRAMStructureTestHelper.createSliceWithSingleMappedRecord();
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, 27, new byte[2], 2);
        slice.setEmbeddedReferenceBlock(block);
        slice.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectResetEmbeddedReferenceBlockContentID() {
        final int originalBlockID = 27;
        final int conflictingBlockID = 28;

        final Slice slice = CRAMStructureTestHelper.createSliceWithSingleMappedRecord();
        slice.setEmbeddedReferenceContentID(originalBlockID);
        slice.setEmbeddedReferenceContentID(conflictingBlockID);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectConflictingEmbeddedReferenceBlockContentID() {
        final int originalBlockID = 27;
        final int conflictingBlockID = 28;

        final Slice slice = CRAMStructureTestHelper.createSliceWithSingleMappedRecord();
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, originalBlockID, new byte[2], 2);
        slice.setEmbeddedReferenceContentID(conflictingBlockID);
        slice.setEmbeddedReferenceBlock(block);
    }

}
