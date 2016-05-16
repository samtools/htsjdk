package htsjdk.samtools.sra;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.NoSuchElementException;

@Test(groups = "sra")
public abstract class AbstractSRATest {

    @BeforeMethod
    public final void assertSRAIsSupported(){
        if(!SRAAccession.isSupported()){
            throw new SkipException("Skipping SRA Test because SRA native code is unavailable.");
        }
    }

    /**
     * Exhaust the iterator and check that it produce the expected number of mapped and unmapped reads.
     * Also checks that the hasNext() agrees with the actual results of next() for the given iterator.
     * @param expectedNumMapped expected number of mapped reads, specify -1 to skip this check
     * @param expectedNumUnmapped expected number of unmapped reads, specify -1 to skip this check
     */
    static void assertCorrectCountsOfMappedAndUnmappedRecords(SAMRecordIterator samRecordIterator,
                                                                        int expectedNumMapped, int expectedNumUnmapped) {
        int numMapped = 0, numUnmapped = 0;
        while (true) {
            boolean hasRecord = samRecordIterator.hasNext();
            SAMRecord record;
            try {
                record = samRecordIterator.next();
                Assert.assertNotNull(record);
                Assert.assertTrue(hasRecord); // exception is not thrown if we came to this point
            } catch (final NoSuchElementException e) {
                Assert.assertFalse(hasRecord);
                break;
            }

            if (record.getReadUnmappedFlag()) {
                numUnmapped++;
            } else {
                numMapped++;
            }
        }

        if (expectedNumMapped != -1) {
            Assert.assertEquals(numMapped, expectedNumMapped);
        }
        if (expectedNumUnmapped != -1) {
            Assert.assertEquals(numUnmapped, expectedNumUnmapped);
        }
    }
}
