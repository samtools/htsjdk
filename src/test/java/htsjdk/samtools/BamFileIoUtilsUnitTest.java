package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Paths;

import static org.testng.Assert.*;

public class BamFileIoUtilsUnitTest extends HtsjdkTest {
    private String inputBam = "gs://broad-dsde-methods-takuto/gatk/test/K-562_test.bam";
    private String bamWithNewHeader = "gs://broad-dsde-methods-takuto/gatk/test/K-562_test_new_header.bam";

    // Or, should we just write this in Picard?
    // But this functionality is in htsjdk, so definitely should be tested here
    // Does Paths.get() take a gs:// file and figure it out though...

    @Test
    public void testReheaderBamFile(){
        SAMFileHeader header = null; // tsato: to implement
        BamFileIoUtils.reheaderBamFile(header, Paths.get(inputBam), Paths.get(bamWithNewHeader)); // tsato: is this going to cut it?
        // Creating a temp file....needed in htsjdk too...
    }
}