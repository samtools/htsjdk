package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.*;
import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.slice.Slice;
import htsjdk.samtools.cram.structure.slice.SliceBAIMetadata;
import htsjdk.samtools.cram.structure.slice.SliceHeader;
import htsjdk.samtools.cram.structure.slice.StreamableSlice;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * Created by vadim on 07/12/2015.
 */
public class SliceTests extends HtsjdkTest {
    public static StreamableSlice dummySliceForTesting(final int sequenceId,
                                                       final int alignmentStart,
                                                       final int alignmentSpan) {
        // dummy value
        final byte[] refBases = null;
        return dummySliceForTesting(sequenceId, alignmentStart, alignmentSpan, refBases);
    }

    private static StreamableSlice dummySliceForTesting(final int sequenceId,
                                                        final int alignmentStart,
                                                        final int alignmentSpan,
                                                        final byte[] refBases) {

        // dummy values

        final int recordCount = 0;
        final long globalRecordCounter = 0;
        final int blockCount = 0;
        final int[] contentIDs = new int[0];
        final int embeddedRefBlockContentID = -1;
        final SAMBinaryTagAndValue tags = null;

        final byte[] refMD5 = SliceHeader.calculateRefMD5(refBases, sequenceId, alignmentStart, alignmentSpan, globalRecordCounter);

        final SliceHeader header = new SliceHeader(sequenceId, alignmentStart, alignmentSpan, recordCount,
                globalRecordCounter, blockCount, contentIDs, embeddedRefBlockContentID, refMD5, tags);

        return new StreamableSlice(header, null, null);
    }

    private static StreamableSlice getUnmappedSlice() {
        return dummySliceForTesting(SliceHeader.NO_REFERENCE,
                SliceHeader.NO_ALIGNMENT_START,
                SliceHeader.NO_ALIGNMENT_SPAN);
    }

    @Test
    public void testUnmappedValidateRef() {
        StreamableSlice slice = getUnmappedSlice();

        slice.validateRefMD5(null);
        slice.validateRefMD5(new byte[0]);
        slice.validateRefMD5(new byte[1024]);
    }

    @Test
    public void test_validateRef() {
        byte[] refBases = "AAAAA".getBytes();
        final byte[] md5 = SequenceUtil.calculateMD5(refBases, 0, Math.min(5, refBases.length));
        final int sequenceId = 0;
        final int alignmentStart = 1;
        final int alignmentSpan = 5;
        final int globalRecordCounter = 0;
        final StreamableSlice slice = dummySliceForTesting(sequenceId, alignmentStart, alignmentSpan, refBases);

        Assert.assertEquals(SliceHeader.calculateRefMD5(refBases, sequenceId, alignmentStart, alignmentSpan, globalRecordCounter), md5);
        slice.validateRefMD5(refBases);
    }

    @DataProvider(name = "badRefs1")
    public Object[][] badRefs1() {
        return new Object[][] {
                {"AAAA".getBytes()},
                {"AATAA".getBytes()},
                {"not even bases".getBytes()},
                {SliceHeader.NO_MD5}
        };
    }

    @DataProvider(name = "badRefs2")
    public Object[][] badRefs2() {
        return new Object[][] {
                {"".getBytes()},
                {new byte[0]}
        };
    }

    private StreamableSlice testSlice() {
        byte[] refBases = "AAAAA".getBytes();
        final int sequenceId = 0;
        final int alignmentStart = 1;
        final int alignmentSpan = 5;
        return dummySliceForTesting(sequenceId, alignmentStart, alignmentSpan, refBases);
    }

    @Test(dataProvider = "badRefs1", expectedExceptions = CRAMException.class)
    public void test_validateMismatchRef(final byte[] badRefBases) {
        final StreamableSlice slice = testSlice();
        slice.validateRefMD5(badRefBases);
    }

