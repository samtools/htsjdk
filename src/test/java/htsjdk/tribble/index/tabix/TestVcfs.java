package htsjdk.tribble.index.tabix;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Test fixture: block-compressed VCFs whose records are laid out by a rule, so queries can be checked by hand. */
final class TestVcfs {
    /** A dictionary with one contig well beyond TBI's 2^29 reach. */
    static final SAMSequenceDictionary LARGE_CONTIG_DICTIONARY = new SAMSequenceDictionary(
            List.of(new SAMSequenceRecord("small", 60_000_000), new SAMSequenceRecord("large", 830_000_000)));

    /**
     * The records on each contig: one every {@code spacing} bases from 500 on, every 40th a deletion 300 kb long
     * so that higher-level bins are used too.
     */
    record Layout(int spacing, int contigLength) {
        List<Integer> startsOverlapping(final int start, final int end) {
            final List<Integer> starts = new ArrayList<>();
            for (int i = 0, position = 500; position < contigLength; i++, position += spacing) {
                final int recordEnd = (i % 40 == 0) ? position + 300_000 : position;
                if (position <= end && recordEnd >= start) starts.add(position);
            }
            return starts;
        }
    }

    static final Layout SMALL_CONTIG = new Layout(7_000, 50_000_000);
    static final Layout LARGE_CONTIG = new Layout(70_000, 830_000_000);

    private TestVcfs() {}

    /** Writes a block-compressed VCF with the given builder settings and records on the named contigs. */
    static Path write(
            final Path dir,
            final SAMSequenceDictionary dictionary,
            final VariantContextWriterBuilder builder,
            final String... contigs)
            throws IOException {
        final Path vcf = dir.resolve("records.vcf.gz");
        final VCFHeader header = new VCFHeader();
        header.setSequenceDictionary(dictionary);
        header.addMetaDataLine(new VCFInfoHeaderLine(VCFConstants.END_KEY, 1, VCFHeaderLineType.Integer, "End"));
        try (final VariantContextWriter writer =
                builder.setOutputPath(vcf).setReferenceDictionary(dictionary).build()) {
            writer.writeHeader(header);
            for (final String contig : contigs) {
                final Layout layout = contig.equals("large") ? LARGE_CONTIG : SMALL_CONTIG;
                for (int i = 0, position = 500; position < layout.contigLength(); i++, position += layout.spacing()) {
                    if (i % 40 == 0) {
                        writer.add(new VariantContextBuilder(
                                        "test",
                                        contig,
                                        position,
                                        position + 300_000,
                                        List.of(Allele.REF_A, Allele.create("<DEL>")))
                                .attribute(VCFConstants.END_KEY, position + 300_000)
                                .make());
                    } else {
                        writer.add(new VariantContextBuilder(
                                        "test", contig, position, position, List.of(Allele.REF_A, Allele.ALT_C))
                                .make());
                    }
                }
            }
        }
        return vcf;
    }

    static Path tempDir(final String prefix) {
        final Path dir = IOUtil.createTempDir(prefix);
        IOUtil.deleteOnExit(dir);
        return dir;
    }

    /** Start positions of the records an indexed query returns, in file order. */
    static List<Integer> queryStarts(final Path vcf, final String contig, final int start, final int end) {
        final List<Integer> starts = new ArrayList<>();
        try (final VCFFileReader reader = new VCFFileReader(vcf, true);
                final CloseableIterator<VariantContext> iterator = reader.query(contig, start, end)) {
            while (iterator.hasNext()) starts.add(iterator.next().getStart());
        }
        return starts;
    }
}
