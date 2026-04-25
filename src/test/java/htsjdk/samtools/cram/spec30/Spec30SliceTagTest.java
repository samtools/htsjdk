package htsjdk.samtools.cram.spec30;

import java.io.IOException;
import org.testng.annotations.Test;

/** Tests slice auxiliary tags from the hts-specs 3.0 test suite. */
public class Spec30SliceTagTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testSliceAuxBdSd() throws IOException {
        assertCramMatchesSam("1300_slice_aux");
    }

    @Test
    public void testSliceAuxExtended() throws IOException {
        assertCramMatchesSam("1301_slice_aux");
    }
}
