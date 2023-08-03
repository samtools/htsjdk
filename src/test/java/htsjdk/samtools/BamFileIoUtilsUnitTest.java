package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.beta.exception.HtsjdkException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public class BamFileIoUtilsUnitTest extends HtsjdkTest {
    final static String TEST_DATA_DIR = "src/test/resources/htsjdk/samtools/cram/";
    final String originalBam = TEST_DATA_DIR + "CEUTrio.HiSeq.WGS.b37.NA12878.20.first.8000.bam";
    final String bamWithDesiredHeader = TEST_DATA_DIR + "NA12878.20.21.unmapped.orig.bam";

    @Test
    public void testReheaderBamFile(){
        final File originalBam = new File(this.originalBam);
        SAMFileHeader header = SamReaderFactory.make().getFileHeader(new File(bamWithDesiredHeader));
        try {
            final File output = File.createTempFile("output", ".bam");
            BamFileIoUtils.reheaderBamFile(header, originalBam.toPath(), output.toPath());

            // Confirm that the header has been replaced
            final SamReader outputReader = SamReaderFactory.make().open(output.toPath());
            Assert.assertEquals(outputReader.getFileHeader(), header);

            // Confirm that the reads are the same as the original
            final Iterator<SAMRecord> originalBamIterator = SamReaderFactory.make().open(originalBam.toPath()).iterator();
            final Iterator<SAMRecord> outputBamIterator = outputReader.iterator();
            final int numRecordsToRead = 10;
            for (int i = 0; i < numRecordsToRead; i++){
                final SAMRecord originalRead = originalBamIterator.next();
                final SAMRecord outputRead = outputBamIterator.next();
                Assert.assertEquals(originalRead, outputRead);
            }

        } catch (IOException e){
            throw new HtsjdkException("Could not create a temporary output file.", e);
        }

    }
}