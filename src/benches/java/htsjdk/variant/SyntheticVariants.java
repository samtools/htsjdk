package htsjdk.variant;

import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFContigHeaderLine;
import htsjdk.variant.vcf.VCFFilterHeaderLine;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Writes synthetic, deterministic VCFs for benchmarks to read, so that a baseline never depends on data outside the
 * repository: plain variant calls for any number of samples, or a single-sample gVCF that is mostly
 * {@code <NON_REF>} reference blocks.
 */
final class SyntheticVariants {
    private SyntheticVariants() {}

    private static final int CONTIGS = 22;
    private static final int CONTIG_LENGTH = 200_000_000;
    private static final String[] BASES = {"A", "C", "G", "T"};

    /** Writes a block-compressed VCF whose content depends only on the arguments. */
    static void generateFile(
            final Path path, final int records, final int samples, final boolean gvcf, final long seed) {
        final Random random = new Random(seed);
        final List<String> sampleNames = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            sampleNames.add(String.format("sample%04d", i));
        }
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .clearOptions()
                .setOutputPath(path)
                .setOutputFileType(VariantContextWriterBuilder.OutputType.BLOCK_COMPRESSED_VCF)
                .build()) {
            writer.writeHeader(new VCFHeader(headerLines(gvcf), sampleNames));
            int contig = 1;
            int position = 0;
            for (int i = 0; i < records; i++) {
                position += 1 + random.nextInt(3000);
                if (position + 10_000 > CONTIG_LENGTH) {
                    contig++;
                    position = 1 + random.nextInt(3000);
                    if (contig > CONTIGS) {
                        break;
                    }
                }
                final VariantContext vc = gvcf && random.nextInt(100) < 70
                        ? referenceBlock("chr" + contig, position, random)
                        : variant("chr" + contig, position, sampleNames, gvcf, random);
                writer.add(vc);
                if (gvcf) {
                    position = vc.getEnd();
                }
            }
        }
    }

    private static Set<VCFHeaderLine> headerLines(final boolean gvcf) {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        for (int i = 1; i <= CONTIGS; i++) {
            final Map<String, String> contig = new LinkedHashMap<>();
            contig.put("ID", "chr" + i);
            contig.put("length", String.valueOf(CONTIG_LENGTH));
            lines.add(new VCFContigHeaderLine(contig, i - 1));
        }
        lines.add(new VCFFilterHeaderLine("LowQual", "Low quality"));
        lines.add(new VCFInfoHeaderLine("AC", VCFHeaderLineCount.A, VCFHeaderLineType.Integer, "Allele count"));
        lines.add(new VCFInfoHeaderLine("AF", VCFHeaderLineCount.A, VCFHeaderLineType.Float, "Allele frequency"));
        lines.add(new VCFInfoHeaderLine("AN", 1, VCFHeaderLineType.Integer, "Allele number"));
        lines.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "Depth"));
        lines.add(new VCFInfoHeaderLine("MQ", 1, VCFHeaderLineType.Float, "Mapping quality"));
        lines.add(new VCFInfoHeaderLine("QD", 1, VCFHeaderLineType.Float, "Quality by depth"));
        lines.add(new VCFInfoHeaderLine("FS", 1, VCFHeaderLineType.Float, "Fisher strand"));
        lines.add(new VCFInfoHeaderLine("DB", 0, VCFHeaderLineType.Flag, "In dbSNP"));
        if (gvcf) {
            lines.add(new VCFInfoHeaderLine("END", 1, VCFHeaderLineType.Integer, "End of the reference block"));
        }
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "Genotype"));
        lines.add(new VCFFormatHeaderLine("AD", VCFHeaderLineCount.R, VCFHeaderLineType.Integer, "Allele depths"));
        lines.add(new VCFFormatHeaderLine("DP", 1, VCFHeaderLineType.Integer, "Depth"));
        lines.add(new VCFFormatHeaderLine("GQ", 1, VCFHeaderLineType.Integer, "Genotype quality"));
        lines.add(new VCFFormatHeaderLine("PL", VCFHeaderLineCount.G, VCFHeaderLineType.Integer, "Phred likelihoods"));
        if (gvcf) {
            lines.add(new VCFFormatHeaderLine("MIN_DP", 1, VCFHeaderLineType.Integer, "Minimum depth in the block"));
        }
        return lines;
    }

    private static VariantContext variant(
            final String contig,
            final int position,
            final List<String> sampleNames,
            final boolean gvcf,
            final Random random) {
        // 15% indels, half deletions (long REF, shorter ALTs) and half insertions (one-base REF, long ALTs); 5% of
        // records carry a second ALT. Deletion ALTs are REF prefixes of distinct lengths, so a REF of at least three
        // bases always leaves room for two of them.
        final List<Allele> alleles = new ArrayList<>();
        final boolean indel = random.nextInt(100) < 15;
        final boolean deletion = indel && random.nextBoolean();
        final String ref = deletion ? randomBases(3 + random.nextInt(6), random) : randomBase(random);
        alleles.add(Allele.create(ref, true));
        final int altCount = random.nextInt(100) < 5 ? 2 : 1;
        while (alleles.size() < altCount + 1) {
            final String alt;
            if (deletion) {
                alt = alleles.size() == 1
                        ? ref.substring(0, 1)
                        : ref.substring(0, 2 + random.nextInt(ref.length() - 2));
            } else if (indel) {
                alt = ref + randomBases(1 + random.nextInt(7), random);
            } else {
                alt = randomBase(random);
            }
            final Allele allele = Allele.create(alt, false);
            if (!alt.equals(ref) && !alleles.contains(allele)) {
                alleles.add(allele);
            }
        }
        if (gvcf) {
            alleles.add(Allele.NON_REF_ALLELE);
        }

        final List<Genotype> genotypes = new ArrayList<>(sampleNames.size());
        final int[] alleleCounts = new int[alleles.size()];
        int totalDepth = 0;
        for (final String sample : sampleNames) {
            final Genotype genotype = genotype(sample, alleles, random);
            genotypes.add(genotype);
            for (final Allele allele : genotype.getAlleles()) {
                if (allele.isCalled()) {
                    alleleCounts[alleles.indexOf(allele)]++;
                }
            }
            totalDepth += Math.max(genotype.getDP(), 0);
        }
        final int calledAlleles = Arrays.stream(alleleCounts).sum();
        final Map<String, Object> attributes = new LinkedHashMap<>();
        final List<Integer> ac = new ArrayList<>();
        final List<Double> af = new ArrayList<>();
        for (int i = 1; i < alleles.size(); i++) {
            ac.add(alleleCounts[i]);
            af.add(calledAlleles == 0 ? 0.0 : Math.round(1000.0 * alleleCounts[i] / calledAlleles) / 1000.0);
        }
        attributes.put("AC", ac);
        attributes.put("AF", af);
        attributes.put("AN", calledAlleles);
        attributes.put("DP", totalDepth);
        attributes.put("MQ", Math.round(100 * (30 + 30 * random.nextDouble())) / 100.0);
        attributes.put("QD", Math.round(100 * (30 * random.nextDouble())) / 100.0);
        attributes.put("FS", Math.round(1000 * (10 * random.nextDouble())) / 1000.0);
        if (random.nextInt(100) < 30) {
            attributes.put("DB", true);
        }

        final VariantContextBuilder builder = new VariantContextBuilder(
                        "generated", contig, position, position + ref.length() - 1, alleles)
                .genotypes(genotypes)
                .attributes(attributes)
                .log10PError(-(10 + 3000 * random.nextDouble()) / 10.0);
        if (random.nextInt(100) < 10) {
            builder.filter("LowQual");
        } else {
            builder.passFilters();
        }
        if (random.nextInt(100) < 20) {
            builder.id("rs" + (1_000_000 + random.nextInt(9_000_000)));
        }
        return builder.make();
    }

    private static Genotype genotype(final String sample, final List<Allele> alleles, final Random random) {
        final Allele ref = alleles.get(0);
        final int firstAlt = 1;
        final int draw = random.nextInt(100);
        final List<Allele> called;
        if (draw < 2) {
            called = List.of(Allele.NO_CALL, Allele.NO_CALL);
        } else if (draw < 62) {
            called = List.of(ref, ref);
        } else if (draw < 92) {
            called = List.of(ref, alleles.get(firstAlt));
        } else {
            called = List.of(alleles.get(firstAlt), alleles.get(firstAlt));
        }
        final int depth = 5 + random.nextInt(60);
        final int[] ad = new int[alleles.size()];
        for (final Allele allele : called) {
            if (allele.isCalled()) {
                ad[alleles.indexOf(allele)] += depth / 2;
            }
        }
        final int genotypeCount = alleles.size() * (alleles.size() + 1) / 2;
        final int[] pl = new int[genotypeCount];
        for (int i = 0; i < genotypeCount; i++) {
            pl[i] = random.nextInt(1000);
        }
        final GenotypeBuilder builder = new GenotypeBuilder(sample, called)
                .DP(depth)
                .GQ(random.nextInt(100))
                .phased(draw >= 2 && random.nextInt(100) < 10);
        if (draw >= 2) {
            builder.AD(ad).PL(pl);
        }
        return builder.make();
    }

    private static VariantContext referenceBlock(final String contig, final int position, final Random random) {
        final Allele ref = Allele.create(randomBase(random), true);
        final List<Allele> alleles = List.of(ref, Allele.NON_REF_ALLELE);
        final int end = position + random.nextInt(2000);
        final int depth = 5 + random.nextInt(60);
        final Genotype genotype = new GenotypeBuilder("sample0000", List.of(ref, ref))
                .DP(depth)
                .GQ(random.nextInt(100))
                .PL(new int[] {0, 30 + random.nextInt(60), 300 + random.nextInt(600)})
                .attribute("MIN_DP", Math.max(1, depth - random.nextInt(5)))
                .make();
        return new VariantContextBuilder("generated", contig, position, end, alleles)
                .genotypes(genotype)
                .attribute("END", end)
                .make();
    }

    private static String randomBase(final Random random) {
        return BASES[random.nextInt(4)];
    }

    private static String randomBases(final int length, final Random random) {
        final StringBuilder bases = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            bases.append(randomBase(random));
        }
        return bases.toString();
    }
}
