package htsjdk.beta.codecs.reads.bam;

import htsjdk.HtsjdkTest;
import htsjdk.beta.codecs.reads.bam.bamV1_0.BAMCodecV1_0;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.io.bundle.IOPathResource;
import htsjdk.beta.io.bundle.InputStreamResource;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;

public class HtsBAMCodecTest  extends HtsjdkTest {
    final IOPath TEST_DIR = new HtsPath("src/test/resources/htsjdk/samtools/");

    @Test
    public void testBAMDecoder() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "example.bam");

        try (final BAMDecoder bamDecoder = (BAMDecoder) HtsDefaultRegistry.getReadsResolver().getReadsDecoder(inputPath)) {
            Assert.assertNotNull(bamDecoder);
            Assert.assertEquals(bamDecoder.getFileFormat(), ReadsFormats.BAM);
            Assert.assertTrue(bamDecoder.getDisplayName().contains(inputPath.toString()));
            Assert.assertTrue(bamDecoder.getVersion().equals(BAMCodecV1_0.VERSION_1));

            final SAMFileHeader samFileHeader = bamDecoder.getHeader();
            Assert.assertNotNull(samFileHeader);

            Assert.assertEquals(samFileHeader.getSortOrder(), SAMFileHeader.SortOrder.coordinate);
        }
    }

    @Test
    public void testBAMEncoder() {
        final IOPath outputPath = IOUtils.createTempPath("pluginTestOutput", ".bam");
        try (final BAMEncoder bamEncoder = (BAMEncoder) HtsDefaultRegistry.getReadsResolver().getReadsEncoder(outputPath)) {
            Assert.assertNotNull(bamEncoder);
            Assert.assertEquals(bamEncoder.getFileFormat(), ReadsFormats.BAM);
            Assert.assertTrue(bamEncoder.getVersion().equals(BAMCodecV1_0.VERSION_1));
            Assert.assertTrue(bamEncoder.getDisplayName().contains(outputPath.toString()));
        }
    }

    @DataProvider(name="inputBundles")
    private Object[][] getInputVariations() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "example.bam");
        final IOPath outputPath = IOUtils.createTempPath("pluginTestOutput", ".bam");

        return new Object[][] {
                {
                        new Bundle(BundleResourceType.CT_ALIGNED_READS, Collections.singletonList(
                                new IOPathResource(inputPath, BundleResourceType.CT_ALIGNED_READS)
                        )),
                        outputPath
                },
                {
                        new Bundle(BundleResourceType.CT_ALIGNED_READS, Collections.singletonList(
                                new InputStreamResource(
                                        inputPath.getInputStream(),
                                        "test bam stream",
                                        BundleResourceType.CT_ALIGNED_READS)
                        )),
                        outputPath
                },
            };
    }

    @Test(dataProvider="inputBundles")
    public void testRoundTripBAM(final Bundle inputBundle, final IOPath outputPath) {
        roundTripFromBundle(inputBundle, outputPath);
    }

    private void roundTripFromBundle(final Bundle inputBundle, final IOPath outputPath) {
        try (final BAMDecoder bamDecoder = (BAMDecoder) HtsDefaultRegistry.getReadsResolver().getReadsDecoder(inputBundle);
             final BAMEncoder bamEncoder = (BAMEncoder) HtsDefaultRegistry.getReadsResolver().getReadsEncoder(outputPath)) {

            Assert.assertNotNull(bamDecoder);
            Assert.assertEquals(bamDecoder.getFileFormat(), ReadsFormats.BAM);
            Assert.assertNotNull(bamEncoder);
            Assert.assertEquals(bamEncoder.getFileFormat(), ReadsFormats.BAM);

            final SAMFileHeader samFileHeader = bamDecoder.getHeader();
            Assert.assertNotNull(samFileHeader);

            bamEncoder.setHeader(samFileHeader);
            for (final SAMRecord samRec : bamDecoder) {
                bamEncoder.write(samRec);
            }
        }
    }

}
