package htsjdk.samtools.cram.spec30;

import java.io.IOException;
import org.testng.annotations.Test;

/** Tests mapped reads with reference (feature codes) from the hts-specs 3.0 test suite. */
public class Spec30MappedWithRefTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testExternalRefNoCigarOps() throws IOException {
        assertCramMatchesSam("0500_mapped");
    }

    @Test
    public void testSubstitutions_X_BS() throws IOException {
        assertCramMatchesSam("0501_mapped");
    }

    @Test
    public void testAmbiguousBases_B_BA() throws IOException {
        assertCramMatchesSam("0502_mapped");
    }

    @Test
    public void testBaseString_b_BB() throws IOException {
        assertCramMatchesSam("0503_mapped");
    }

    @Test
    public void testSoftHardClips_S_H() throws IOException {
        assertCramMatchesSam("0504_mapped");
    }

    @Test
    public void testIndels_D_I_i() throws IOException {
        assertCramMatchesSam("0505_mapped");
    }

    @Test
    public void testPadding_P() throws IOException {
        assertCramMatchesSam("0506_mapped");
    }

    @Test
    public void testRefSkip_N() throws IOException {
        assertCramMatchesSam("0507_mapped");
    }
}
