package htsjdk.samtools.cram.spec30;

import java.io.IOException;
import org.testng.annotations.Test;

/** Tests embedded reference encoding from the hts-specs 3.0 test suite. */
public class Spec30EmbeddedRefTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testEmbeddedReference() throws IOException {
        assertCramMatchesSam("0600_mapped");
    }

    @Test
    public void testEmbeddedReferenceZeroMD5() throws IOException {
        assertCramMatchesSam("0601_mapped");
    }
}
