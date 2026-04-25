package htsjdk.samtools.cram.spec30;

import java.io.IOException;
import org.testng.annotations.Test;

/**
 * SAM → CRAM → SAM round-trip tests for basic hts-specs 3.0 test files:
 * unmapped (0300s), mapped no-ref (0400s), mapped with-ref (0500s), embedded ref (0600s).
 */
public class Spec30RoundTripBasicTest extends HtsSpecsComplianceTestBase {

    // Unmapped
    @Test
    public void roundTrip_0300() throws IOException {
        assertRoundTrip("0300_unmapped");
    }

    @Test
    public void roundTrip_0301() throws IOException {
        assertRoundTrip("0301_unmapped");
    }

    @Test
    public void roundTrip_0302() throws IOException {
        assertRoundTrip("0302_unmapped");
    }

    @Test
    public void roundTrip_0303() throws IOException {
        assertRoundTrip("0303_unmapped");
    }

    // Mapped, no reference
    @Test
    public void roundTrip_0400() throws IOException {
        assertRoundTrip("0400_mapped");
    }

    @Test
    public void roundTrip_0401() throws IOException {
        assertRoundTrip("0401_mapped");
    }

    @Test
    public void roundTrip_0402() throws IOException {
        assertRoundTrip("0402_mapped");
    }

    @Test
    public void roundTrip_0403() throws IOException {
        assertRoundTrip("0403_mapped");
    }

    // Mapped, with reference (feature codes)
    @Test
    public void roundTrip_0500() throws IOException {
        assertRoundTrip("0500_mapped");
    }

    @Test
    public void roundTrip_0501() throws IOException {
        assertRoundTrip("0501_mapped");
    }

    @Test
    public void roundTrip_0502() throws IOException {
        assertRoundTrip("0502_mapped");
    }

    @Test
    public void roundTrip_0503() throws IOException {
        assertRoundTrip("0503_mapped");
    }

    @Test
    public void roundTrip_0504() throws IOException {
        assertRoundTrip("0504_mapped");
    }

    @Test
    public void roundTrip_0505() throws IOException {
        assertRoundTrip("0505_mapped");
    }

    @Test
    public void roundTrip_0506() throws IOException {
        assertRoundTrip("0506_mapped");
    }

    @Test
    public void roundTrip_0507() throws IOException {
        assertRoundTrip("0507_mapped");
    }

    // Embedded reference
    @Test
    public void roundTrip_0600() throws IOException {
        assertRoundTrip("0600_mapped");
    }

    @Test
    public void roundTrip_0601() throws IOException {
        assertRoundTrip("0601_mapped");
    }
}
