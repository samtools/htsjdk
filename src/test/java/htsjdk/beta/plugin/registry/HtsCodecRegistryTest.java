package htsjdk.beta.plugin.registry;

import htsjdk.HtsjdkTest;
import htsjdk.beta.codecs.hapref.fasta.FASTACodecV1_0;
import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsBundle;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.beta.plugin.registry.testcodec.HtsTestCodec;
import htsjdk.beta.plugin.registry.testcodec.HtsTestCodecFormats;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.beta.plugin.hapref.HaploidReferenceFormats;
import htsjdk.samtools.reference.ReferenceSequence;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class HtsCodecRegistryTest extends HtsjdkTest {
    final IOPath TEST_DIR = new HtsPath("src/test/resources/htsjdk/samtools/");

    @Test
    public void testHapRefDecoder() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "/hg19mini.fasta");

        try (final HaploidReferenceDecoder hapRefDecoder =
                     HtsDefaultRegistry.getHaploidReferenceResolver().getHaploidReferenceDecoder(inputPath)) {
            Assert.assertNotNull(hapRefDecoder);
            Assert.assertEquals(hapRefDecoder.getFileFormat(), HaploidReferenceFormats.FASTA);
            Assert.assertEquals(hapRefDecoder.getVersion(), FASTACodecV1_0.VERSION_1);

            for (final ReferenceSequence referenceSequence : hapRefDecoder) {
                Assert.assertNotNull(referenceSequence);
            }
        }
    }

    @DataProvider(name="customFormatBundles")
    private Object[][] getCustomFormatBundles() {
        return new Object[][]{
                { getCustomIOPathBundle() },
                { HtsCodecResolverTest.makeInputStreamBundleWithContent(
                        HtsContentType.ALIGNED_READS.name(),
                        HtsTestCodecFormats.FILE_FORMAT_1,
                        HtsTestCodecFormats.FILE_FORMAT_1 + HtsCodecResolverTest.V1_0,
                        true) }
        };
    }

    @Test(dataProvider = "customFormatBundles")
    public void testCustomFormatCodec(final Bundle testFormatBundle) {
        // test resolution against a private registry with a custom reads format (use the the HtsTestCodec
        // implementation, which supports various custom reads formats)
        final HtsCodecRegistry privateRegistry = HtsCodecRegistry.createPrivateRegistry();
        privateRegistry.registerCodec(
                new HtsTestCodec(
                        HtsTestCodecFormats.FILE_FORMAT_1,
                        HtsCodecResolverTest.V1_0,
                        HtsCodecResolverTest.FORMAT_1_FILE_EXTENSION,
                        null,
                        true
                ));

        try (final ReadsDecoder customDecoder = privateRegistry.getReadsResolver().getReadsDecoder(testFormatBundle)) {
            Assert.assertNotNull(customDecoder);
            Assert.assertEquals(customDecoder.getFileFormat(), HtsTestCodecFormats.FILE_FORMAT_1);
            Assert.assertEquals(customDecoder.getVersion(), HtsCodecResolverTest.V1_0);
        }
    }

    private Bundle getCustomIOPathBundle() {
        final Bundle bundle = HtsCodecResolverTest.makeInputIOPathBundleWithContent(
                BundleResourceType.ALIGNED_READS,
                HtsTestCodecFormats.FILE_FORMAT_1,
                HtsCodecResolverTest.FORMAT_1_FILE_EXTENSION,
                HtsTestCodecFormats.FILE_FORMAT_1 + HtsCodecResolverTest.V1_0,
                true
        );
        final IOPath customFormatIOPath = bundle.get(BundleResourceType.ALIGNED_READS).get().getIOPath().get();
        final ReadsBundle readsBundle = new ReadsBundle(customFormatIOPath);

        return readsBundle;
    }

}
