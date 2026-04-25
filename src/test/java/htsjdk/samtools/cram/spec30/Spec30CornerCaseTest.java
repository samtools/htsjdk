package htsjdk.samtools.cram.spec30;

import java.io.IOException;
import org.testng.annotations.Test;

/** Tests corner cases from the hts-specs 3.0 test suite. */
public class Spec30CornerCaseTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testReadsBeyondReferenceEnd() throws IOException {
        assertCramMatchesSam("1200_overflow");
    }
}
