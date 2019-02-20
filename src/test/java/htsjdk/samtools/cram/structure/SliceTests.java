package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
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

    @Test
    public void testUnmappedValidateRef() {
        Slice slice = new Slice();
        slice.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        slice.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;

        Assert.assertTrue(slice.validateRefMD5(null));
        Assert.assertTrue(slice.validateRefMD5(new byte[0]));
        Assert.assertTrue(slice.validateRefMD5(new byte[1024]));
    }

    @Test
    public void test_validateRef() {
        byte[] ref = "AAAAA".getBytes();
        final byte[] md5 = SequenceUtil.calculateMD5(ref, 0, Math.min(5, ref.length));
        Slice slice = new Slice();
        slice.sequenceId = 0;
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
        return new Object[][] {
                {
                        getSingleRefRecords(),
                        true,
                        TEST_RECORD_COUNT, 0, 1, 12
                },
                {
                        getMultiRefRecords(),
                        true,
                        TEST_RECORD_COUNT, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },
                {
                        getUnplacedRecords(),
                        true,
                        TEST_RECORD_COUNT, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },


                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

                {
                        getNoRefRecords(),
                        true,
                        TEST_RECORD_COUNT, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },

                // show that not having a valid alignment start does nothing
                // in the Coordinate-Sorted case (so it remains a Multi-Ref Slice)
                // but causes the reads to be marked as Unplaced otherwise (so it becomes an Unmapped Slice)

                {
                        getNoStartRecords(),
                        true,
                        TEST_RECORD_COUNT, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },
                {
                        getNoStartRecords(),
                        false,
                        TEST_RECORD_COUNT, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN
                },

        };
    }

    @Test(dataProvider = "forBuildTests")
    public void testBuild(final List<CramCompressionRecord> records,
                          final boolean coordinateSorted,
                          final int expectedRecordCount,
                          final int expectedSequenceId,
                          final int expectedAlignmentStart,
                          final int expectedAlignmentSpan) {
        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, coordinateSorted);
        final Slice slice = Slice.buildSlice(records, header);
        assertSliceState(slice, expectedRecordCount, expectedSequenceId, expectedAlignmentStart, expectedAlignmentSpan);
    }

    // show that a slice with a single ref will initially be built as single-ref
    // but adding an additional ref will make it multiref
    // and more will keep it multiref (mapped or otherwise)

    @Test
    public void testBuildStates() {
        final List<CramCompressionRecord> records = new ArrayList<>();

        final CramCompressionRecord record1 = createRecord(0, 0);
        records.add(record1);
        assertSliceStateFromRecords(records, 1, 0, 1, 3);

        final CramCompressionRecord record2 = createRecord(1, 1);
        records.add(record2);
        assertSliceStateFromRecords(records, 2, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);

        final CramCompressionRecord record3 = createRecord(2, 2);
        records.add(record3);
        assertSliceStateFromRecords(records, 3, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);

        final CramCompressionRecord unmapped = createRecord(3, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        unmapped.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        unmapped.setSegmentUnmapped(true);
        records.add(unmapped);
        assertSliceStateFromRecords(records, 4, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testSingleAndUnmappedBuild() {
        final List<CramCompressionRecord> records = new ArrayList<>();

        final CramCompressionRecord single = createRecord(1, 0);
        records.add(single);

        final CramCompressionRecord unmapped = createRecord(1, 0);
        unmapped.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        unmapped.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        unmapped.setSegmentUnmapped(true);
        records.add(unmapped);

        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, true);

        final Slice slice = Slice.buildSlice(records, header);
        assertSliceState(slice, 2, Slice.MULTI_REFERENCE, Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN);
    }

    private void assertSliceStateFromRecords(final List<CramCompressionRecord> records,
                                             final int expectedRecordCount,
                                             final int expectedSequenceId,
                                             final int expectedAlignmentStart,
                                             final int expectedAlignmentSpan) {
        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, true);
        final Slice slice = Slice.buildSlice(records, header);
        assertSliceState(slice, expectedRecordCount, expectedSequenceId, expectedAlignmentStart, expectedAlignmentSpan);
    }

    private void assertSliceState(final Slice slice,
                                  final int expectedRecordCount,
                                  final int expectedSequenceId,
                                  final int expectedAlignmentStart,
                                  final int expectedAlignmentSpan) {

        Assert.assertEquals(slice.nofRecords, expectedRecordCount);
        Assert.assertEquals(slice.sequenceId, expectedSequenceId);
        Assert.assertEquals(slice.alignmentStart, expectedAlignmentStart);
        Assert.assertEquals(slice.alignmentSpan, expectedAlignmentSpan);

        if (expectedSequenceId == Slice.MULTI_REFERENCE) {
            Assert.assertTrue(slice.isMultiref());
        } else if (expectedSequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            Assert.assertTrue(slice.isUnmapped());
        } else {
            Assert.assertTrue(slice.isMappedSingleRef());
        }
    }

    private CramCompressionRecord createRecord(final int index,
                                               final int sequenceId) {
        final CramCompressionRecord record = new CramCompressionRecord();
        record.readBases = "AAA".getBytes();
        record.qualityScores = "!!!".getBytes();
        record.readLength = 3;
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
