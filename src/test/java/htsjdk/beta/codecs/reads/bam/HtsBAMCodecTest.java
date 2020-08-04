package htsjdk.beta.codecs.reads.bam;

import htsjdk.HtsjdkTest;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.bundle.IOPathResource;
import htsjdk.beta.plugin.bundle.InputStreamResource;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.registry.HtsReadsCodecs;
import htsjdk.beta.plugin.reads.ReadsFormat;
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

        try (final BAMDecoder bamDecoder = (BAMDecoder) HtsReadsCodecs.getReadsDecoder(inputPath)) {
            Assert.assertNotNull(bamDecoder);
            Assert.assertEquals(bamDecoder.getFormat(), ReadsFormat.BAM);

            final SAMFileHeader samFileHeader = bamDecoder.getHeader();
            Assert.assertNotNull(samFileHeader);

            Assert.assertEquals(samFileHeader.getSortOrder(), SAMFileHeader.SortOrder.coordinate);
        }
    }

    @Test
    public void testBAMEncoder() {
        final IOPath outputPath = new HtsPath("pluginTestOutput.bam");
        try (final BAMEncoder bamEncoder = (BAMEncoder) HtsReadsCodecs.getReadsEncoder(outputPath)) {
            Assert.assertNotNull(bamEncoder);
            Assert.assertEquals(bamEncoder.getFormat(), ReadsFormat.BAM);
        }
    }

    @DataProvider(name="inputBundles")
    private Object[][] getInputVariations() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "example.bam");
        final IOPath outputPath = new HtsPath("pluginTestOutput.bam");

        return new Object[][] {
                {
                        new Bundle(BundleResourceType.READS, Collections.singletonList(
                                new IOPathResource(inputPath, BundleResourceType.READS)
                        )),
                        outputPath
                },
                {
                        new Bundle(BundleResourceType.READS, Collections.singletonList(
                                new InputStreamResource(
                                        inputPath.getInputStream(),
                                        "test cram stream",
                                        BundleResourceType.READS)
                        )),
                        outputPath
                },
            };
    }

    @Test(dataProvider="inputBundles")
    public void testRoundTripBAM(final Bundle inputBundle, final IOPath outputPath) {
        roundTripFromBundle(inputBundle, outputPath);
    }

    //TODO: change all the args to Bundle when the registry methods are filled out
    private void roundTripFromBundle(final Bundle inputBundle, final IOPath outputPath) {
        try (final BAMDecoder bamDecoder = (BAMDecoder) HtsReadsCodecs.getReadsDecoder(inputBundle);
             final BAMEncoder bamEncoder = (BAMEncoder) HtsReadsCodecs.getReadsEncoder(outputPath)) {

            Assert.assertNotNull(bamDecoder);
            Assert.assertEquals(bamDecoder.getFormat(), ReadsFormat.BAM);
            Assert.assertNotNull(bamEncoder);
            Assert.assertEquals(bamEncoder.getFormat(), ReadsFormat.BAM);

            final SAMFileHeader samFileHeader = bamDecoder.getHeader();
            Assert.assertNotNull(samFileHeader);

            bamEncoder.setHeader(samFileHeader);
            for (final SAMRecord samRec : bamDecoder) {
                bamEncoder.write(samRec);
            }
        }
    }

}
