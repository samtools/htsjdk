package htsjdk.samtools.cram.spec30;

import htsjdk.samtools.SAMRecord;
import java.io.IOException;
import java.util.List;
import org.testng.annotations.Test;

/** Tests multi-container/slice/ref layouts from the hts-specs 3.0 test suite. */
public class Spec30MultiContainerTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testMultipleContainers() throws IOException {
        assertCramMatchesSam("0800_ctr");
    }

    @Test
    public void testMultipleRefsInContainer() throws IOException {
        assertCramMatchesSam("0801_ctr");
    }

    @Test
    public void testMultipleSlicesPerContainer() throws IOException {
        assertCramMatchesSam("0802_ctr");
    }

    @Test
    public void testAllThreeProduceSameOutput() throws IOException {
        // The README states 0800, 0801, 0802 should all decode to the same content
        final List<SAMRecord> ctr0800 = decodeCram("0800_ctr");
        final List<SAMRecord> ctr0801 = decodeCram("0801_ctr");
        final List<SAMRecord> ctr0802 = decodeCram("0802_ctr");
        assertRecordsMatch(ctr0801, ctr0800, "0801 vs 0800");
        assertRecordsMatch(ctr0802, ctr0800, "0802 vs 0800");
    }
}
