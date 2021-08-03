package htsjdk.beta.codecs.hapref.fasta;

import htsjdk.HtsjdkTest;
import htsjdk.beta.exception.HtsjdkUnsupportedOperationException;
import htsjdk.beta.io.IOPathUtils;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleBuilder;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.io.bundle.IOPathResource;
import htsjdk.beta.io.bundle.SeekableStreamResource;
import htsjdk.beta.plugin.hapref.HaploidReferenceEncoderOptions;
import htsjdk.beta.plugin.hapref.HaploidReferenceFormats;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.util.CloseableIterator;
import org.testng.Assert;
import org.testng.annotations.Test;

public class HtsFASTACodecTest extends HtsjdkTest {
    final IOPath TEST_DIR = new HtsPath("src/test/resources/htsjdk/samtools/");

    @Test
    public void testFASTADecoderFromIOPath() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "/hg19mini.fasta");

        try (final HaploidReferenceDecoder fastaDecoder =
                     HtsDefaultRegistry.getHaploidReferenceResolver().getHaploidReferenceDecoder(inputPath)) {
            Assert.assertNotNull(fastaDecoder);

            Assert.assertEquals(fastaDecoder.getFileFormat(), HaploidReferenceFormats.FASTA);
            Assert.assertEquals(fastaDecoder.getVersion(), FASTACodecV1_0.VERSION_1);
            Assert.assertTrue(fastaDecoder.getDisplayName().contains(inputPath.toString()));
            Assert.assertFalse(fastaDecoder.hasIndex());
            Assert.assertFalse(fastaDecoder.isQueryable());

            final SAMSequenceDictionary sequenceDictionary = fastaDecoder.getHeader();
            Assert.assertNotNull(sequenceDictionary);

            try (final CloseableIterator<ReferenceSequence> it = fastaDecoder.iterator()) {
                while (it.hasNext()) {
                    final ReferenceSequence referenceSequence = it.next();
                    Assert.assertNotNull(referenceSequence);
                    Assert.assertNotNull(sequenceDictionary.getSequence(referenceSequence.getName()));
                }
            }
        }
    }

    @Test
    public void testFASTADecoderFromStream() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "/hg19mini.fasta");
        final Bundle streamBundle = new BundleBuilder().addPrimary(
                new SeekableStreamResource(
                        new IOPathResource(inputPath, BundleResourceType.HAPLOID_REFERENCE).getSeekableStream().get(),
                        inputPath.toString(),
                        BundleResourceType.HAPLOID_REFERENCE)
        ).build();
        try (final HaploidReferenceDecoder fastaDecoder =
                     HtsDefaultRegistry.getHaploidReferenceResolver().getHaploidReferenceDecoder(streamBundle)) {
            Assert.assertNotNull(fastaDecoder);

            Assert.assertEquals(fastaDecoder.getFileFormat(), HaploidReferenceFormats.FASTA);
            Assert.assertEquals(fastaDecoder.getVersion(), FASTACodecV1_0.VERSION_1);
            Assert.assertTrue(fastaDecoder.getDisplayName().contains(inputPath.toString()));
            Assert.assertFalse(fastaDecoder.hasIndex());
            Assert.assertFalse(fastaDecoder.isQueryable());

            // when reading from a stream, ReferenceSequenceFile doesn't have an index from which
            // to create a sequence dictionary
            final SAMSequenceDictionary sequenceDictionary = fastaDecoder.getHeader();
             Assert.assertNull(sequenceDictionary);

            for (final ReferenceSequence referenceSequence : fastaDecoder) {
                Assert.assertNotNull(referenceSequence);
            }
        }
    }

    @Test(expectedExceptions = HtsjdkUnsupportedOperationException.class)
    public void testRejectFASTAEncoder() {
        final IOPath outPath = IOPathUtils.createTempPath("testFastEncoder", ".fasta");
        new FASTACodecV1_0().getEncoder(
                new BundleBuilder()
                        .addPrimary(new IOPathResource(outPath, BundleResourceType.HAPLOID_REFERENCE))
                        .build(),
                new HaploidReferenceEncoderOptions());
    }
}