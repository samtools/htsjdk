package htsjdk.tribble.index.tabix;

import htsjdk.HtsjdkTest;
import htsjdk.index.BinningIndex;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.tribble.SimpleFeature;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.util.TabixUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link TabixIndex} as a CSI file: writing, reading, and telling a tabix CSI from other CSIs. */
public class TabixIndexCsiFormatTest extends HtsjdkTest {
    private static final SAMSequenceDictionary DICTIONARY = new SAMSequenceDictionary(
            List.of(new SAMSequenceRecord("c1", 800_000_000), new SAMSequenceRecord("c2", 1_000)));

    private static TabixIndex csiIndex() {
        final TabixIndexCreator creator = new TabixIndexCreator(DICTIONARY, TabixFormat.VCF, TabixIndexType.CSI);
        long position = 1L << 16;
        for (int start = 1; start < 800_000_000; start += 5_000_000) {
            creator.addFeature(new SimpleFeature("c1", start, start), position);
            position += 100;
        }
        creator.addFeature(new SimpleFeature("c2", 10, 10), position);
        return (TabixIndex) creator.finalizeIndex(position + 100);
    }

    private static Path tempFile(final String name) throws IOException {
        final Path dir = Files.createTempDirectory("TabixIndexCsiFormatTest");
        dir.toFile().deleteOnExit();
        return dir.resolve(name);
    }

    /** The file's decompressed content. */
    private static byte[] decompressed(final Path csi) throws IOException {
        try (final java.io.InputStream in = new htsjdk.samtools.util.BlockCompressedInputStream(csi)) {
            return in.readAllBytes();
        }
    }

    @Test
    public void testIndexSurvivesARoundTrip() throws IOException {
        // A CSI file has no linear index, so the loaded index is not equal to the built one; what must hold is
        // that loading and writing again reproduces the file, and that bins, chunks and loffsets come back intact.
        final TabixIndex index = csiIndex();
        final Path csi = tempFile("records.vcf.gz.csi");
        index.write(csi);
        final TabixIndex loaded = new TabixIndex(csi);
        Assert.assertEquals(loaded.getIndexType(), TabixIndexType.CSI);
        Assert.assertEquals(loaded.getSequenceNames(), List.of("c1", "c2"));
        Assert.assertEquals(loaded.getFormatSpec(), TabixFormat.VCF);
        for (int ref = 0; ref < 2; ref++) {
            final htsjdk.index.ReferenceBins built = index.getBinningIndex().getReference(ref);
            final htsjdk.index.ReferenceBins read = loaded.getBinningIndex().getReference(ref);
            Assert.assertEquals(read.getBinCount(), built.getBinCount());
            for (int i = 0; i < built.getBinCount(); i++) {
                Assert.assertEquals(read.getBinNumber(i), built.getBinNumber(i));
                Assert.assertEquals(read.getChunks(i), built.getChunks(i));
                Assert.assertEquals(read.getLoffset(i), built.getLoffset(i));
            }
            Assert.assertEquals(read.getMetadata(), built.getMetadata());
        }
        final Path rewritten = tempFile("rewritten.csi");
        loaded.write(rewritten);
        Assert.assertEquals(decompressed(rewritten), decompressed(csi));
    }

    @Test
    public void testRecordCountsAreStoredForBcftools() {
        // bcftools index -n reads them from the metadata pseudo-bin.
        final TabixIndex index = csiIndex();
        Assert.assertEquals(
                index.getBinningIndex()
                        .getReference(0)
                        .getMetadata()
                        .orElseThrow()
                        .mappedCount(),
                160);
        Assert.assertEquals(
                index.getBinningIndex()
                        .getReference(1)
                        .getMetadata()
                        .orElseThrow()
                        .mappedCount(),
                1);
    }

    @Test
    public void testWriteBasedOnFeaturePathUsesTheCsiExtension() throws IOException {
        final Path vcf = tempFile("records.vcf.gz");
        Files.write(vcf, new byte[0]);
        csiIndex().writeBasedOnFeaturePath(vcf);
        Assert.assertTrue(Files.exists(vcf.resolveSibling("records.vcf.gz.csi")));
    }

    @Test
    public void testIndexFactoryLoadsACsiFile() throws IOException {
        final Path csi = tempFile("records.vcf.gz.csi");
        csiIndex().write(csi);
        final Index loaded = IndexFactory.loadIndex(csi.toString());
        Assert.assertTrue(loaded instanceof TabixIndex);
        Assert.assertEquals(((TabixIndex) loaded).getIndexType(), TabixIndexType.CSI);
        Assert.assertEquals(loaded.getSequenceNames(), List.of("c1", "c2"));
        Assert.assertFalse(loaded.getBlocks("c1", 700_000_000, 700_100_000).isEmpty());
    }

    @Test
    public void testSequenceDictionaryCanBeReadFromACsiFile() throws IOException {
        final Path csi = tempFile("records.vcf.gz.csi");
        csiIndex().write(csi);
        Assert.assertEquals(TabixUtils.getSequenceDictionary(csi).getSequence(1).getSequenceName(), "c2");
    }

    @Test
    public void testNoDictionaryGivesTheDeepDefaultScheme() {
        final TabixIndexCreator creator = new TabixIndexCreator(null, TabixFormat.VCF, TabixIndexType.CSI);
        creator.addFeature(new SimpleFeature("c1", 10, 10), 1L << 16);
        final TabixIndex index = (TabixIndex) creator.finalizeIndex((1L << 16) + 100);
        Assert.assertEquals(index.getBinningIndex().getDepth(), 8);
    }

    @Test(expectedExceptions = TribbleException.class, expectedExceptionsMessageRegExp = ".*not a tabix index.*")
    public void testCsiWithoutATabixHeaderIsRejected() throws IOException {
        // What samtools writes for a BAM: a CSI with an empty aux block.
        final Path csi = tempFile("reads.bam.csi");
        try (final BlockCompressedOutputStream out =
                new BlockCompressedOutputStream(Files.newOutputStream(csi), (Path) null)) {
            final BinaryCodec codec = new BinaryCodec(out);
            csiIndex().getBinningIndex().writeCsi(codec, new byte[0]);
        }
        new TabixIndex(csi);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testTbiCannotCarryACsiBinningScheme() {
        final BinningIndex deep = new BinningIndex.Builder(14, 6).build(1);
        new TabixIndex(TabixFormat.VCF, List.of("c1"), deep, TabixIndexType.TBI);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMinShiftCannotBeSetForTbi() {
        new TabixIndexCreator(DICTIONARY, TabixFormat.VCF, TabixIndexType.TBI, 12);
    }
}
