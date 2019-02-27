package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by vadim on 07/12/2015.
 */
public class SliceTests extends HtsjdkTest {
    private final int TEST_RECORD_COUNT = 10;
    private final int READ_LENGTH_FOR_TEST_RECORDS = 123;

    @Test
    public void testUnmappedValidateRef() {
        final Slice slice = new Slice(ReferenceContext.UNMAPPED);

        Assert.assertTrue(slice.validateRefMD5(null));
        Assert.assertTrue(slice.validateRefMD5(new byte[0]));
        Assert.assertTrue(slice.validateRefMD5(new byte[1024]));
    }

    @Test
    public void test_validateRef() {
        byte[] ref = "AAAAA".getBytes();
        final byte[] md5 = SequenceUtil.calculateMD5(ref, 0, Math.min(5, ref.length));
        final Slice slice = new Slice(new ReferenceContext(0));
        slice.alignmentSpan = 5;
        slice.alignmentStart = 1;
        slice.setRefMD5(ref);

        Assert.assertEquals(slice.refMD5, md5);
        Assert.assertTrue(slice.validateRefMD5(ref));
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testFailsMD5Check() throws IOException {
        // auxf.alteredForMD5test.fa has been altered slightly from the original reference
        // to cause the CRAM md5 check to fail
        final File CRAMFile = new File("src/test/resources/htsjdk/samtools/cram/auxf#values.3.0.cram");
        final File refFile = new File("src/test/resources/htsjdk/samtools/cram/auxf.alteredForMD5test.fa");
        ReferenceSource refSource = new ReferenceSource(refFile);
        CRAMFileReader reader = null;
        try {
            reader = new CRAMFileReader(
                    CRAMFile,
                    null,
                    refSource,
                    ValidationStringency.STRICT);
            Iterator<SAMRecord> it = reader.getIterator();
            while (it.hasNext()) {
                it.next();
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    @DataProvider(name = "forBuildTests")
    private Object[][] forBuildTests() {
        final List<Object[]> retval = new ArrayList<>();
        final boolean[] coordinateSorteds = new boolean[] { true, false };
        for (final boolean coordSorted : coordinateSorteds) {
            retval.add(new Object[]
                {
                        getSingleRefRecords(),
                        coordSorted,
                        new ReferenceContext(0), 1,
                        READ_LENGTH_FOR_TEST_RECORDS + TEST_RECORD_COUNT - 1
                });
            retval.add(new Object[]
                {
                        getMultiRefRecords(),
                        coordSorted,
                        ReferenceContext.MULTIPLE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                });
            retval.add(new Object[]
                {
                        getUnplacedRecords(),
                        coordSorted,
                        ReferenceContext.UNMAPPED, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                });


                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

            retval.add(new Object[]
                {
                        getNoRefRecords(),
                        coordSorted,
                        ReferenceContext.UNMAPPED, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                });

            retval.add(new Object[]
                {
                        getNoStartRecords(),
                        coordSorted,
                        ReferenceContext.UNMAPPED, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                });
        }

        return retval.toArray(new Object[0][0]);
    }

    @Test(dataProvider = "forBuildTests")
    public void testBuild(final List<CramCompressionRecord> records,
                          final boolean coordinateSorted,
                          final ReferenceContext expectedReferenceContext,
                          final int expectedAlignmentStart,
                          final int expectedAlignmentSpan) {
        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, coordinateSorted);
        final int globalRecordCounter = 12345;   // arbitrary
        final Slice slice = Slice.buildSlice(records, header);
        final int expectedBaseCount = TEST_RECORD_COUNT * READ_LENGTH_FOR_TEST_RECORDS;
        assertSliceState(slice, expectedReferenceContext, expectedAlignmentStart, expectedAlignmentSpan,
                TEST_RECORD_COUNT, expectedBaseCount);
    }

    // show that a slice with a single ref will initially be built as single-ref
    // but adding an additional ref will make it multiref
    // and more will keep it multiref (mapped or otherwise)

    @Test
    public void testBuildStates() {
        final List<CramCompressionRecord> records = new ArrayList<>();

        final CramCompressionRecord record1 = createRecord(0, 0);
        records.add(record1);
        assertSliceStateFromTestRecords(records, new ReferenceContext(0), 1, READ_LENGTH_FOR_TEST_RECORDS);

        final CramCompressionRecord record2 = createRecord(1, 1);
        records.add(record2);
        assertSliceStateFromTestRecords(records, ReferenceContext.MULTIPLE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);

        final CramCompressionRecord record3 = createRecord(2, 2);
        records.add(record3);
        assertSliceStateFromTestRecords(records, ReferenceContext.MULTIPLE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);

        final CramCompressionRecord unmapped = createRecord(3, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        unmapped.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        unmapped.setSegmentUnmapped(true);
        records.add(unmapped);
        assertSliceStateFromTestRecords(records, ReferenceContext.MULTIPLE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testSingleAndUnmappedBuild() {
        final List<CramCompressionRecord> records = new ArrayList<>();

        final CramCompressionRecord single = createRecord(1, 0);
        single.readLength = 20;
        records.add(single);

        final CramCompressionRecord unmapped = createRecord(1, 0);
        unmapped.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        unmapped.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        unmapped.setSegmentUnmapped(true);
        unmapped.readLength = 35;
        records.add(unmapped);

        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, true);

        final int globalRecordCounter = 98765;   // arbitrary
        final Slice slice = Slice.buildSlice(records, header);
        final int expectedBaseCount = 20 + 35;
        assertSliceState(slice, ReferenceContext.MULTIPLE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN,
                records.size(), expectedBaseCount);
    }

    private void assertSliceStateFromTestRecords(final List<CramCompressionRecord> records,
                                                 final ReferenceContext expectedReferenceContext,
                                                 final int expectedAlignmentStart,
                                                 final int expectedAlignmentSpan) {
        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, true);
        final Slice slice = Slice.buildSlice(records, header);
        final int expectedGlobalRecordCounter = 0;   // first Slice
        final int expectedBaseCount = records.size() * READ_LENGTH_FOR_TEST_RECORDS;
        assertSliceState(slice, expectedReferenceContext, expectedAlignmentStart, expectedAlignmentSpan,
                records.size(), expectedBaseCount);
    }

    private void assertSliceState(final Slice slice,
                                  final ReferenceContext expectedReferenceContext,
                                  final int expectedAlignmentStart,
                                  final int expectedAlignmentSpan,
                                  final int expectedRecordCount,
                                  final int expectedBaseCount) {

        Assert.assertEquals(slice.getReferenceContext(), expectedReferenceContext);
        Assert.assertEquals(slice.alignmentStart, expectedAlignmentStart);
        Assert.assertEquals(slice.alignmentSpan, expectedAlignmentSpan);
        Assert.assertEquals(slice.nofRecords, expectedRecordCount);
        Assert.assertEquals(slice.bases, expectedBaseCount);
    }

    private CramCompressionRecord createRecord(final int index,
                                               final int sequenceId) {
        final CramCompressionRecord record = new CramCompressionRecord();
        record.readBases = "AAA".getBytes();
        record.qualityScores = "!!!".getBytes();
        record.readLength = READ_LENGTH_FOR_TEST_RECORDS;
        record.readName = "" + index;
        record.sequenceId = sequenceId;
        record.alignmentStart = index + 1;
        record.setLastSegment(true);
        record.readFeatures = Collections.emptyList();
        record.setSegmentUnmapped(false);
        return record;
    }

    // set half of the records created as unmapped, alternating by index

    private CramCompressionRecord createRecordHalfUnmapped(final int index,
                                                           final int sequenceId) {
        final CramCompressionRecord record = createRecord(index, sequenceId);
        if (index % 2 == 0) {
            record.setSegmentUnmapped(true);
        } else {
            record.setSegmentUnmapped(false);
        }
        return record;
    }

    private List<CramCompressionRecord> getSingleRefRecords() {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int index = 0; index < TEST_RECORD_COUNT; index++) {
            records.add(createRecordHalfUnmapped(index, 0));
        }
        return records;
    }

    private List<CramCompressionRecord> getMultiRefRecords() {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int index = 0; index < TEST_RECORD_COUNT; index++) {
            records.add(createRecordHalfUnmapped(index, index));
        }
        return records;
    }

    private List<CramCompressionRecord> getUnplacedRecords() {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int index = 0; index < TEST_RECORD_COUNT; index++) {
            final CramCompressionRecord record = createRecord(index, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
            record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            record.setSegmentUnmapped(true);
            records.add(record);
        }
        return records;
    }

    // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
    // but not both.  We treat these weird edge cases as unplaced.

    private List<CramCompressionRecord> getNoRefRecords() {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int index = 0; index < TEST_RECORD_COUNT; index++) {
            final CramCompressionRecord record = createRecord(index, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
            record.setSegmentUnmapped(true);
            records.add(record);
        }
        return records;
    }

    private List<CramCompressionRecord> getNoStartRecords() {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int index = 0; index < TEST_RECORD_COUNT; index++) {
            final CramCompressionRecord record = createRecord(index, index);
            record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            record.setSegmentUnmapped(true);
            records.add(record);
        }
        return records;
    }
}
