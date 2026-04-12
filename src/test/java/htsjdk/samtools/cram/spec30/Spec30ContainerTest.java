package htsjdk.samtools.cram.spec30;

import org.testng.annotations.Test;

import java.io.IOException;

/** Tests container structure and compression header from the hts-specs 3.0 test suite. */
public class Spec30ContainerTest extends HtsSpecsComplianceTestBase {

    // 0200_cmpr_hdr.cram has a container with compression header but no slices (0 records).
    // htsjdk throws IndexOutOfBoundsException when distributing indexing parameters to the empty
    // slice list. This is a known htsjdk limitation -- it doesn't handle slice-less containers.
    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testCompressionHeader() throws IOException {
        assertCramMatchesSam("0200_cmpr_hdr");
    }
}
