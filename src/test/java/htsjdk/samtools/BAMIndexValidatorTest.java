package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.IOUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BAMIndexValidatorTest extends HtsjdkTest {

    private static final Path BAM_FILE = Path.of("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    private static final Path BAI_FILE = Path.of(BAM_FILE + ".bai");
    private static final Path CSI_FILE = Path.of(BAM_FILE + ".csi");

    @Test
    public void exhaustivelyTestIndexTest() throws IOException {

        BAMFileReader bamFileReader1 = new BAMFileReader(
                BAM_FILE,
                BAI_FILE,
                true,
                false,
                ValidationStringency.DEFAULT_STRINGENCY,
                new DefaultSAMRecordFactory());
        bamFileReader1.enableIndexCaching(true);
        BAMFileReader bamFileReader2 = new BAMFileReader(
                BAM_FILE,
                CSI_FILE,
                true,
                false,
                ValidationStringency.DEFAULT_STRINGENCY,
                new DefaultSAMRecordFactory());

        final SamReader samFileReader1 = new SamReader.PrimitiveSamReaderToSamReaderAdapter(bamFileReader1, null);
        final SamReader samFileReader2 = new SamReader.PrimitiveSamReaderToSamReaderAdapter(bamFileReader2, null);

        int baiCount = BamIndexValidator.exhaustivelyTestIndex(samFileReader1);
        int csiCount = BamIndexValidator.exhaustivelyTestIndex(samFileReader2);

        Assert.assertEquals(baiCount, csiCount);
    }

    @Test
    public void testAReferenceWithOneChunkIsCheckedOnce() throws IOException {
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        records.addFrag("read", 0, 1_000, false);
        final Path bam = Files.createTempFile("oneChunk.", ".bam");
        IOUtil.deleteOnExit(bam);
        IOUtil.deleteOnExit(bam.resolveSibling(bam.getFileName().toString().replaceAll("\\.bam$", ".bai")));
        try (SAMFileWriter writer =
                new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(records.getHeader(), true, bam)) {
            records.getRecords().forEach(writer::addAlignment);
        }

        try (SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
            Assert.assertEquals(BamIndexValidator.lessExhaustivelyTestIndex(reader), 1);
        }
    }

    @Test(expectedExceptions = SAMException.class)
    public void testLinearIndexOfAReferenceWithoutChunksIsStillChecked() throws IOException {
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        records.addFrag("read", 0, 1_000, false);
        final Path bam = Files.createTempFile("noChunks.", ".bam");
        IOUtil.deleteOnExit(bam);
        try (SAMFileWriter writer = new SAMFileWriterFactory().makeBAMWriter(records.getHeader(), true, bam)) {
            records.getRecords().forEach(writer::addAlignment);
        }

        // A BAI whose first reference has no bins, and a linear index pointing far beyond the end of the BAM.
        final Path bai = Files.createTempFile("noChunks.", ".bai");
        IOUtil.deleteOnExit(bai);
        try (BinaryCodec codec = new BinaryCodec(bai, true)) {
            final int referenceCount =
                    records.getHeader().getSequenceDictionary().size();
            codec.writeBytes(new byte[] {'B', 'A', 'I', 1});
            codec.writeInt(referenceCount);
            codec.writeInt(0); // n_bin
            codec.writeInt(1); // n_intv
            codec.writeLong((100 * Files.size(bam)) << 16);
            for (int i = 1; i < referenceCount; i++) {
                codec.writeLong(0); // n_bin and n_intv
            }
        }

        try (SamReader reader =
                SamReaderFactory.makeDefault().open(SamInputResource.of(bam).index(bai))) {
            BamIndexValidator.lessExhaustivelyTestIndex(reader);
        }
    }
}
