package htsjdk.beta.codecs.reads.cram;

import htsjdk.HtsjdkTest;
import htsjdk.beta.codecs.reads.cram.cramV2_1.CRAMCodecV2_1;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

public class HtsCRAMCodec21Test extends HtsjdkTest {
    final IOPath TEST_DIR = new HtsPath("src/test/resources/htsjdk/samtools/");

    @Test
    public void testCRAMDecoder() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "cram/ce#1000.2.1.cram");
        final IOPath referencePath = new HtsPath(TEST_DIR + "cram/ce.fa");

        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions()
                .setCRAMDecoderOptions(new CRAMDecoderOptions().setReferencePath(referencePath));

        try (final CRAMDecoder cramDecoder =
                     (CRAMDecoder) HtsDefaultRegistry.getReadsResolver().getReadsDecoder(inputPath, readsDecoderOptions)) {
            Assert.assertNotNull(cramDecoder);
            Assert.assertEquals(cramDecoder.getFileFormat(), ReadsFormats.CRAM);
            Assert.assertEquals(cramDecoder.getVersion(), CRAMCodecV2_1.VERSION_2_1);
            Assert.assertTrue(cramDecoder.getDisplayName().contains(inputPath.toString()));
            Assert.assertFalse(cramDecoder.isQueryable());
            Assert.assertFalse(cramDecoder.hasIndex());

            final SAMFileHeader samFileHeader = cramDecoder.getHeader();
            Assert.assertEquals(samFileHeader.getSortOrder(), SAMFileHeader.SortOrder.unsorted);
        }
    }

    @Test
    public void testRoundTripCRAM() {
        final IOPath cramInputPath = new HtsPath(TEST_DIR + "cram/ce#1000.2.1.cram");
        final IOPath cramOutputPath = IOUtils.createTempPath("pluginTestOutput", ".cram");
        final IOPath referencePath = new HtsPath(TEST_DIR + "cram/ce.fa");

        final ReadsDecoderOptions readsDecoderOptions =
                new ReadsDecoderOptions().setCRAMDecoderOptions(
                        new CRAMDecoderOptions().setReferencePath(referencePath));
        final ReadsEncoderOptions readsEncoderOptions =
                new ReadsEncoderOptions().setCRAMEncoderOptions(new CRAMEncoderOptions().setReferencePath(referencePath));

        try (final CRAMDecoder cramDecoder = (CRAMDecoder)
                HtsDefaultRegistry.getReadsResolver().getReadsDecoder(cramInputPath, readsDecoderOptions);
             final CRAMEncoder cramEncoder = (CRAMEncoder)
                     HtsDefaultRegistry.getReadsResolver().getReadsEncoder(cramOutputPath, readsEncoderOptions)) {

            Assert.assertNotNull(cramDecoder);
            Assert.assertEquals(cramDecoder.getFileFormat(), ReadsFormats.CRAM);
            Assert.assertTrue(cramDecoder.getDisplayName().contains(cramInputPath.toString()));

            Assert.assertNotNull(cramEncoder);
            Assert.assertEquals(cramEncoder.getFileFormat(), ReadsFormats.CRAM);
            Assert.assertTrue(cramEncoder.getDisplayName().contains(cramOutputPath.toString()));

            final SAMFileHeader samFileHeader = cramDecoder.getHeader();
            Assert.assertNotNull(samFileHeader);

            cramEncoder.setHeader(samFileHeader);
            for (final SAMRecord samRec : cramDecoder) {
                cramEncoder.write(samRec);
            }
        }

        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault().referenceSequence(referencePath.toPath());
        try (final SamReader samReader = samReaderFactory.open(cramOutputPath.toPath())) {
            for (final SAMRecord samRec : samReader) {
                //System.out.println(samRec);
            }
        } catch (final IOException e) {
            throw new HtsjdkIOException(e);
        }
    }

    @Test
    public void testCRAMDecoderOptions() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "cram/ce#1000.2.1.cram");
        final IOPath referencePath = new HtsPath(TEST_DIR + "cram/ce.fa");

        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions()
                .setCRAMDecoderOptions(new CRAMDecoderOptions().setReferencePath(referencePath));

        try (final CRAMDecoder cramDecoder = (CRAMDecoder)
                HtsDefaultRegistry.getReadsResolver().getReadsDecoder(inputPath, readsDecoderOptions)) {
            Assert.assertNotNull(cramDecoder);
            Assert.assertEquals(cramDecoder.getFileFormat(), ReadsFormats.CRAM);

            final SAMFileHeader samFileHeader = cramDecoder.getHeader();
            Assert.assertNotNull(samFileHeader);

            Assert.assertEquals(samFileHeader.getSortOrder(), SAMFileHeader.SortOrder.unsorted);
        }
    }

}
