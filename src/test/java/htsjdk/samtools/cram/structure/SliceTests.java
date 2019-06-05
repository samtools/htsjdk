package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SliceTests extends HtsjdkTest {
    private static final int TEST_RECORD_COUNT = 10;

    @Test
    public void testValidateReferenceMD5Unmapped() {
        final Container container = CRAMStructureTestHelper.createContainer(
                new ContainerFactory(
                        CRAMStructureTestHelper.SAM_FILE_HEADER,
                        new CRAMEncodingStrategy(),
                        CRAMStructureTestHelper.REFERENCE_SOURCE),
                CRAMStructureTestHelper.createSAMRecordsUnmapped(10),
                0L);

        Assert.assertEquals(container.getSlices().size(), 1);
        final Slice slice = container.getSlices().get(0);
        Assert.assertEquals(slice.getAlignmentContext().getReferenceContext(), ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);

        Assert.assertTrue(slice.referenceMD5IsValid(null));
        Assert.assertTrue(slice.referenceMD5IsValid(new byte[0]));
        Assert.assertTrue(slice.referenceMD5IsValid(new byte[1024]));
    }

    @Test
    public void testValidateReferenceMD5Mapped() {
        final SAMRecord samRecord = CRAMStructureTestHelper.createSAMRecordMapped(
                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                10);
        final Container container = CRAMStructureTestHelper.createContainer(
                new ContainerFactory(
                        CRAMStructureTestHelper.SAM_FILE_HEADER,
                        new CRAMEncodingStrategy(),
                        CRAMStructureTestHelper.REFERENCE_SOURCE),
                Collections.singletonList(samRecord),
                0L);

        Assert.assertEquals(container.getSlices().size(), 1);
        Assert.assertEquals(
                container.getAlignmentContext().getReferenceContext(),
                new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO));
        final Slice slice = container.getSlices().get(0);
        Assert.assertEquals(
                slice.getAlignmentContext().getReferenceContext(),
                new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO));
        Assert.assertEquals(
                slice.getAlignmentContext().getReferenceContext(),
                new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO));

        Assert.assertEquals(
                slice.getReferenceMD5(),
                SequenceUtil.calculateMD5(
                        CRAMStructureTestHelper.REFERENCE_SOURCE.getReferenceBases(
                                CRAMStructureTestHelper.SAM_FILE_HEADER.getSequence(
                                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),false),
                        samRecord.getAlignmentStart(),
                        samRecord.getReadLength()));
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testValidateReferenceMD5Fails() throws IOException {
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
    public void sliceStateTest(final List<CRAMCompressionRecord> records,
                               final boolean coordinateSorted,
                               final ReferenceContext expectedReferenceContext,
                               final int expectedAlignmentStart,
                               final int expectedAlignmentSpan) {
        final CompressionHeader header = new CompressionHeaderFactory(new CRAMEncodingStrategy()).createCompressionHeader(records, coordinateSorted);
        final Slice slice = new Slice(records, header, 0L, 0L);
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
        final List<CRAMCompressionRecord> records = new ArrayList<>();
        final CRAMCompressionRecord single = CRAMStructureTestHelper.createCRAMRecordsMapped(1, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO).get(0);
        records.add(single);

        final CRAMCompressionRecord unmapped = CRAMStructureTestHelper.createCRAMRecordsUnmapped(1).get(0);
        records.add(unmapped);

        final CompressionHeader header = new CompressionHeaderFactory(new CRAMEncodingStrategy()).createCompressionHeader(records, true);

        final Slice slice = new Slice(records, header, 0L, 0L);
        final int expectedBaseCount = single.getReadLength() + unmapped.getReadLength();
        CRAMStructureTestHelper.assertSliceState(
                slice,
                AlignmentContext.MULTIPLE_REFERENCE_CONTEXT,
                records.size(),
                expectedBaseCount,
                0);
    }

    private List<CRAMCompressionRecord> initAlignmentSpanRecords() {
        final List<CRAMCompressionRecord> cramCompressionRecords = new ArrayList<>();
        // mapped span 0:1,20
        cramCompressionRecords.add(CRAMStructureTestHelper.createCRAMRecordsMapped(1, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO).get(0));
        // unmapped but placed span 1:5,25
        cramCompressionRecords.add(CRAMStructureTestHelper.createCRAMRecordUnmappedPlaced(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE, 5));
        // mapped span 1:1,20
        cramCompressionRecords.add(CRAMStructureTestHelper.createCRAMRecordsMapped(1, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE).get(0));
        // unmapped span
        cramCompressionRecords.add(CRAMStructureTestHelper.createCRAMRecordsUnmapped(1).get(0));
        // span totals -> 0:1,20 and 1:1,20
        return cramCompressionRecords;
    }

    @Test
    public void testSpansCoordinateSorted() {
        final List<CRAMCompressionRecord> cramCompressionRecords = initAlignmentSpanRecords();

        // note for future refactoring
        // createHeader(records) calls CompressionHeaderBuilder.setTagIdDictionary(buildTagIdDictionary(records));
        // which is the only way to set a record's tagIdsIndex
        // which would otherwise be null

        // NOTE: multiref alignment spans are used for CRAI/BAI indexing, and only make sense when records are
        // coordinate sorted, so we only test with coordinateSorted = true;
        final CompressionHeader header = new CompressionHeaderFactory(new CRAMEncodingStrategy()).createCompressionHeader(cramCompressionRecords, true);
        final Slice slice = new Slice(cramCompressionRecords, header, 0L, 0L);
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
        final List<CRAMCompressionRecord> cramCompressionRecords = CRAMStructureTestHelper.createCRAMRecordsMapped(
                10,
                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO);

        // NOTE: multiref alignment spans are used for CRAI/BAI indexing, and only make sense when records are
        // coordinate sorted, so test that we reject coordinateSorted = false;
        final CompressionHeader header = new CompressionHeaderFactory(new CRAMEncodingStrategy()).createCompressionHeader(cramCompressionRecords, false);
        final Slice slice = new Slice(cramCompressionRecords, header, 0L, 0L);
        slice.getMultiRefAlignmentSpans(new CompressorCache(), ValidationStringency.DEFAULT_STRINGENCY);
    }

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

    @Test(expectedExceptions = CRAMException.class)
    public void testRejectEmbeddedReferenceBlockConflictsWithID() {
        final Slice slice = CRAMStructureTestHelper.createSliceWithSingleMappedRecord();
        final int embeddedReferenceBlockContentID = 27;
        slice.setEmbeddedReferenceContentID(embeddedReferenceBlockContentID);
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP,
                embeddedReferenceBlockContentID + 1, new byte[2], 2);
        slice.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testRejectResetEmbeddedReferenceBlock() {
        final Slice slice = CRAMStructureTestHelper.createSliceWithSingleMappedRecord();
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, 27, new byte[2], 2);
        slice.setEmbeddedReferenceBlock(block);
        slice.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testRejectResetEmbeddedReferenceBlockContentID() {
        final int originalBlockID = 27;
        final int conflictingBlockID = 28;

        final Slice slice = CRAMStructureTestHelper.createSliceWithSingleMappedRecord();
        slice.setEmbeddedReferenceContentID(originalBlockID);
        slice.setEmbeddedReferenceContentID(conflictingBlockID);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testRejectConflictingEmbeddedReferenceBlockContentID() {
        final int originalBlockID = 27;
        final int conflictingBlockID = 28;

        final Slice slice = CRAMStructureTestHelper.createSliceWithSingleMappedRecord();
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, originalBlockID, new byte[2], 2);
        slice.setEmbeddedReferenceContentID(conflictingBlockID);
        slice.setEmbeddedReferenceBlock(block);
    }

}
