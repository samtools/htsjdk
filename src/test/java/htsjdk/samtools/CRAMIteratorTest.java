package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekableStream;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.io.File;


/**
 * @author Anton_Mazur@epam.com, EPAM Systems, Inc.
 *
 * This test serve for verifying CRAMIterator records validation using strict validation strategy
 **/

public class CRAMIteratorTest extends HtsjdkTest {
    private static final File refFile = new File("src/test/resources/htsjdk/samtools/cram/ce.fa");
    private static final File cramFile = new File("src/test/resources/htsjdk/samtools/cram/ce#supp.3.0.cram");
    ReferenceSource source = new ReferenceSource(refFile);

    @Test
    public void shouldNotThrowsExceptionIfCRAMfileContainsInvalidRecords() {
        SAMRecordIterator cramIter =
                getCramFileIterator(cramFile, source, ValidationStringency.STRICT);

        Assert.assertTrue(cramIter.hasNext());
    }

    @Test(expectedExceptions = SAMException.class)
    public void shouldThrowExceptionIfCRAMFileContainsInvalidRecods() {
        SAMRecordIterator cramIter =
                getCramFileIterator(cramFile, source, ValidationStringency.STRICT);

        while (cramIter.hasNext())
        {
            cramIter.next();
        }
    }

    private SAMRecordIterator getCramFileIterator(File cramFile,
                                                  ReferenceSource source,
                                                  ValidationStringency valStrigency) {

        CRAMFileReader cramFileReader = new CRAMFileReader(cramFile, (SeekableStream) null, source);
        cramFileReader.setValidationStringency(valStrigency);
        return cramFileReader.getIterator();
    }
}