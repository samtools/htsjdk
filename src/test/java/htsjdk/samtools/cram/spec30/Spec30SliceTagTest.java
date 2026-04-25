package htsjdk.samtools.cram.spec30;

import org.testng.annotations.Test;

import java.io.IOException;

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
