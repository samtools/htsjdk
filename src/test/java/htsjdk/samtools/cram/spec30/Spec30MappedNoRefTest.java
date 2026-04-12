package htsjdk.samtools.cram.spec30;

import org.testng.annotations.Test;

import java.io.IOException;

/** Tests mapped reads without reference from the hts-specs 3.0 test suite. */
public class Spec30MappedNoRefTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testSingleMappedRead() throws IOException {
        assertCramMatchesSam("0400_mapped");
    }

    @Test
    public void testPairedDetachedStarMate() throws IOException {
        assertCramMatchesSam("0401_mapped");
    }

    @Test
    public void testPairedDetachedWithMateInfo() throws IOException {
        assertCramMatchesSam("0402_mapped");
    }

    @Test
    public void testPairedMateDownstream() throws IOException {
        assertCramMatchesSam("0403_mapped");
    }
}
