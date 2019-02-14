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

    @Test
    public void testSingleRefBuild() {
        final List<CramCompressionRecord> records = getSingleRefRecords();
        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, true);

        final Slice slice = Slice.buildSlice(records, header);

        Assert.assertEquals(slice.nofRecords, 10);
        Assert.assertEquals(slice.sequenceId, 0);
        Assert.assertEquals(slice.alignmentStart, 1);
        Assert.assertEquals(slice.alignmentSpan, 12);
    }

    @Test
    public void testMultiRefBuild() {
        final List<CramCompressionRecord> records = getMultiRefRecords();

        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, true);

        final Slice slice = Slice.buildSlice(records, header);

        Assert.assertEquals(slice.nofRecords, 10);
        Assert.assertTrue(slice.isMultiref());
        Assert.assertEquals(slice.alignmentStart, Slice.NO_ALIGNMENT_START);
        Assert.assertEquals(slice.alignmentSpan, Slice.NO_ALIGNMENT_SPAN);
    }

    // show that a slice with a single ref will initially be built as single-ref
    // but adding an additional refs will make it multiref
    // and another will keep it multiref

    @Test
    public void testBuildStates() {
        final List<CramCompressionRecord> records = new ArrayList<>();

        final CramCompressionRecord record0 = new CramCompressionRecord();
        record0.readBases = "AAA".getBytes();
        record0.qualityScores = "!!!".getBytes();
        record0.readLength = 3;
        record0.readName = "1";
        record0.sequenceId = 0;
        record0.alignmentStart = 1;
        record0.setLastSegment(true);
        record0.readFeatures = Collections.emptyList();
        record0.setSegmentUnmapped(false);

        records.add(record0);

        final CompressionHeader header0 = new CompressionHeaderFactory().build(records, null, true);
        final Slice slice0 = Slice.buildSlice(records, header0);

        Assert.assertEquals(slice0.nofRecords, 1);
        Assert.assertEquals(slice0.sequenceId, 0);
        Assert.assertEquals(slice0.alignmentStart, 1);
        Assert.assertEquals(slice0.alignmentSpan, 3);

        final CramCompressionRecord record1 = new CramCompressionRecord();
        record1.readBases = "AAA".getBytes();
        record1.qualityScores = "!!!".getBytes();
        record1.readLength = 3;
        record1.readName = "1";
        record1.sequenceId = 1;
        record1.alignmentStart = 1;
        record1.setLastSegment(true);
        record1.readFeatures = Collections.emptyList();
        record1.setSegmentUnmapped(false);

        records.add(record1);

        final CompressionHeader header1 = new CompressionHeaderFactory().build(records, null, true);
        final Slice slice1 = Slice.buildSlice(records, header1);

        Assert.assertEquals(slice1.nofRecords, 2);
        Assert.assertEquals(slice1.sequenceId, Slice.MULTI_REFERENCE);
        Assert.assertEquals(slice1.alignmentStart, Slice.NO_ALIGNMENT_START);
        Assert.assertEquals(slice1.alignmentSpan, Slice.NO_ALIGNMENT_SPAN);

        final CramCompressionRecord unmapped = new CramCompressionRecord();
        unmapped.readBases = "AAA".getBytes();
        unmapped.qualityScores = "!!!".getBytes();
        unmapped.readLength = 3;
        unmapped.readName = "1";
        unmapped.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        unmapped.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        unmapped.setLastSegment(true);
        unmapped.readFeatures = Collections.emptyList();
        unmapped.setSegmentUnmapped(true);

        records.add(unmapped);

        final CompressionHeader header2 = new CompressionHeaderFactory().build(records, null, true);
        final Slice slice2 = Slice.buildSlice(records, header2);

        Assert.assertEquals(slice2.nofRecords, 3);
        Assert.assertEquals(slice2.sequenceId, Slice.MULTI_REFERENCE);
        Assert.assertEquals(slice2.alignmentStart, Slice.NO_ALIGNMENT_START);
        Assert.assertEquals(slice2.alignmentSpan, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testUnmappedNoRefBuild() {
        final List<CramCompressionRecord> records = getNoRefRecords();

        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, true);

        final Slice slice = Slice.buildSlice(records, header);

        Assert.assertEquals(slice.nofRecords, 10);
        Assert.assertTrue(slice.isUnmapped());
        Assert.assertEquals(slice.alignmentStart, Slice.NO_ALIGNMENT_START);
        Assert.assertEquals(slice.alignmentSpan, Slice.NO_ALIGNMENT_SPAN);
    }

    // show that not having a valid alignment start does nothing in the Coordinate-Sorted case
    //      (so it remains aMulti-Ref Slice)
    // but causes the reads to be marked as Unplaced otherwise
    //      (so it becomes an Unmapped Slice)

    @Test
    public void testUnmappedNoStartBuildSorted() {
        final List<CramCompressionRecord> records = getNoStartRecords();

        final boolean coordinateSorted = true;
        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, coordinateSorted);

        final Slice slice = Slice.buildSlice(records, header);

        Assert.assertEquals(slice.nofRecords, 10);
        Assert.assertTrue(slice.isMultiref());
        Assert.assertEquals(slice.alignmentStart, Slice.NO_ALIGNMENT_START);
        Assert.assertEquals(slice.alignmentSpan, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testUnmappedNoStartBuildUnsorted() {
        final List<CramCompressionRecord> records = getNoStartRecords();

        final boolean coordinateSorted = false;
        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, coordinateSorted);

        final Slice slice = Slice.buildSlice(records, header);

        Assert.assertEquals(slice.nofRecords, 10);
        Assert.assertTrue(slice.isUnmapped());
        Assert.assertEquals(slice.alignmentStart, Slice.NO_ALIGNMENT_START);
        Assert.assertEquals(slice.alignmentSpan, Slice.NO_ALIGNMENT_SPAN);
    }

    private List<CramCompressionRecord> getSingleRefRecords() {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.readBases = "AAA".getBytes();
            record.qualityScores = "!!!".getBytes();
            record.readLength = 3;
            record.readName = "" + i;
            record.sequenceId = 0;
            record.alignmentStart = i + 1;
            record.setLastSegment(true);
            record.readFeatures = Collections.emptyList();

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
            } else {
                record.setSegmentUnmapped(false);
            }

            records.add(record);
        }
        return records;
    }

    private List<CramCompressionRecord> getMultiRefRecords() {
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.readBases = "AAA".getBytes();
            record.qualityScores = "!!!".getBytes();
            record.readLength = 3;
            record.readName = "" + i;
            record.sequenceId = i;
            record.alignmentStart = i + 1;
            record.setLastSegment(true);
            record.readFeatures = Collections.emptyList();

            if (i % 2 == 0) {
                record.setSegmentUnmapped(true);
            } else {
                record.setSegmentUnmapped(false);
            }

            records.add(record);
        }
        return records;
    }

    private List<CramCompressionRecord> getNoRefRecords() {
        final List<CramCompressionRecord> records = getMultiRefRecords();
        for (int i = 0; i < 10; i++) {
            records.get(i).sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        }
        return records;
    }

    private List<CramCompressionRecord> getNoStartRecords() {
        final List<CramCompressionRecord> records = getMultiRefRecords();
        for (int i = 0; i < 10; i++) {
            records.get(i).alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        }
        return records;
    }


    @Test
    public void testSingleAndUnmappedBuild() {
        final List<CramCompressionRecord> records = new ArrayList<>();

        final CramCompressionRecord single = new CramCompressionRecord();
        single.readBases = "AAA".getBytes();
        single.qualityScores = "!!!".getBytes();
        single.readLength = 3;
        single.readName = "1";
        single.sequenceId = 0;
        single.alignmentStart = 1;
        single.setLastSegment(true);
        single.readFeatures = Collections.emptyList();
        single.setSegmentUnmapped(false);

        records.add(single);

        final CramCompressionRecord unmapped = new CramCompressionRecord();
        unmapped.readBases = "AAA".getBytes();
        unmapped.qualityScores = "!!!".getBytes();
        unmapped.readLength = 3;
        unmapped.readName = "unmapped";
        unmapped.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        unmapped.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        unmapped.setLastSegment(true);
        unmapped.readFeatures = Collections.emptyList();
        unmapped.setSegmentUnmapped(true);

        records.add(unmapped);

        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, true);

        final Slice slice = Slice.buildSlice(records, header);

        Assert.assertEquals(slice.nofRecords, 2);
        Assert.assertTrue(slice.isMultiref());
        Assert.assertEquals(slice.alignmentStart, Slice.NO_ALIGNMENT_START);
        Assert.assertEquals(slice.alignmentSpan, Slice.NO_ALIGNMENT_SPAN);
    }
}
