package htsjdk.samtools.cram.spec30;

import org.testng.annotations.Test;

import java.io.IOException;

/** Tests auxiliary tag encoding from the hts-specs 3.0 test suite. */
public class Spec30TagTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testSingleIntegerTag() throws IOException {
        assertCramMatchesSam("0700_tag");
    }

    @Test
    public void testBlankTagLines() throws IOException {
        assertCramMatchesSam("0701_tag");
    }

    @Test
    public void testCommonTagTypes() throws IOException {
        assertCramMatchesSam("0702_tag");
    }

    @Test
    public void testDifferentIntegerSizes() throws IOException {
        assertCramMatchesSam("0703_tag");
    }

    @Test
    public void testATags() throws IOException {
        assertCramMatchesSam("0704_tag");
    }

    @Test
    public void testHTags() throws IOException {
        assertCramMatchesSam("0705_tag");
    }

    @Test
    public void testBTags() throws IOException {
        assertCramMatchesSam("0706_tag");
    }

    @Test
    public void testExplicitMdNmMatchingComputed() throws IOException {
        assertCramMatchesSam("0707_tag");
    }

    @Test
    public void testExplicitMdNmInvalid() throws IOException {
        assertCramMatchesSam("0708_tag");
    }

    @Test
    public void testExplicitRG() throws IOException {
        assertCramMatchesSam("0709_tag");
    }

    @Test
    public void testImplicitRG() throws IOException {
        assertCramMatchesSam("0710_tag");
    }
}
