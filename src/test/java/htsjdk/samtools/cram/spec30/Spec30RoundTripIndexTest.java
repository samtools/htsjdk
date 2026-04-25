package htsjdk.samtools.cram.spec30;

import java.io.IOException;
import org.testng.annotations.Test;

/**
 * SAM → CRAM → SAM round-trip tests for the index test files (1400-1406).
 * These files have larger record counts so they exercise container/slice boundaries.
 */
public class Spec30RoundTripIndexTest extends HtsSpecsComplianceTestBase {

    @Test
    public void roundTrip_1400() throws IOException {
        assertRoundTrip("1400_index_simple");
    }

    @Test
    public void roundTrip_1401() throws IOException {
        assertRoundTrip("1401_index_unmapped");
    }

    @Test
    public void roundTrip_1402() throws IOException {
        assertRoundTrip("1402_index_3ref");
    }

    @Test
    public void roundTrip_1403() throws IOException {
        assertRoundTrip("1403_index_multiref");
    }

    @Test
    public void roundTrip_1404() throws IOException {
        assertRoundTrip("1404_index_multislice");
    }

    @Test
    public void roundTrip_1405() throws IOException {
        assertRoundTrip("1405_index_multisliceref");
    }

    @Test
    public void roundTrip_1406() throws IOException {
        assertRoundTrip("1406_index_long");
    }
}
