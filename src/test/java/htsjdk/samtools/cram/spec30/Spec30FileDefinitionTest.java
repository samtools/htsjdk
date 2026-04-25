package htsjdk.samtools.cram.spec30;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

/**
 * Tests CRAM file definition (magic number, EOF block) from the hts-specs 3.0 test suite.
 */
public class Spec30FileDefinitionTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testEmptyFileWithEOF() throws IOException {
        assertCramMatchesSam("0001_empty_eof");
    }

    @Test
    public void testEmptyFileWithoutEOF() throws IOException {
        // The spec says EOF is mandatory, but implementations may warn rather than error.
        // Verify htsjdk doesn't crash on a missing EOF block.
        try {
            decodeCramFile(SPEC_30_FAILED_DIR.resolve("0000_empty_noeof.cram").toFile());
            // If we get here without exception, that's acceptable (warning-only behavior)
        } catch (final Exception e) {
            // An exception is also acceptable -- just verify it's not an NPE or AIOOB
            Assert.assertFalse(e instanceof NullPointerException,
                    "Missing EOF should not cause NullPointerException");
            Assert.assertFalse(e instanceof ArrayIndexOutOfBoundsException,
                    "Missing EOF should not cause ArrayIndexOutOfBoundsException");
        }
    }
}
