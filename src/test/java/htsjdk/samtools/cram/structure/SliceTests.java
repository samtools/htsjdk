package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * Created by vadim on 07/12/2015.
 */
public class SliceTests extends HtsjdkTest {
    @Test
    public void testUnmappedValidateRef() {
        Slice slice = new Slice();
        slice.alignmentStart= SAMRecord.NO_ALIGNMENT_START;
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
        slice.sequenceId=0;
        slice.alignmentSpan=5;
        slice.alignmentStart=1;
        slice.setRefMD5(ref);

        Assert.assertEquals(slice.refMD5, md5);
        Assert.assertTrue(slice.validateRefMD5(ref));
    }

    @Test(expectedExceptions= CRAMException.class)
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
}
