package htsjdk.samtools.cram.spec30;

import org.testng.annotations.Test;

import java.io.IOException;

/** Tests corner cases from the hts-specs 3.0 test suite. */
public class Spec30CornerCaseTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testReadsBeyondReferenceEnd() throws IOException {
        assertCramMatchesSam("1200_overflow");
    }
}
