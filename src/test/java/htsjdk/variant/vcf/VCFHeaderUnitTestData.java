package htsjdk.variant.vcf;

import org.testng.Assert;

import java.util.*;

// Unit test data used by unit tests for VCFHeader, VCFMetaDataLines, and VCFHeaderLine hierarchy.
public class VCFHeaderUnitTestData {
    public VCFHeaderVersion canonicalVersion = VCFHeader.DEFAULT_VCF_VERSION;

    // fileformat line
    public List<VCFHeaderLine> fileformatLines = new ArrayList<VCFHeaderLine>() {{
        add(new VCFHeaderLine(canonicalVersion.getFormatString(), canonicalVersion.getVersionString()));
    }};

    // FILTER lines
    public List<VCFHeaderLine> filterLines = new ArrayList<VCFHeaderLine>() {{
        add(new VCFFilterHeaderLine("LowQual", "Description=\"Low quality\""));
        add(new VCFFilterHeaderLine("highDP", "Description=\"DP < 8\""));
        add(new VCFFilterHeaderLine("TruthSensitivityTranche98.50to98.80", "Truth sensitivity tranche level at VSQ Lod: -0.1106 <= x < 0.6654"));
    }};

    // FORMAT lines
    public List<VCFHeaderLine> formatLines = new ArrayList<VCFHeaderLine>() {{
        add(new VCFFormatHeaderLine(VCFConstants.GENOTYPE_KEY, 1, VCFHeaderLineType.String, "Genotype"));
        add(new VCFFormatHeaderLine(VCFConstants.GENOTYPE_QUALITY_KEY, 1, VCFHeaderLineType.Integer, "Genotype Quality"));
        add(new VCFFormatHeaderLine(VCFConstants.DEPTH_KEY, 1, VCFHeaderLineType.Integer, "Approximate read depth (reads with MQ=255 or with bad mates are filtered)"));
        add(new VCFFormatHeaderLine(VCFConstants.GENOTYPE_PL_KEY, VCFHeaderLineCount.G, VCFHeaderLineType.Integer, "Normalized, Phred-scaled likelihoods for genotypes as defined in the VCF specification"));
        add(new VCFFormatHeaderLine(VCFConstants.GENOTYPE_ALLELE_DEPTHS, VCFHeaderLineCount.R, VCFHeaderLineType.Integer, "Allelic depths for the ref and alt alleles in the order listed"));
        add(new VCFFormatHeaderLine(VCFConstants.PHASE_QUALITY_KEY, 1, VCFHeaderLineType.Float, "Read-backed phasing quality"));
        add(new VCFFormatHeaderLine("MLPSAF", VCFHeaderLineCount.A, VCFHeaderLineType.Float, "Maximum likelihood expectation (MLE) for the alternate allele fraction"));
        add(new VCFFormatHeaderLine(VCFConstants.GENOTYPE_FILTER_KEY, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "Genotype-level filter"));
    }};

    // INFO lines
    public List<VCFHeaderLine> infoLines = new ArrayList<VCFHeaderLine>() {{
        add(new VCFInfoHeaderLine(VCFConstants.END_KEY, 1, VCFHeaderLineType.Integer, "Stop position of the interval"));
        add(new VCFInfoHeaderLine(VCFConstants.DBSNP_KEY, 0, VCFHeaderLineType.Flag, "dbSNP Membership"));
        add(new VCFInfoHeaderLine(VCFConstants.DEPTH_KEY, 1, VCFHeaderLineType.Integer, "Approximate read depth; some reads may have been filtered"));
        add(new VCFInfoHeaderLine(VCFConstants.STRAND_BIAS_KEY, 1, VCFHeaderLineType.Float, "Strand Bias"));
        add(new VCFInfoHeaderLine(VCFConstants.ALLELE_FREQUENCY_KEY, VCFHeaderLineCount.A, VCFHeaderLineType.Float, "Allele Frequency, for each ALT allele, in the same order as listed"));
        add(new VCFInfoHeaderLine(VCFConstants.ALLELE_COUNT_KEY, VCFHeaderLineCount.A, VCFHeaderLineType.Integer, "Allele count in genotypes, for each ALT allele, in the same order as listed"));
        add(new VCFInfoHeaderLine(VCFConstants.ALLELE_NUMBER_KEY, 1, VCFHeaderLineType.Integer, "Total number of alleles in called genotypes"));
        add(new VCFInfoHeaderLine(VCFConstants.MAPPING_QUALITY_ZERO_KEY, 1, VCFHeaderLineType.Integer, "Total Mapping Quality Zero Reads"));
        add(new VCFInfoHeaderLine(VCFConstants.RMS_MAPPING_QUALITY_KEY, 1, VCFHeaderLineType.Float, "RMS Mapping Quality"));
        add(new VCFInfoHeaderLine(VCFConstants.SOMATIC_KEY, 0, VCFHeaderLineType.Flag, "Somatic event"));
    }};

    // CONTIG lines
    public List<VCFHeaderLine> contigLines = new ArrayList<VCFHeaderLine>() {{
        add(new VCFContigHeaderLine(Collections.singletonMap("ID", "1"), 0));
        add(new VCFContigHeaderLine(Collections.singletonMap("ID", "2"), 1));
        add(new VCFContigHeaderLine(Collections.singletonMap("ID", "3"), 2));
    }};

    //misc lines
    public List<VCFHeaderLine> miscLines = new ArrayList<VCFHeaderLine>() {{
        add(new VCFHeaderLine("reference", "g37"));
        add(new VCFHeaderLine("GATKCommandLine", "SelectVariants and such."));
    }};

    //Return a full metadata lines, retaining order in a LinkedHashSet.
    public LinkedHashSet<VCFHeaderLine> getFullMetaDataLinesAsSet() {
        LinkedHashSet<VCFHeaderLine> allHeaderLines = new LinkedHashSet<VCFHeaderLine>() {{ //preserve order
            addAll(fileformatLines);
            addAll(filterLines);
            addAll(formatLines);
            addAll(infoLines);
            addAll(contigLines);
            addAll(miscLines);
        }};
        Assert.assertEquals(allHeaderLines.size(),
                fileformatLines.size() + filterLines.size() + formatLines.size() +
                        infoLines.size() + contigLines.size() + miscLines.size());
        return allHeaderLines;
    }

    public VCFMetaDataLines getFullMetaDataLines() {
        Set<VCFHeaderLine> lineSet = getFullMetaDataLinesAsSet();
        VCFMetaDataLines md = new VCFMetaDataLines();
        md.addAllMetaDataLines(lineSet);
        return md;
    }

}
