package htsjdk.samtools.cram.spec30;

import org.testng.annotations.Test;

import java.io.IOException;

/** Tests unmapped read encoding from the hts-specs 3.0 test suite. */
public class Spec30UnmappedTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testSingleUnmappedRead() throws IOException {
        assertCramMatchesSam("0300_unmapped");
    }

    @Test
    public void testTwoUnpairedDifferentLength() throws IOException {
        assertCramMatchesSam("0301_unmapped");
    }

    @Test
    public void testThreeReadsIncludingPair() throws IOException {
        assertCramMatchesSam("0302_unmapped");
    }

    @Test
    public void testThreeReadsWithReducedFlags() throws IOException {
        assertCramMatchesSam("0303_unmapped");
    }
}
