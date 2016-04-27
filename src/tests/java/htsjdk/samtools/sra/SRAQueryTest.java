package htsjdk.samtools.sra;

import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SRAQueryTest extends AbstractSRATest {

    @DataProvider(name = "testUnmappedCounts")
    private Object[][] createDataForUnmappedCounts() {
        return new Object[][] {
                {"SRR2096940", 498}
        };
    }

    @Test(dataProvider = "testUnmappedCounts")
    public void testUnmappedCounts(String acc, int expectedNumUnmapped) {
        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(
                SamInputResource.of(new SRAAccession(acc))
        );

        final SAMRecordIterator samRecordIterator = reader.queryUnmapped();

        assertCorrectCountsOfMappedAndUnmappedRecords(samRecordIterator, 0, expectedNumUnmapped);
    }

    @DataProvider(name = "testReferenceAlignedCounts")
    private Object[][] createDataForReferenceAlignedCounts() {
        return new Object[][] {
                {"SRR2096940", "CM000681.1", 0, 10591},
                {"SRR2096940", "CM000681.1", 55627015, 10591},
                {"SRR2096940", "CM000681.1", 55627016, 0},
        };
    }

    @Test(dataProvider = "testReferenceAlignedCounts")
    public void testReferenceAlignedCounts(String acc, String reference, int referenceStart, int expectedNumMapped) {
        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(
                SamInputResource.of(new SRAAccession(acc))
        );

        final SAMRecordIterator samRecordIterator = reader.queryAlignmentStart(reference, referenceStart);

        assertCorrectCountsOfMappedAndUnmappedRecords(samRecordIterator, expectedNumMapped, 0);
    }

    @DataProvider(name = "testQueryCounts")
    private Object[][] createDataForQueryCounts() {
        return new Object[][] {
                {"SRR2096940", "CM000681.1", 0, 59128983, true, 10591, 0},
                {"SRR2096940", "CM000681.1", 55627015, 59128983, true, 10591, 0},
                {"SRR2096940", "CM000681.1", 55627016, 59128983, true, 0, 0},
                {"SRR2096940", "CM000681.1", 55627016, 59128983, false, 10591, -1},
        };
    }

    @Test(dataProvider = "testQueryCounts")
    public void testQueryCounts(String acc, String reference, int referenceStart, int referenceEnd, boolean contained, int expectedNumMapped, int expectedNumUnmapped) {
        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(
                SamInputResource.of(new SRAAccession(acc))
        );

        final SAMRecordIterator samRecordIterator = reader.query(reference, referenceStart, referenceEnd, contained);

        assertCorrectCountsOfMappedAndUnmappedRecords(samRecordIterator, expectedNumMapped, expectedNumUnmapped);
    }


}
