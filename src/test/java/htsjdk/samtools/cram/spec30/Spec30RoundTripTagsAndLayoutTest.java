package htsjdk.samtools.cram.spec30;

import org.testng.annotations.Test;

import java.io.IOException;

/**
 * SAM → CRAM → SAM round-trip tests for tags (0700s), multi-container (0800s),
 * compression (0900s), data layout (1100s), corner cases (1200), and slice tags (1300s).
 */
public class Spec30RoundTripTagsAndLayoutTest extends HtsSpecsComplianceTestBase {

    // Tags
    @Test public void roundTrip_0700() throws IOException { assertRoundTrip("0700_tag"); }
    @Test public void roundTrip_0701() throws IOException { assertRoundTrip("0701_tag"); }
    @Test public void roundTrip_0702() throws IOException { assertRoundTrip("0702_tag"); }
    @Test public void roundTrip_0703() throws IOException { assertRoundTrip("0703_tag"); }
    @Test public void roundTrip_0704() throws IOException { assertRoundTrip("0704_tag"); }
    @Test public void roundTrip_0705() throws IOException { assertRoundTrip("0705_tag"); }
    @Test public void roundTrip_0706() throws IOException { assertRoundTrip("0706_tag"); }
    @Test public void roundTrip_0707() throws IOException { assertRoundTrip("0707_tag"); }
    @Test public void roundTrip_0708() throws IOException { assertRoundTrip("0708_tag"); }
    @Test public void roundTrip_0709() throws IOException { assertRoundTrip("0709_tag"); }
    @Test public void roundTrip_0710() throws IOException { assertRoundTrip("0710_tag"); }

    // Multi-container/slice/ref
    @Test public void roundTrip_0800() throws IOException { assertRoundTrip("0800_ctr"); }
    @Test public void roundTrip_0801() throws IOException { assertRoundTrip("0801_ctr"); }
    @Test public void roundTrip_0802() throws IOException { assertRoundTrip("0802_ctr"); }

    // Compression (all same content, just different block compression)
    @Test public void roundTrip_0900() throws IOException { assertRoundTrip("0900_comp_raw"); }

    // Data layout
    @Test public void roundTrip_1100() throws IOException { assertRoundTrip("1100_HUFFMAN"); }
    @Test public void roundTrip_1101() throws IOException { assertRoundTrip("1101_BETA"); }

    // Corner cases
    @Test public void roundTrip_1200() throws IOException { assertRoundTrip("1200_overflow"); }

    // Slice tags
    @Test public void roundTrip_1300() throws IOException { assertRoundTrip("1300_slice_aux"); }
    @Test public void roundTrip_1301() throws IOException { assertRoundTrip("1301_slice_aux"); }
}
