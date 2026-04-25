package htsjdk.samtools.cram.spec30;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.CloseableIterator;
import java.io.File;
import java.io.IOException;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests CRAI index query correctness using the hts-specs 3.0 index test files (1400-1406).
 * Each test verifies specific query results documented in the hts-specs README.
 */
public class Spec30IndexTest extends HtsSpecsComplianceTestBase {

    // ---- 1400: Simple mapped case, 10bp reads, 77 reads per container ----

    @Test
    public void test1400_simpleQuery() throws IOException {
        // CHROMOSOME_I:333-444 should return 121 records (s324-333 to s444-453)
        assertQueryCount("1400_index_simple", "CHROMOSOME_I", 333, 444, false, 121);
    }

    // ---- 1401: All unmapped ----

    @Test
    public void test1401_unmappedQuery() throws IOException {
        // Query for unmapped should return all 1000 records
        assertUnmappedCount("1401_index_unmapped", 1000);
    }

    // ---- 1402: 3 references + unmapped, one ref per slice ----

    @Test
    public void test1402_chrI_100_200() throws IOException {
        assertQueryCount("1402_index_3ref", "CHROMOSOME_I", 100, 200, false, 110);
    }

    @Test
    public void test1402_chrII_5_5() throws IOException {
        assertQueryCount("1402_index_3ref", "CHROMOSOME_II", 5, 5, false, 5);
    }

    @Test
    public void test1402_chrII_10_10() throws IOException {
        assertQueryCount("1402_index_3ref", "CHROMOSOME_II", 10, 10, false, 10);
    }

    @Test
    public void test1402_chrII_15_15() throws IOException {
        assertQueryCount("1402_index_3ref", "CHROMOSOME_II", 15, 15, false, 5);
    }

    @Test
    public void test1402_chrIII_15_15() throws IOException {
        assertQueryCount("1402_index_3ref", "CHROMOSOME_III", 15, 15, false, 10);
    }

    @Test
    public void test1402_unmapped() throws IOException {
        assertUnmappedCount("1402_index_3ref", 300);
    }

    // ---- 1403: Multi-ref mode (same queries as 1402) ----

    @Test
    public void test1403_chrI_100_200() throws IOException {
        assertQueryCount("1403_index_multiref", "CHROMOSOME_I", 100, 200, false, 110);
    }

    @Test
    public void test1403_chrII_5_5() throws IOException {
        assertQueryCount("1403_index_multiref", "CHROMOSOME_II", 5, 5, false, 5);
    }

    @Test
    public void test1403_chrII_10_10() throws IOException {
        assertQueryCount("1403_index_multiref", "CHROMOSOME_II", 10, 10, false, 10);
    }

    @Test
    public void test1403_chrII_15_15() throws IOException {
        assertQueryCount("1403_index_multiref", "CHROMOSOME_II", 15, 15, false, 5);
    }

    @Test
    public void test1403_chrIII_15_15() throws IOException {
        assertQueryCount("1403_index_multiref", "CHROMOSOME_III", 15, 15, false, 10);
    }

    @Test
    public void test1403_unmapped() throws IOException {
        assertUnmappedCount("1403_index_multiref", 300);
    }

    // ---- 1404: Multi-slice containers (same queries as 1402) ----

    @Test
    public void test1404_chrI_100_200() throws IOException {
        assertQueryCount("1404_index_multislice", "CHROMOSOME_I", 100, 200, false, 110);
    }

    @Test
    public void test1404_chrII_10_10() throws IOException {
        assertQueryCount("1404_index_multislice", "CHROMOSOME_II", 10, 10, false, 10);
    }

    @Test
    public void test1404_unmapped() throws IOException {
        assertUnmappedCount("1404_index_multislice", 300);
    }

    // ---- 1405: Multi-slice + multi-ref (same queries as 1402) ----

    @Test
    public void test1405_chrI_100_200() throws IOException {
        assertQueryCount("1405_index_multisliceref", "CHROMOSOME_I", 100, 200, false, 110);
    }

    @Test
    public void test1405_chrII_10_10() throws IOException {
        assertQueryCount("1405_index_multisliceref", "CHROMOSOME_II", 10, 10, false, 10);
    }

    @Test
    public void test1405_unmapped() throws IOException {
        assertUnmappedCount("1405_index_multisliceref", 300);
    }

    // ---- 1406: Mix of long and short reads ----

    @Test
    public void test1406_query_500_550() throws IOException {
        assertQueryCount("1406_index_long", "CHROMOSOME_I", 500, 550, false, 61);
    }

    @Test
    public void test1406_query_500_650() throws IOException {
        assertQueryCount("1406_index_long", "CHROMOSOME_I", 500, 650, false, 162);
    }

    @Test
    public void test1406_query_610_910() throws IOException {
        assertQueryCount("1406_index_long", "CHROMOSOME_I", 610, 910, false, 313);
    }

    // ---- Helpers ----

    private void assertQueryCount(
            final String basename,
            final String contig,
            final int start,
            final int end,
            final boolean contained,
            final int expectedCount)
            throws IOException {
        final File cramFile = SPEC_30_DIR.resolve(basename + ".cram").toFile();
        final File craiFile = SPEC_30_DIR.resolve(basename + ".cram.crai").toFile();
        final ReferenceSource source = new ReferenceSource(REFERENCE);

        try (final CRAMFileReader reader = new CRAMFileReader(
                new SeekableFileStream(cramFile),
                new SeekableFileStream(craiFile),
                source,
                ValidationStringency.SILENT)) {

            final int seqIdx = reader.getFileHeader().getSequenceIndex(contig);
            final QueryInterval[] intervals = new QueryInterval[] {new QueryInterval(seqIdx, start, end)};
            try (final CloseableIterator<SAMRecord> iterator = reader.query(intervals, contained)) {
                final int count = countRecords(iterator);
                Assert.assertEquals(count, expectedCount, basename + " query " + contig + ":" + start + "-" + end);
            }
        }
    }

    private void assertUnmappedCount(final String basename, final int expectedCount) throws IOException {
        final File cramFile = SPEC_30_DIR.resolve(basename + ".cram").toFile();
        final File craiFile = SPEC_30_DIR.resolve(basename + ".cram.crai").toFile();
        final ReferenceSource source = new ReferenceSource(REFERENCE);

        try (final CRAMFileReader reader = new CRAMFileReader(
                new SeekableFileStream(cramFile),
                new SeekableFileStream(craiFile),
                source,
                ValidationStringency.SILENT)) {
            try (final CloseableIterator<SAMRecord> iterator = reader.queryUnmapped()) {
                final int count = countRecords(iterator);
                Assert.assertEquals(count, expectedCount, basename + " unmapped query");
            }
        }
    }

    private static int countRecords(final CloseableIterator<SAMRecord> iterator) {
        int count = 0;
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        return count;
    }
}
