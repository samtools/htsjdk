package htsjdk.samtools.cram.spec30;

import java.io.IOException;
import org.testng.annotations.Test;

/** Tests data layout encoding (CORE block, HUFFMAN, BETA) from the hts-specs 3.0 test suite. */
public class Spec30DataLayoutTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testAllInCoreBlockHuffman() throws IOException {
        assertCramMatchesSam("1100_HUFFMAN");
    }

    @Test
    public void testAllInCoreBlockBeta() throws IOException {
        assertCramMatchesSam("1101_BETA");
    }
}
