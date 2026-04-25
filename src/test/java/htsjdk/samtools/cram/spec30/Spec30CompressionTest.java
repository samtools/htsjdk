package htsjdk.samtools.cram.spec30;

import htsjdk.samtools.SAMRecord;
import java.io.IOException;
import java.util.List;
import org.testng.annotations.Test;

/** Tests built-in compression codec support from the hts-specs 3.0 test suite. */
public class Spec30CompressionTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testRawUncompressed() throws IOException {
        assertCramMatchesSam("0900_comp_raw");
    }

    @Test
    public void testGzip() throws IOException {
        assertCramMatchesSam("0901_comp_gz");
    }

    @Test
    public void testBzip2() throws IOException {
        assertCramMatchesSam("0902_comp_bz2");
    }

    @Test
    public void testLzma() throws IOException {
        assertCramMatchesSam("0903_comp_lzma");
    }

    @Test
    public void testRansOrder0() throws IOException {
        assertCramMatchesSam("0904_comp_rans0");
    }

    @Test
    public void testRansOrder1() throws IOException {
        assertCramMatchesSam("0905_comp_rans1");
    }

    @Test
    public void testAllCompressionTypesProduceSameOutput() throws IOException {
        final List<SAMRecord> raw = decodeCram("0900_comp_raw");
        assertRecordsMatch(decodeCram("0901_comp_gz"), raw, "gzip vs raw");
        assertRecordsMatch(decodeCram("0902_comp_bz2"), raw, "bzip2 vs raw");
        assertRecordsMatch(decodeCram("0903_comp_lzma"), raw, "lzma vs raw");
        assertRecordsMatch(decodeCram("0904_comp_rans0"), raw, "rans0 vs raw");
        assertRecordsMatch(decodeCram("0905_comp_rans1"), raw, "rans1 vs raw");
    }
}
