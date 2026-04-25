package htsjdk.samtools.cram.spec30;

import org.testng.annotations.Test;

import java.io.IOException;

/** Tests SAM header encoding in CRAM from the hts-specs 3.0 test suite. */
public class Spec30HeaderTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testHeader1_sequenceRecords() throws IOException {
        assertCramMatchesSam("0100_header1");
    }

    @Test
    public void testHeader2_withBlankBlock() throws IOException {
        assertCramMatchesSam("0101_header2");
    }
}
