package htsjdk.beta.plugin.registry;

import htsjdk.HtsjdkTest;
import htsjdk.beta.codecs.hapref.fasta.FASTACodecV1_0;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.beta.plugin.hapref.HaploidReferenceFormat;
import htsjdk.samtools.reference.ReferenceSequence;
import org.testng.Assert;
import org.testng.annotations.Test;

public class HtsCodecRegistryTest extends HtsjdkTest {

    final IOPath TEST_DIR = new HtsPath("src/test/resources/htsjdk/samtools/");

    @Test
    public void testHapRefDecoder() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "/hg19mini.fasta");

        try (final HaploidReferenceDecoder hapRefDecoder = HtsHapRefCodecs.getHapRefDecoder(inputPath)) {
            Assert.assertNotNull(hapRefDecoder);
            Assert.assertEquals(hapRefDecoder.getFormat(), HaploidReferenceFormat.FASTA);
            Assert.assertEquals(hapRefDecoder.getVersion(), FASTACodecV1_0.VERSION_1);

            for (final ReferenceSequence referenceSequence : hapRefDecoder) {
                Assert.assertNotNull(referenceSequence);
            }
        }
    }

}
