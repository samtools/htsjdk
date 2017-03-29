package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;

/**
 * Created by vadim on 21/04/2017.
 */
public class BamFileIoUtilsTest {
    @Test
    public void testIsBamFile() {
        Assert.assertTrue(BamFileIoUtils.isBamFile(new File("test.bam")));
        Assert.assertTrue(BamFileIoUtils.isBamFile(new File("test.sam.bam")));
        Assert.assertTrue(BamFileIoUtils.isBamFile(new File(".bam")));
        Assert.assertTrue(BamFileIoUtils.isBamFile(new File("./test.bam")));
        Assert.assertTrue(BamFileIoUtils.isBamFile(new File("./test..bam")));
        Assert.assertTrue(BamFileIoUtils.isBamFile(new File("c:\\path\\to\test.bam")));

        Assert.assertFalse(BamFileIoUtils.isBamFile(new File("test.Bam")));
        Assert.assertFalse(BamFileIoUtils.isBamFile(new File("test.BAM")));
        Assert.assertFalse(BamFileIoUtils.isBamFile(new File("test.sam")));
        Assert.assertFalse(BamFileIoUtils.isBamFile(new File("test.bam.sam")));
        Assert.assertFalse(BamFileIoUtils.isBamFile(new File("testbam")));
    }
}