    @Test(dataProvider = "badRefs2", expectedExceptions = RuntimeException.class)
    public void test_validateOutsideRef(final byte[] badRefBases) {
        final StreamableSlice slice = testSlice();
        slice.validateRefMD5(badRefBases);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test_validateRefNull() {
        final StreamableSlice slice = testSlice();
        slice.validateRefMD5(null);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void test_validateMultiRef() {
        byte[] refBases = "AAAAA".getBytes();
        final int sequenceId = SliceHeader.MULTI_REFERENCE;
        final int alignmentStart = 1;
        final int alignmentSpan = 5;
        final StreamableSlice slice = dummySliceForTesting(sequenceId, alignmentStart, alignmentSpan, refBases);

        slice.validateRefMD5(refBases);
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
    public void testBAIMetadata() {
        final int sequenceId = 5;
        final int alignmentStart = 10;
        final int alignmentSpan = 15;
        final int recordCount = 20;
        final int byteOffset = 25;
        final int containerByteOffset = 30;
        final int byteSize = 35;
        final int index = 40;

        final SliceHeader sh = new SliceHeader(sequenceId, alignmentStart, alignmentSpan, recordCount,
                0, 0, null, 0, null, null);
        final StreamableSlice ss = new StreamableSlice(sh, null, null);
        final Slice s = ss.withIndexingMetadata(byteOffset, byteSize, index);

        final SliceBAIMetadata b = s.getBAIMetadata(containerByteOffset);

        Assert.assertEquals(b.getSequenceId(), sequenceId);
        Assert.assertEquals(b.getAlignmentStart(), alignmentStart);
        Assert.assertEquals(b.getAlignmentSpan(), alignmentSpan);
        Assert.assertEquals(b.getRecordCount(), recordCount);
        Assert.assertEquals(b.getByteOffset(), byteOffset);
        Assert.assertEquals(b.getContainerByteOffset(), containerByteOffset);
        Assert.assertEquals(b.getByteSize(), byteSize);
        Assert.assertEquals(b.getIndex(), index);
    }

    @Test
    public void testBAIMetadataWithSpan() {
        final int sequenceId = 5;
        final int alignmentStart = 10;
        final int alignmentSpan = 15;
        final int recordCount = 20;
        final int byteOffset = 25;
        final int containerByteOffset = 30;
        final int byteSize = 35;
        final int index = 40;

        final SliceHeader sh = new SliceHeader(sequenceId, alignmentStart, alignmentSpan, recordCount,
                0, 0, null, 0, null, null);
        final StreamableSlice ss = new StreamableSlice(sh, null, null);
        final Slice s = ss.withIndexingMetadata(byteOffset, byteSize, index);

        final int sequenceId2 = 45;
        final int start2 = 50;
        final int span2 = 55;
        final int count2 = 60;
        final AlignmentSpan as = new AlignmentSpan(start2, span2, count2);
        final SliceBAIMetadata b = s.getBAIMetadata(sequenceId2, as, containerByteOffset);

        Assert.assertEquals(b.getSequenceId(), sequenceId2);
        Assert.assertEquals(b.getAlignmentStart(), start2);
        Assert.assertEquals(b.getAlignmentSpan(), span2);
        Assert.assertEquals(b.getRecordCount(), count2);
        Assert.assertEquals(b.getByteOffset(), byteOffset);
        Assert.assertEquals(b.getContainerByteOffset(), containerByteOffset);
        Assert.assertEquals(b.getByteSize(), byteSize);
        Assert.assertEquals(b.getIndex(), index);
    }

    @Test
    public void testCRAIEntry() {
        final int sequenceId = 5;
        final int alignmentStart = 10;
        final int alignmentSpan = 15;
        final int recordCount = 20;
        final int byteOffset = 25;
        final int containerByteOffset = 30;
        final int byteSize = 35;
        final int index = 40;

        final SliceHeader sh = new SliceHeader(sequenceId, alignmentStart, alignmentSpan, recordCount,
                0, 0, null, 0, null, null);
        final StreamableSlice ss = new StreamableSlice(sh, null, null);
        final Slice s = ss.withIndexingMetadata(byteOffset, byteSize, index);

        final CRAIEntry c = s.getCRAIEntry(containerByteOffset);

        Assert.assertEquals(c.getSequenceId(), sequenceId);
        Assert.assertEquals(c.getAlignmentStart(), alignmentStart);
        Assert.assertEquals(c.getAlignmentSpan(), alignmentSpan);
        Assert.assertEquals(c.getSliceByteOffset(), byteOffset);
        Assert.assertEquals(c.getContainerStartOffset(), containerByteOffset);
        Assert.assertEquals(c.getSliceByteSize(), byteSize);
     }


    @Test
    public void testCRAIEntryWithSpan() {
        final int sequenceId = 5;
        final int alignmentStart = 10;
        final int alignmentSpan = 15;
        final int recordCount = 20;
        final int byteOffset = 25;
        final int containerByteOffset = 30;
        final int byteSize = 35;
        final int index = 40;

        final SliceHeader sh = new SliceHeader(sequenceId, alignmentStart, alignmentSpan, recordCount,
                0, 0, null, 0, null, null);
        final StreamableSlice ss = new StreamableSlice(sh, null, null);
        final Slice s = ss.withIndexingMetadata(byteOffset, byteSize, index);

        final int sequenceId2 = 45;
        final int start2 = 50;
        final int span2 = 55;
        final int count2 = 60;
        final AlignmentSpan as = new AlignmentSpan(start2, span2, count2);
        final CRAIEntry c = s.getCRAIEntry(sequenceId2, as, containerByteOffset);

        Assert.assertEquals(c.getSequenceId(), sequenceId2);
        Assert.assertEquals(c.getAlignmentStart(), start2);
        Assert.assertEquals(c.getAlignmentSpan(), span2);
        Assert.assertEquals(c.getSliceByteOffset(), byteOffset);
        Assert.assertEquals(c.getContainerStartOffset(), containerByteOffset);
        Assert.assertEquals(c.getSliceByteSize(), byteSize);
    }

}
