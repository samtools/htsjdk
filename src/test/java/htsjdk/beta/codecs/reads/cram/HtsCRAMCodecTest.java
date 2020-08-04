package htsjdk.beta.codecs.reads.cram;

import htsjdk.HtsjdkTest;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.registry.HtsReadsCodecs;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormat;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.RuntimeIOException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

public class HtsCRAMCodecTest extends HtsjdkTest {
    final IOPath TEST_DIR = new HtsPath("src/test/resources/htsjdk/samtools/");

    @Test
    public void testCRAMDecoder() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "cram/ce#unmap2.3.0.cram");
        final IOPath referencePath = new HtsPath(TEST_DIR + "cram/c2.fa");

        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions().setReferencePath(referencePath);

        try (final CRAMDecoder cramDecoder = (CRAMDecoder) HtsReadsCodecs.getReadsDecoder(inputPath, readsDecoderOptions)) {
            Assert.assertNotNull(cramDecoder);
            Assert.assertEquals(cramDecoder.getFormat(), ReadsFormat.CRAM);

            final SAMFileHeader samFileHeader = cramDecoder.getHeader();
            Assert.assertEquals(samFileHeader.getSortOrder(), SAMFileHeader.SortOrder.unsorted);
        }
    }

    @Test
    public void testRoundTripCRAM() {
        final IOPath cramInputPath = new HtsPath(TEST_DIR + "cram/c2#pad.3.0.cram");
        final IOPath cramOutputPath = new HtsPath("pluginTestOutput.cram");
        final IOPath referencePath = new HtsPath(TEST_DIR + "cram/c2.fa");

        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions().setReferencePath(referencePath);
        final ReadsEncoderOptions readsEncoderOptions = new ReadsEncoderOptions().setReferencePath(referencePath);

        try (final CRAMDecoder cramDecoder = (CRAMDecoder) HtsReadsCodecs.getReadsDecoder(cramInputPath, readsDecoderOptions);
             final CRAMEncoder cramEncoder = (CRAMEncoder) HtsReadsCodecs.getReadsEncoder(cramOutputPath, readsEncoderOptions)) {

            Assert.assertNotNull(cramDecoder);
            Assert.assertEquals(cramDecoder.getFormat(), ReadsFormat.CRAM);
            Assert.assertNotNull(cramEncoder);
            Assert.assertEquals(cramEncoder.getFormat(), ReadsFormat.CRAM);

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
                System.out.println(samRec);
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Test
    public void testCRAMCustomDecoderOptions() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "cram/ce#unmap2.3.0.cram");
        final IOPath referencePath = new HtsPath(TEST_DIR + "cram/c2.fa");

        final CRAMDecoderOptions customDecoderOptions = new CRAMDecoderOptions().setReferencePath(referencePath);

        try (final CRAMDecoder cramDecoder = (CRAMDecoder) HtsReadsCodecs.getReadsDecoder(inputPath, customDecoderOptions)) {
            Assert.assertNotNull(cramDecoder);
            Assert.assertEquals(cramDecoder.getFormat(), ReadsFormat.CRAM);

            final SAMFileHeader samFileHeader = cramDecoder.getHeader();
            Assert.assertNotNull(samFileHeader);

            Assert.assertEquals(samFileHeader.getSortOrder(), SAMFileHeader.SortOrder.unsorted);
        }
    }

}
