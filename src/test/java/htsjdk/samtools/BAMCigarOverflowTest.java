package htsjdk.samtools;

import htsjdk.samtools.util.CloserUtil;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

import java.io.File;

/**
 * Test the fix of a bug reported by s-andrews in which the use of an arithmetic rather than a logical right shift in BinaryCigarCodec.binaryCigarToCigarElement()
 * causes an overflow in the CIGAR when reading a BAM file for a read that spans a very large intron.
 */
public class BAMCigarOverflowTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");

    @Test
    public void testCigarOverflow() throws Exception {
        final SamReader reader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.LENIENT).open(new File(TEST_DATA_DIR, "BAMCigarOverflowTest/CigarOverflowTest.bam"));

        //Load the single read from the BAM file.
        final SAMRecord testBAMRecord = reader.iterator().next();
        CloserUtil.close(reader);

        //The BAM file that exposed the bug triggered a SAM validation error because the bin field of the BAM record did not equal the computed value. Here we test for this error.
        //Cast to int to avoid an ambiguity in the assertEquals() call between assertEquals(int,int) and assertEquals(Object,Object).
        assertEquals(testBAMRecord.computeIndexingBin(), (int) testBAMRecord.getIndexingBin());
    }

}
