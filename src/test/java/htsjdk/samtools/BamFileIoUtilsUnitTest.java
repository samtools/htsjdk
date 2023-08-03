package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.beta.exception.HtsjdkException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

public class BamFileIoUtilsUnitTest extends HtsjdkTest {
    final static String TEST_DATA_DIR = "src/test/resources/htsjdk/samtools/cram/";
    final static String NA12878_8000 = TEST_DATA_DIR + "CEUTrio.HiSeq.WGS.b37.NA12878.20.first.8000.bam";
    final static String NA12878_20_21 = TEST_DATA_DIR + "NA12878.20.21.unmapped.orig.bam";
    final static int DEFAULT_NUM_RECORDS_TO_COMPARE = 10;

    @Test
    public void testReheaderBamFile(){
        final File originalBam = new File(this.NA12878_8000);
        SAMFileHeader header = SamReaderFactory.make().getFileHeader(new File(NA12878_20_21));
        try {
            final File output = File.createTempFile("output", ".bam");
            BamFileIoUtils.reheaderBamFile(header, originalBam.toPath(), output.toPath());

            // Confirm that the header has been replaced
            final SamReader outputReader = SamReaderFactory.make().open(output.toPath());
            Assert.assertEquals(outputReader.getFileHeader(), header);

            // Confirm that the reads are the same as the original
            Assert.assertTrue(compareBamReads(originalBam, output, DEFAULT_NUM_RECORDS_TO_COMPARE));
        } catch (IOException e){
            throw new HtsjdkException("Could not create a temporary output file.", e);
        }
    }

    /**
     * Compare first (numRecordsToCompare) reads of two bam files.
     * In particular we do not check for equality of the headers.
     *
     * @param numRecordsToCompare the number of reads to compare
     *
     * @return true if the first (numRecordsToCompare) reads are equal, else false
     */
    private boolean compareBamReads(final File bam1, final File bam2, final int numRecordsToCompare){
        final Iterator<SAMRecord> originalBamIterator = SamReaderFactory.make().open(bam1.toPath()).iterator();
        final Iterator<SAMRecord> outputBamIterator = SamReaderFactory.make().open(bam2.toPath()).iterator();
        for (int i = 0; i < numRecordsToCompare; i++){
            final SAMRecord originalRead = originalBamIterator.next();
            final SAMRecord outputRead = outputBamIterator.next();
            if (! originalRead.equals(outputRead)){
                return false;
            }
        }
        return true;
    }

    @Test
    public void testBlockCopyBamFile() throws IOException {
        final File output = File.createTempFile("output", ".bam");
        final OutputStream out = Files.newOutputStream(output.toPath());
        final Path input = Paths.get(NA12878_8000);
        final InputStream in = Files.newInputStream(input);

        BamFileIoUtils.blockCopyBamFile(Paths.get(NA12878_8000), out, false, false);

        final SamReader inputReader = SamReaderFactory.make().open(input);
        final SamReader outputReader = SamReaderFactory.make().open(output);
        Assert.assertEquals(outputReader.getFileHeader(), inputReader.getFileHeader());
        Assert.assertTrue(compareBamReads(input.toFile(), output, DEFAULT_NUM_RECORDS_TO_COMPARE));
    }
}