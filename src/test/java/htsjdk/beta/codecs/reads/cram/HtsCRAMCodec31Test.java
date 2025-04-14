package htsjdk.beta.codecs.reads.cram;

import htsjdk.HtsjdkTest;
import htsjdk.beta.codecs.reads.cram.cramV3_1.CRAMCodecV3_1;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.CRAM31Tests;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.utils.SamtoolsTestUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HtsCRAMCodec31Test extends HtsjdkTest {
    final IOPath TEST_DIR = new HtsPath("src/test/resources/htsjdk/samtools/");

    @Test
    public void testCRAMDecoder() {
        final IOPath sourceCRAMPath = new HtsPath(TEST_DIR + "cram/CEUTrio.HiSeq.WGS.b37.NA12878.20.21.v3.0.samtools.cram");
        final IOPath referencePath = new HtsPath(TEST_DIR + "reference/human_g1k_v37.20.21.fasta.gz");
        final IOPath input31CRAM = SamtoolsTestUtils.convertToCRAM(
                sourceCRAMPath,
                referencePath,
                "--output-fmt cram,version=3.1,normal");

        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions()
                .setCRAMDecoderOptions(new CRAMDecoderOptions().setReferencePath(referencePath));

        try (final CRAMDecoder cramDecoder =
                     (CRAMDecoder) HtsDefaultRegistry.getReadsResolver().getReadsDecoder(input31CRAM, readsDecoderOptions)) {
            Assert.assertNotNull(cramDecoder);
            Assert.assertEquals(cramDecoder.getFileFormat(), ReadsFormats.CRAM);
            Assert.assertEquals(cramDecoder.getVersion(), CRAMCodecV3_1.VERSION_3_1);
            Assert.assertTrue(cramDecoder.getDisplayName().contains(input31CRAM.toString()));
            Assert.assertFalse(cramDecoder.isQueryable());
            Assert.assertFalse(cramDecoder.hasIndex());

            final SAMFileHeader samFileHeader = cramDecoder.getHeader();
            Assert.assertEquals(samFileHeader.getSortOrder(), SAMFileHeader.SortOrder.coordinate);

            // draw at least one read to ensure that the reference is loaded and readable and the decoder is working
            try (final CloseableIterator<SAMRecord> iterator = cramDecoder.iterator()) {
                Assert.assertTrue(iterator.hasNext());
                final SAMRecord samRecord = iterator.next();
                Assert.assertEquals(samRecord.getReadName(), "20FUKAAXX100202:6:27:4968:125377");
            }
        }
    }

    @Test
    public void testRoundTripCRAM31() throws IOException {
        final IOPath sourceCRAMPath = new HtsPath(TEST_DIR + "cram/CEUTrio.HiSeq.WGS.b37.NA12878.20.21.v3.0.samtools.cram");
        final IOPath referencePath = new HtsPath(TEST_DIR + "reference/human_g1k_v37.20.21.fasta.gz");
        final IOPath tempCRAM31Path = IOUtils.createTempPath("htsCRAMCodecTemporary", FileExtensions.CRAM);

        final ReadsDecoderOptions readsDecoderOptions =
                new ReadsDecoderOptions().setCRAMDecoderOptions(
                        new CRAMDecoderOptions().setReferencePath(referencePath));
        final ReadsEncoderOptions readsEncoderOptions =
                new ReadsEncoderOptions()
                        .setPreSorted(true)
                        .setCRAMEncoderOptions(new CRAMEncoderOptions().setReferencePath(referencePath));

        try (final CRAMDecoder cramDecoder = (CRAMDecoder)
                HtsDefaultRegistry.getReadsResolver().getReadsDecoder(sourceCRAMPath, readsDecoderOptions);
             final CRAMEncoder cram31Encoder = (CRAMEncoder)
                     HtsDefaultRegistry.getReadsResolver().getReadsEncoder(tempCRAM31Path, readsEncoderOptions)) {

            Assert.assertNotNull(cramDecoder);
            Assert.assertEquals(cramDecoder.getFileFormat(), ReadsFormats.CRAM);
            Assert.assertTrue(cramDecoder.getDisplayName().contains(sourceCRAMPath.toString()));

            Assert.assertNotNull(cram31Encoder);
            Assert.assertEquals(cram31Encoder.getFileFormat(), ReadsFormats.CRAM);
            Assert.assertTrue(cram31Encoder.getDisplayName().contains(tempCRAM31Path.toString()));

            final SAMFileHeader samFileHeader = cramDecoder.getHeader();
            cram31Encoder.setHeader(samFileHeader);
            for (final SAMRecord samRec : cramDecoder) {
                cram31Encoder.write(samRec);
            }
        }

        // make sure we got a CRAM 3.1 file
        Assert.assertEquals(CRAM31Tests.getCRAMVersion(tempCRAM31Path), CramVersions.CRAM_v3_1);

        final List<SAMRecord> recs30 = new ArrayList<>();
        final List<SAMRecord> recs31 = new ArrayList<>();

        try (final CRAMDecoder cram30Decoder = (CRAMDecoder)
                HtsDefaultRegistry.getReadsResolver().getReadsDecoder(sourceCRAMPath, readsDecoderOptions);
             final CRAMDecoder cram31Decoder = (CRAMDecoder)
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(tempCRAM31Path, readsDecoderOptions)) {
            final Iterator<SAMRecord> it31 = cram31Decoder.iterator();
            for (final SAMRecord sam30Rec : cram30Decoder) {
                final SAMRecord sam31Rec =  it31.next();
                recs30.add(sam30Rec);
                recs31.add(sam31Rec);
                Assert.assertEquals(sam30Rec, sam31Rec);
            }
        }
    }

}
