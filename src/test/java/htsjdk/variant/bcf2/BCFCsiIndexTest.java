package htsjdk.variant.bcf2;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.tribble.CloseableTribbleIterator;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFHeader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BCFCsiIndexTest extends HtsjdkTest {

    /** Writes a BGZF BCF without an index: SNPs at 100 and 300 on chr1, and at 50 on chr2. */
    private static Path writeBcfWithoutIndex() throws IOException {
        final SAMSequenceDictionary dictionary = new SAMSequenceDictionary(
                List.of(new SAMSequenceRecord("chr1", 10000), new SAMSequenceRecord("chr2", 5000)));
        final VCFHeader header = new VCFHeader(Set.of(), List.of());
        header.setSequenceDictionary(dictionary);
        final Path bcf = Files.createTempFile("BCFCsiIndexTest.", ".bcf");
        bcf.toFile().deleteOnExit();
        bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI).toFile().deleteOnExit();
        try (VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(bcf)
                .setReferenceDictionary(dictionary)
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.add(snp("chr1", 100));
            writer.add(snp("chr1", 300));
            writer.add(snp("chr2", 50));
        }
        return bcf;
    }

    private static VariantContext snp(final String contig, final int position) {
        return new VariantContextBuilder("test", contig, position, position, List.of(Allele.REF_A, Allele.ALT_C))
                .make();
    }

    private static BCFCsiIndex createIndex(final Path bcf) throws IOException {
        try (BCFFileReader reader = new BCFFileReader(bcf, null)) {
            return reader.createCsiIndex();
        }
    }

    private static List<Integer> queryStarts(
            final Path bcf, final Path csi, final String contig, final int start, final int end) throws IOException {
        final List<Integer> starts = new ArrayList<>();
        try (BCFFileReader reader = new BCFFileReader(bcf, csi);
                CloseableTribbleIterator<VariantContext> records = reader.query(contig, start, end)) {
            records.forEachRemaining(vc -> starts.add(vc.getStart()));
        }
        return starts;
    }

    @Test
    public void writtenIndexAnswersRegionQueries() throws IOException {
        final Path bcf = writeBcfWithoutIndex();
        final Path csi = Files.createTempFile("BCFCsiIndexTest.", FileExtensions.CSI);
        csi.toFile().deleteOnExit();
        createIndex(bcf).write(csi);

        Assert.assertEquals(queryStarts(bcf, csi, "chr1", 200, 400), List.of(300));
        Assert.assertEquals(queryStarts(bcf, csi, "chr2", 1, 5000), List.of(50));
    }

    @Test
    public void writeBasedOnFeaturePathWritesTheIndexBesideTheBcf() throws IOException {
        final Path bcf = writeBcfWithoutIndex();
        createIndex(bcf).writeBasedOnFeaturePath(bcf);

        final Path csi = bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI);
        Assert.assertEquals(queryStarts(bcf, csi, "chr1", 1, 150), List.of(100));
    }

    @Test
    public void contigsAreListedInTheOrderOfTheBcfDictionary() throws IOException {
        Assert.assertEquals(createIndex(writeBcfWithoutIndex()).getSequenceNames(), List.of("chr1", "chr2"));
    }

    @Test
    public void aContigNotInTheBcfHasNoBlocks() throws IOException {
        final BCFCsiIndex index = createIndex(writeBcfWithoutIndex());
        Assert.assertFalse(index.containsChromosome("chrZ"));
        Assert.assertTrue(index.getBlocks("chrZ", 1, 1000).isEmpty());
    }
}
