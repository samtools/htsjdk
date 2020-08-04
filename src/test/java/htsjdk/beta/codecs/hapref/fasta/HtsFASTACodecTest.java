package htsjdk.beta.codecs.hapref.fasta;

import htsjdk.HtsjdkTest;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.registry.HtsHapRefCodecs;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.samtools.reference.ReferenceSequence;
import org.testng.Assert;
import org.testng.annotations.Test;

public class HtsFASTACodecTest extends HtsjdkTest {
    final IOPath TEST_DIR = new HtsPath("src/test/resources/htsjdk/samtools/");

    @Test
    public void testFASTADecoder() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "/hg19mini.fasta");

        try (final HaploidReferenceDecoder fastaDecoder = HtsHapRefCodecs.getHapRefDecoder(inputPath)) {
            Assert.assertNotNull(fastaDecoder);

            for (final ReferenceSequence referenceSequence : fastaDecoder){
                Assert.assertNotNull(referenceSequence);
            }
        }
    }

}
