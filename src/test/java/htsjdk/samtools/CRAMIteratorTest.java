package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekableStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;


/**
 * This test serves for verifying CRAMIterator records validation using strict validation strategy
 *
 * @author Anton_Mazur@epam.com, EPAM Systems, Inc.
 **/

public class CRAMIteratorTest extends HtsjdkTest {

    @Test(description = "This test checks that records validation is deferred until they are retrieved")
    public void noValidationFailureOnContainerOpen() {
        try (SAMRecordIterator cramIteratorOverInvalidRecords = getCramFileIterator(ValidationStringency.STRICT)) {
            Assert.assertTrue(cramIteratorOverInvalidRecords.hasNext());
        }
    }

    @Test(expectedExceptions = SAMException.class)
    public void throwOnRecordValidationFailure() {
        try (SAMRecordIterator cramIteratorOverInvalidRecords = getCramFileIterator(ValidationStringency.STRICT)) {
            while (cramIteratorOverInvalidRecords.hasNext()) {
                cramIteratorOverInvalidRecords.next();
            }
        }
    }

    private SAMRecordIterator getCramFileIterator(ValidationStringency valStringency) {
        final File refFile = new File("src/test/resources/htsjdk/samtools/cram/ce.fa");
        final File cramFile = new File("src/test/resources/htsjdk/samtools/cram/ce#containsInvalidRecords.3.0.cram");
        final ReferenceSource source = new ReferenceSource(refFile);

        final CRAMFileReader cramFileReader = new CRAMFileReader(cramFile, (SeekableStream) null, source);
        cramFileReader.setValidationStringency(valStringency);
        return cramFileReader.getIterator();
    }
}
