package htsjdk.tribble.index.tabix;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tabix indexes that list sequences on which the file has no records, as the dictionary-driven creators produce.
 */
public class TabixIndexEmptySequenceTest extends HtsjdkTest {
    private static final SAMSequenceDictionary DICTIONARY = new SAMSequenceDictionary(List.of(
            new SAMSequenceRecord("c1", 100_000),
            new SAMSequenceRecord("c2", 100_000),
            new SAMSequenceRecord("c3", 100_000)));

    /** Writes a block-compressed VCF with 50 records on each named contig, indexed over the whole dictionary. */
    private static Path writeVcfWithRecordsOn(final String... contigs) throws IOException {
        final Path dir = IOUtil.createTempDir("TabixIndexEmptySequenceTest");
        IOUtil.deleteOnExit(dir);
        final Path vcf = dir.resolve("records.vcf.gz");
        final VCFHeader header = new VCFHeader();
        header.setSequenceDictionary(DICTIONARY);
        try (final OutputStream indexOut =
                        Files.newOutputStream(dir.resolve(vcf.getFileName() + FileExtensions.TABIX_INDEX));
                final VariantContextWriter writer = new VariantContextWriterBuilder()
                        .setOutputStream(new BlockCompressedOutputStream(Files.newOutputStream(vcf), (Path) null))
                        .setReferenceDictionary(DICTIONARY)
                        .setIndexCreator(new StreamBasedTabixIndexCreator(DICTIONARY, TabixFormat.VCF, indexOut))
                        .setOption(Options.INDEX_ON_THE_FLY)
                        .build()) {
            writer.writeHeader(header);
            for (final String contig : contigs) {
                for (int position = 1_000; position <= 50_000; position += 1_000) {
                    writer.add(new VariantContextBuilder(
                                    "test", contig, position, position, List.of(Allele.REF_A, Allele.ALT_C))
                            .make());
                }
            }
        }
        return vcf;
    }

    /** Start positions of the records an indexed query returns, in file order. */
    private static List<Integer> queryStarts(final Path vcf, final String contig, final int start, final int end) {
        final List<Integer> starts = new ArrayList<>();
        try (final VCFFileReader reader = new VCFFileReader(vcf, true);
                final CloseableIterator<VariantContext> iterator = reader.query(contig, start, end)) {
            while (iterator.hasNext()) starts.add(iterator.next().getStart());
        }
        return starts;
    }

    @Test
    public void testRecordsAreFoundWhenEarlierSequencesHaveNone() throws IOException {
        final Path vcf = writeVcfWithRecordsOn("c2");
        Assert.assertEquals(queryStarts(vcf, "c2", 9_500, 12_500), List.of(10_000, 11_000, 12_000));
    }

    @Test
    public void testRecordsAreFoundEitherSideOfASequenceThatHasNone() throws IOException {
        final Path vcf = writeVcfWithRecordsOn("c1", "c3");
        Assert.assertEquals(queryStarts(vcf, "c1", 49_500, 60_000), List.of(50_000));
        Assert.assertEquals(queryStarts(vcf, "c3", 1, 2_500), List.of(1_000, 2_000));
    }

    @Test
    public void testSequenceWithoutRecordsReturnsNothing() throws IOException {
        final Path vcf = writeVcfWithRecordsOn("c1", "c3");
        Assert.assertEquals(queryStarts(vcf, "c2", 1, 100_000), List.of());
    }

    @Test
    public void testIndexListsEveryDictionarySequence() throws IOException {
        final Path vcf = writeVcfWithRecordsOn("c2");
        final TabixIndex index = (TabixIndex) IndexFactory.loadIndex(vcf + FileExtensions.TABIX_INDEX);
        Assert.assertEquals(index.getSequenceNames(), List.of("c1", "c2", "c3"));
        Assert.assertTrue(index.getBinningIndex().getReference(0).isEmpty());
        Assert.assertFalse(index.getBinningIndex().getReference(1).isEmpty());
        Assert.assertTrue(index.getBinningIndex().getReference(2).isEmpty());
    }

    @Test
    public void testIndexWithEmptySequencesSurvivesARoundTrip() throws IOException {
        final Path vcf = writeVcfWithRecordsOn("c1", "c3");
        final TabixIndex index = (TabixIndex) IndexFactory.loadIndex(vcf + FileExtensions.TABIX_INDEX);
        final Path rewritten = vcf.resolveSibling("rewritten.tbi");
        index.write(rewritten);
        Assert.assertEquals(new TabixIndex(rewritten), index);
    }
}
