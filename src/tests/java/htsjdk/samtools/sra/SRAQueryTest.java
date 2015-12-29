package htsjdk.samtools.sra;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.NoSuchElementException;

public class SRAQueryTest {

    @DataProvider(name = "testUnmappedCounts")
    public Object[][] createDataForUnmappedCounts() {
        return new Object[][] {
                {"SRR2096940", 498}
        };
    }

    @Test(dataProvider = "testUnmappedCounts")
    public void testUnmappedCounts(String acc, int numberUnalignments) {
        if (!SRAAccession.isSupported()) return;

        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(
                SamInputResource.of(new SRAAccession(acc))
        );

        final SAMRecordIterator samRecordIterator = reader.queryUnmapped();

        checkAlignedUnalignedCountsByIterator(samRecordIterator, 0, numberUnalignments);
    }

    @DataProvider(name = "testReferenceAlignedCounts")
    public Object[][] createDataForReferenceAlignedCounts() {
        return new Object[][] {
                {"SRR2096940", "CM000681.1", 0, 10591},
                {"SRR2096940", "CM000681.1", 55627015, 10591},
                {"SRR2096940", "CM000681.1", 55627016, 0},
        };
    }

    @Test(dataProvider = "testReferenceAlignedCounts")
    public void testReferenceAlignedCounts(String acc, String reference, int refernceStart, int numberAlignments) {
        if (!SRAAccession.isSupported()) return;

        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(
                SamInputResource.of(new SRAAccession(acc))
        );

        final SAMRecordIterator samRecordIterator = reader.queryAlignmentStart(reference, refernceStart);

        checkAlignedUnalignedCountsByIterator(samRecordIterator, numberAlignments, 0);
    }

    @DataProvider(name = "testQueryCounts")
    public Object[][] createDataForQueryCounts() {
        return new Object[][] {
                {"SRR2096940", "CM000681.1", 0, 59128983, true, 10591, 0},
                {"SRR2096940", "CM000681.1", 55627015, 59128983, true, 10591, 0},
                {"SRR2096940", "CM000681.1", 55627016, 59128983, true, 0, 0},
                {"SRR2096940", "CM000681.1", 55627016, 59128983, false, 10591, -1},
        };
    }

    @Test(dataProvider = "testQueryCounts")
    public void testQueryCounts(String acc, String reference, int refernceStart, int referenceEnd, boolean contained, int numberAlignments, int numberUnalignment) {
        if (!SRAAccession.isSupported()) return;

        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(
                SamInputResource.of(new SRAAccession(acc))
        );

        final SAMRecordIterator samRecordIterator = reader.query(reference, refernceStart, referenceEnd, contained);

        checkAlignedUnalignedCountsByIterator(samRecordIterator, numberAlignments, numberUnalignment);
    }

    private void checkAlignedUnalignedCountsByIterator(SAMRecordIterator samRecordIterator,
                                                       int numberAlignments, int numberUnalignments) {
        int countAlignments = 0, countUnalignments = 0;
        while (true) {
            boolean hasRecord = samRecordIterator.hasNext();
            SAMRecord record = null;
            try {
                record = samRecordIterator.next();
                Assert.assertTrue(hasRecord); // exception is not thrown if we came to this point
            } catch (NoSuchElementException e) {
                Assert.assertFalse(hasRecord);
            }

            Assert.assertEquals(hasRecord, record != null);

            if (record == null) {
                break;
            }

            if (record.getReadUnmappedFlag()) {
                countUnalignments++;
            } else {
                countAlignments++;
            }
        }

        if (numberAlignments != -1) {
            Assert.assertEquals(numberAlignments, countAlignments);
        }
        if (numberUnalignments != -1) {
            Assert.assertEquals(numberUnalignments, countUnalignments);
        }
    }

}
