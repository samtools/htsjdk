package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekableStream;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.io.File;


/**
 * This test serve for verifying CRAMIterator records validation using strict validation strategy
 *
 * @author Anton_Mazur@epam.com, EPAM Systems, Inc.
 **/

public class CRAMIteratorTest extends HtsjdkTest {

    @Test(description = "This test checks that records validation is deferred until they are retrieved")
    public void notThrowOnOpeningContainerWithInvalidRecords() {
        final File refFile = new File("src/test/resources/htsjdk/samtools/cram/ce.fa");
        final File cramFileWithInvalidRecs = new File("src/test/resources/htsjdk/samtools/cram/ce#containsInvalidRecords.3.0.cram");
        final ReferenceSource source = new ReferenceSource(refFile);
        final SAMRecordIterator cramIteratorOverInvalidRecords =
                getCramFileIterator(cramFileWithInvalidRecs, source, ValidationStringency.STRICT);

        Assert.assertTrue(cramIteratorOverInvalidRecords.hasNext());
        cramIteratorOverInvalidRecords.close();
    }

    @Test(expectedExceptions = SAMException.class)
    public void throwOnRecordValidationFailure() {
        final File refFile = new File("src/test/resources/htsjdk/samtools/cram/ce.fa");
        final File cramFileWithInvalidRecs = new File("src/test/resources/htsjdk/samtools/cram/ce#containsInvalidRecords.3.0.cram");
        final ReferenceSource source = new ReferenceSource(refFile);
        final SAMRecordIterator cramIteratorOverInvalidRecords =
                getCramFileIterator(cramFileWithInvalidRecs, source, ValidationStringency.STRICT);
        try{
            while (cramIteratorOverInvalidRecords.hasNext()) {
                cramIteratorOverInvalidRecords.next();
            }
        } finally {
            cramIteratorOverInvalidRecords.close();
        }
    }

    private SAMRecordIterator getCramFileIterator(File cramFile,
                                                  ReferenceSource source,
                                                  ValidationStringency valStringency) {

        final CRAMFileReader cramFileReader = new CRAMFileReader(cramFile, (SeekableStream) null, source);
        cramFileReader.setValidationStringency(valStringency);
        return cramFileReader.getIterator();
    }
}
