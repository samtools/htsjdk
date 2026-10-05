package htsjdk.beta.codecs.reads.cram;

import htsjdk.HtsjdkTest;
import htsjdk.beta.codecs.reads.cram.cramV3_0.CRAMCodecV3_0;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.beta.plugin.reads.ReadsBundle;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordSetBuilder;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.cram31.CRAM31FidelityTestBase;
import htsjdk.samtools.util.IOUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class HtsCRAMCodec30Test extends HtsjdkTest {
    final IOPath TEST_DIR = new HtsPath("src/test/resources/htsjdk/samtools/");

    @Test
    public void testCRAMDecoder() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "cram/ce#unmap2.3.0.cram");
        final IOPath referencePath = new HtsPath(TEST_DIR + "cram/c2.fa");

        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions()
                .setCRAMDecoderOptions(new CRAMDecoderOptions().setReferencePath(referencePath));

        try (final CRAMDecoder cramDecoder =
                (CRAMDecoder) HtsDefaultRegistry.getReadsResolver().getReadsDecoder(inputPath, readsDecoderOptions)) {
            Assert.assertNotNull(cramDecoder);
            Assert.assertEquals(cramDecoder.getFileFormat(), ReadsFormats.CRAM);
            Assert.assertEquals(cramDecoder.getVersion(), CRAMCodecV3_0.VERSION_3_0);
            Assert.assertTrue(cramDecoder.getDisplayName().contains(inputPath.toString()));
            Assert.assertFalse(cramDecoder.isQueryable());
            Assert.assertFalse(cramDecoder.hasIndex());

            final SAMFileHeader samFileHeader = cramDecoder.getHeader();
            Assert.assertEquals(samFileHeader.getSortOrder(), SAMFileHeader.SortOrder.unsorted);
        }
    }

    @Test
    public void testRoundTripCRAM() {
        final IOPath cramInputPath = new HtsPath(TEST_DIR + "cram/c2#pad.3.0.cram");
        final IOPath cramOutputPath = IOUtils.createTempPath("pluginTestOutput", ".cram");
        final IOPath referencePath = new HtsPath(TEST_DIR + "cram/c2.fa");

        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions()
                .setCRAMDecoderOptions(new CRAMDecoderOptions().setReferencePath(referencePath));
        final ReadsEncoderOptions readsEncoderOptions = new ReadsEncoderOptions()
                .setCRAMEncoderOptions(new CRAMEncoderOptions().setReferencePath(referencePath));

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

        final SamReaderFactory samReaderFactory =
                SamReaderFactory.makeDefault().referenceSequence(referencePath.toPath());
        try (final SamReader samReader = samReaderFactory.open(cramOutputPath.toPath())) {
            for (final SAMRecord samRec : samReader) {}
        } catch (final IOException e) {
            throw new HtsjdkIOException(e);
        }
    }

    @Test
    public void testCRAMDecoderOptions() {
        final IOPath inputPath = new HtsPath(TEST_DIR + "cram/ce#unmap2.3.0.cram");
        final IOPath referencePath = new HtsPath(TEST_DIR + "cram/c2.fa");

        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions()
                .setCRAMDecoderOptions(new CRAMDecoderOptions().setReferencePath(referencePath));

        try (final CRAMDecoder cramDecoder =
                (CRAMDecoder) HtsDefaultRegistry.getReadsResolver().getReadsDecoder(inputPath, readsDecoderOptions)) {
            Assert.assertNotNull(cramDecoder);
            Assert.assertEquals(cramDecoder.getFileFormat(), ReadsFormats.CRAM);

            final SAMFileHeader samFileHeader = cramDecoder.getHeader();
            Assert.assertNotNull(samFileHeader);

            Assert.assertEquals(samFileHeader.getSortOrder(), SAMFileHeader.SortOrder.unsorted);
        }
    }

    @Test
    public void theV3_0EncoderWritesCRAM30WhenPreSorted() throws IOException {
        Assert.assertEquals(writeGeneratedReadsThroughV3_0Encoder(true), CramVersions.CRAM_v3);
    }

    @Test
    public void theV3_0EncoderWritesCRAM30WhenNotPreSorted() throws IOException {
        Assert.assertEquals(writeGeneratedReadsThroughV3_0Encoder(false), CramVersions.CRAM_v3);
    }

    private static String coreFields(final SAMRecord read) {
        return String.join(
                " ",
                read.getReadName(),
                String.valueOf(read.getFlags()),
                read.getContig(),
                String.valueOf(read.getAlignmentStart()),
                read.getCigarString(),
                read.getReadString(),
                read.getBaseQualityString());
    }

    /**
     * Writes a few generated reads, against a generated reference, through the registry's CRAM 3.0 encoder, and
     * checks the file reads back as the reads written.
     *
     * @param preSorted whether the encoder is told the reads are presorted, which selects the writer it builds
     * @return the CRAM version in the written file's header
     */
    private static CRAMVersion writeGeneratedReadsThroughV3_0Encoder(final boolean preSorted) throws IOException {
        final Path tempDir = IOUtil.createTempDir("HtsCRAMCodec30Test");
        try {
            final SAMRecordSetBuilder readsBuilder =
                    new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate, true, 10_000);
            readsBuilder.addPair("pair1", 0, 100, 300);
            readsBuilder.addFrag("frag1", 1, 500, true);
            final Path referencePath = tempDir.resolve("reference.fasta");
            readsBuilder.writeRandomReference(referencePath);

            final IOPath cramPath = IOUtils.toHtsPath(tempDir.resolve("reads.cram"));
            final ReadsEncoderOptions readsEncoderOptions = new ReadsEncoderOptions()
                    .setPreSorted(preSorted)
                    .setCRAMEncoderOptions(new CRAMEncoderOptions().setReferencePath(IOUtils.toHtsPath(referencePath)));
            try (final ReadsEncoder cramEncoder = HtsDefaultRegistry.getReadsResolver()
                    .getReadsEncoder(
                            new ReadsBundle<>(cramPath),
                            readsEncoderOptions,
                            ReadsFormats.CRAM,
                            CRAMCodecV3_0.VERSION_3_0)) {
                cramEncoder.setHeader(readsBuilder.getHeader());
                readsBuilder.getRecords().forEach(cramEncoder::write);
            }
            try (final SamReader reader = SamReaderFactory.makeDefault()
                    .referenceSequence(referencePath)
                    .open(cramPath.toPath())) {
                // decoding adds MD and NM, which the generated reads lack, so compare everything else that was written
                final List<String> readBack = new ArrayList<>();
                reader.forEach(read -> readBack.add(coreFields(read)));
                final List<String> written = new ArrayList<>();
                readsBuilder.getRecords().forEach(read -> written.add(coreFields(read)));
                Assert.assertEquals(readBack, written);
            }
            return CRAM31FidelityTestBase.getCRAMVersion(cramPath);
        } finally {
            IOUtil.recursiveDelete(tempDir);
        }
    }
}
