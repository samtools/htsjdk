package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;

public class SAMRecordUtilTest {
    @Test public void testReverseComplement() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord rec = new SAMRecord(header);
        rec.setReadString("ACACACACAC");
        rec.setBaseQualityString("HHHHHIIIII");
        rec.setAttribute("X1", new byte[] {1,2,3,4,5});
        rec.setAttribute("X2", new short[] {1,2,3,4,5});
        rec.setAttribute("X3", new int[] {1,2,3,4,5});
        rec.setAttribute("X4", new float[] {1.0f,2.0f,3.0f,4.0f,5.0f});
        rec.setAttribute("Y1", "AAAAGAAAAC");

        SAMRecordUtil.reverseComplement(rec, Arrays.asList("Y1"), Arrays.asList("X1", "X2", "X3", "X4", "X5"));
        Assert.assertEquals(rec.getReadString(), "GTGTGTGTGT");
        Assert.assertEquals(rec.getBaseQualityString(), "IIIIIHHHHH");
        Assert.assertEquals(rec.getByteArrayAttribute("X1"), new byte[] {5,4,3,2,1});
        Assert.assertEquals(rec.getSignedShortArrayAttribute("X2"), new short[] {5,4,3,2,1});
        Assert.assertEquals(rec.getSignedIntArrayAttribute("X3"), new int[] {5,4,3,2,1});
        Assert.assertEquals(rec.getFloatArrayAttribute("X4"), new float[] {5.0f,4.0f,3.0f,2.0f,1.0f});
        Assert.assertEquals(rec.getStringAttribute("Y1"), "GTTTTCTTTT");
    }
}
