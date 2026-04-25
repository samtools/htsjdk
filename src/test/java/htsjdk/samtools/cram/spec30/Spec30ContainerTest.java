package htsjdk.samtools.cram.spec30;

import org.testng.annotations.Test;

import java.io.IOException;

/** Tests container structure and compression header from the hts-specs 3.0 test suite. */
public class Spec30ContainerTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testCompressionHeader() throws IOException {
        assertCramMatchesSam("0200_cmpr_hdr");
    }
}
