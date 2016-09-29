package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Arrays;

public class SAMRecordUtilTest {

    @Test public void testReverseComplement() {
        final SAMRecord rec = createTestSamRec();

        SAMRecordUtil.reverseComplement(rec, Arrays.asList("Y1"), Arrays.asList("X1", "X2", "X3", "X4", "X5"), true);
        Assert.assertEquals(rec.getReadString(), "GTGTGTGTGT");
        Assert.assertEquals(rec.getBaseQualityString(), "IIIIIHHHHH");
        Assert.assertEquals(rec.getByteArrayAttribute("X1"), new byte[] {5,4,3,2,1});
        Assert.assertEquals(rec.getSignedShortArrayAttribute("X2"), new short[] {5,4,3,2,1});
        Assert.assertEquals(rec.getSignedIntArrayAttribute("X3"), new int[] {5,4,3,2,1});
        Assert.assertEquals(rec.getFloatArrayAttribute("X4"), new float[] {5.0f,4.0f,3.0f,2.0f,1.0f});
        Assert.assertEquals(rec.getStringAttribute("Y1"), "GTTTTCTTTT");
    }

    @Test public void testSafeReverseComplement() throws CloneNotSupportedException {
        final SAMRecord original = createTestSamRec();
        final SAMRecord cloneOfOriginal = (SAMRecord) original.clone();
        //Runs an unsafe reverseComplement
        SAMRecordUtil.reverseComplement(cloneOfOriginal, Arrays.asList("Y1"), Arrays.asList("X1", "X2", "X3", "X4", "X5"), true);

        Assert.assertEquals(original.getReadString(), "ACACACACAC");
        Assert.assertEquals(original.getBaseQualityString(), "HHHHHIIIII");
        Assert.assertEquals(original.getByteArrayAttribute("X1"), new byte[] {1,2,3,4,5});
        Assert.assertEquals(original.getSignedShortArrayAttribute("X2"), new short[] {1,2,3,4,5});
        Assert.assertEquals(original.getSignedIntArrayAttribute("X3"), new int[] {1,2,3,4,5});
        Assert.assertEquals(original.getFloatArrayAttribute("X4"), new float[] {1.0f,2.0f,3.0f,4.0f,5.0f});
        Assert.assertEquals(original.getStringAttribute("Y1"), "AAAAGAAAAC");

        Assert.assertEquals(cloneOfOriginal.getReadString(), "GTGTGTGTGT");
        Assert.assertEquals(cloneOfOriginal.getBaseQualityString(), "IIIIIHHHHH");
        Assert.assertEquals(cloneOfOriginal.getByteArrayAttribute("X1"), new byte[] {5,4,3,2,1});
        Assert.assertEquals(cloneOfOriginal.getSignedShortArrayAttribute("X2"), new short[] {5,4,3,2,1});
        Assert.assertEquals(cloneOfOriginal.getSignedIntArrayAttribute("X3"), new int[] {5,4,3,2,1});
        Assert.assertEquals(cloneOfOriginal.getFloatArrayAttribute("X4"), new float[] {5.0f,4.0f,3.0f,2.0f,1.0f});
        Assert.assertEquals(cloneOfOriginal.getStringAttribute("Y1"), "GTTTTCTTTT");

    }


    public SAMRecord createTestSamRec() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord rec = new SAMRecord(header);
        rec.setReadString("ACACACACAC");
        rec.setBaseQualityString("HHHHHIIIII");
        rec.setAttribute("X1", new byte[] {1,2,3,4,5});
        rec.setAttribute("X2", new short[] {1,2,3,4,5});
        rec.setAttribute("X3", new int[] {1,2,3,4,5});
        rec.setAttribute("X4", new float[] {1.0f,2.0f,3.0f,4.0f,5.0f});
        rec.setAttribute("Y1", "AAAAGAAAAC");

        return(rec);
    }

}
