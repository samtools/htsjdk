package htsjdk.samtools.sra;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.NoSuchElementException;

@Test(groups = "sra")
public abstract class AbstractSRATest extends HtsjdkTest {
    private static boolean canResolveNetworkAccession = false;
    private static String checkAccession = "SRR000123";

    @BeforeGroups(groups = "sra")
    public final void checkIfCanResolve() {
        if (SRAAccession.checkIfInitialized() != null) {
            return;
        }
        canResolveNetworkAccession = SRAAccession.isValid(checkAccession);
    }

    @BeforeMethod
    public final void assertSRAIsSupported() {
        if(SRAAccession.checkIfInitialized() != null){
            throw new SkipException("Skipping SRA Test because SRA native code is unavailable.");
        }
    }

    @BeforeMethod
    public final void skipIfCantResolve(Method method, Object[] params) {
        String accession = null;

        if (params.length > 0) {
            Object firstParam = params[0];
            if (firstParam instanceof String) {
                accession = (String)firstParam;
            } else if (firstParam instanceof SRAAccession) {
                accession = firstParam.toString();
            }
        }

        if (accession != null &&
                accession.matches(SRAAccession.REMOTE_ACCESSION_PATTERN) && !canResolveNetworkAccession) {
            throw new SkipException("Skipping network SRA Test because cannot resolve remote SRA accession '" +
                    checkAccession + "'.");
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
