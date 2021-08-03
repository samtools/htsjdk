package htsjdk.beta.codecs.reads.sam;

import htsjdk.HtsjdkTest;
import htsjdk.beta.codecs.reads.sam.samV1_0.SAMCodecV1_0;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.io.bundle.IOPathResource;
import htsjdk.beta.io.bundle.InputStreamResource;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;

public class SAMCodecV1_0Test extends HtsjdkTest {
    final IOPath TEST_DIR = new HtsPath("src/test/resources/htsjdk/samtools/");

    @Test
    public void testSAMDecoder() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "coordinate_sorted.sam");

        try (final ReadsDecoder samDecoder = HtsDefaultRegistry.getReadsResolver().getReadsDecoder(inputPath)) {
            Assert.assertNotNull(samDecoder);
            Assert.assertEquals(samDecoder.getFileFormat(), ReadsFormats.SAM);
            Assert.assertEquals(samDecoder.getVersion(), SAMCodecV1_0.VERSION_1);
            Assert.assertTrue(samDecoder.getDisplayName().contains(inputPath.toString()));
            Assert.assertEquals(samDecoder.isQueryable(), false);
            Assert.assertEquals(samDecoder.hasIndex(), false);

            final SAMFileHeader samFileHeader = samDecoder.getHeader();
            Assert.assertNotNull(samFileHeader);
            Assert.assertEquals(samFileHeader.getSortOrder(), SAMFileHeader.SortOrder.coordinate);
        }
    }

    @Test
    public void testSAMEncoder() {
        final IOPath outputPath = IOUtils.createTempPath("pluginTestOutput", ".sam");
        try (final SAMEncoder samEncoder = (SAMEncoder) HtsDefaultRegistry.getReadsResolver().getReadsEncoder(outputPath)) {
            Assert.assertNotNull(samEncoder);
            Assert.assertEquals(samEncoder.getFileFormat(), ReadsFormats.SAM);
        }
    }

    @DataProvider(name="inputBundles")
    private Object[][] getInputVariations() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "coordinate_sorted.sam");
        final IOPath outputPath = IOUtils.createTempPath("pluginTestOutput", ".sam");

        return new Object[][] {
                {
                        new Bundle(BundleResourceType.ALIGNED_READS, Collections.singletonList(
                                new IOPathResource(inputPath, BundleResourceType.ALIGNED_READS)
                        )),
                        outputPath
                },
                {
                        new Bundle(BundleResourceType.ALIGNED_READS, Collections.singletonList(
                                new InputStreamResource(
                                        inputPath.getInputStream(),
                                        "test sam stream",
                                        BundleResourceType.ALIGNED_READS)
                        )),
                        outputPath
                },
        };
    }

    @Test(dataProvider="inputB" +
            "undles")
    public void testRoundTripSAM(final Bundle inputBundle, final IOPath outputPath) {
        roundTripFromBundle(inputBundle, outputPath);
    }

    private void roundTripFromBundle(final Bundle inputBundle, final IOPath outputPath) {
        try (final ReadsDecoder samDecoder = HtsDefaultRegistry.getReadsResolver().getReadsDecoder(inputBundle);
             final ReadsEncoder samEncoder = HtsDefaultRegistry.getReadsResolver().getReadsEncoder(outputPath)) {

            Assert.assertNotNull(samDecoder);
            Assert.assertEquals(samDecoder.getFileFormat(), ReadsFormats.SAM);
            Assert.assertTrue(samDecoder.getDisplayName().contains(
                    inputBundle.get(BundleResourceType.ALIGNED_READS).get().getDisplayName()));

            Assert.assertNotNull(samEncoder);
            Assert.assertEquals(samEncoder.getFileFormat(), ReadsFormats.SAM);
            Assert.assertEquals(samEncoder.getVersion(), SAMCodecV1_0.VERSION_1);
            Assert.assertTrue(samEncoder.getDisplayName().contains(outputPath.toString()));

            final SAMFileHeader samFileHeader = samDecoder.getHeader();
            Assert.assertNotNull(samFileHeader);

            samEncoder.setHeader(samFileHeader);
            for (final SAMRecord samRec : samDecoder) {
                samEncoder.write(samRec);
            }
        }
    }

}
