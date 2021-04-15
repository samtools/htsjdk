package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.SynchronousLineReader;
import htsjdk.variant.variantcontext.writer.VCFVersionUpgradePolicy;
import org.testng.Assert;

import java.io.StringReader;
import java.util.*;

// Unit test data used by unit tests for VCFHeader, VCFMetaDataLines, and VCFHeaderLine hierarchy.
public class VCFHeaderUnitTestData {
    public final static VCFHeaderVersion TEST_VERSION = VCFHeader.DEFAULT_VCF_VERSION;

    // fileformat line
    public static List<VCFHeaderLine> getTestDefaultFileFormatLine() {
        return new ArrayList<VCFHeaderLine>() {{
            add(VCFHeader.makeHeaderVersionLine(TEST_VERSION));
        }};
    }

    // FILTER lines
    public static List<VCFHeaderLine> getTestFilterLines() {
        return new ArrayList<VCFHeaderLine>() {{
            add(new VCFFilterHeaderLine("LowQual", "Description=\"Low quality\""));
            add(new VCFFilterHeaderLine("highDP", "Description=\"DP < 8\""));
            add(new VCFFilterHeaderLine("TruthSensitivityTranche98.50to98.80", "Truth sensitivity tranche level at VSQ Lod: -0.1106 <= x < 0.6654"));
        }};
    }

    // FORMAT lines
    public static List<VCFHeaderLine> getTestFormatLines() {
        return new ArrayList<VCFHeaderLine>() {{
            add(new VCFFormatHeaderLine(VCFConstants.GENOTYPE_KEY, 1, VCFHeaderLineType.String, "Genotype"));
            add(new VCFFormatHeaderLine(VCFConstants.GENOTYPE_QUALITY_KEY, 1, VCFHeaderLineType.Integer, "Genotype Quality"));
            add(new VCFFormatHeaderLine(VCFConstants.DEPTH_KEY, 1, VCFHeaderLineType.Integer, "Approximate read depth (reads with MQ=255 or with bad mates are filtered)"));
            add(new VCFFormatHeaderLine(VCFConstants.GENOTYPE_PL_KEY, VCFHeaderLineCount.G, VCFHeaderLineType.Integer, "Normalized, Phred-scaled likelihoods for genotypes as defined in the VCF specification"));
            add(new VCFFormatHeaderLine(VCFConstants.GENOTYPE_ALLELE_DEPTHS, VCFHeaderLineCount.R, VCFHeaderLineType.Integer, "Allelic depths for the ref and alt alleles in the order listed"));
            add(new VCFFormatHeaderLine(VCFConstants.PHASE_QUALITY_KEY, 1, VCFHeaderLineType.Float, "Read-backed phasing quality"));
            add(new VCFFormatHeaderLine("MLPSAF", VCFHeaderLineCount.A, VCFHeaderLineType.Float, "Maximum likelihood expectation (MLE) for the alternate allele fraction"));
            add(new VCFFormatHeaderLine(VCFConstants.GENOTYPE_FILTER_KEY, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "Genotype-level filter"));
        }};
    }

    // INFO lines
    public static List<VCFHeaderLine> getTestInfoLines() {
        return new ArrayList<VCFHeaderLine>() {{
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
    }

    // CONTIG lines
    public static List<VCFHeaderLine> getTestContigLines() {
        return new ArrayList<VCFHeaderLine>() {{
            add(new VCFContigHeaderLine(Collections.singletonMap("ID", "1"), 0));
            add(new VCFContigHeaderLine(Collections.singletonMap("ID", "2"), 1));
            add(new VCFContigHeaderLine(Collections.singletonMap("ID", "3"), 2));
        }};
    }

    //misc lines
    public static List<VCFHeaderLine> getTestMiscellaneousLines() {
        return new ArrayList<VCFHeaderLine>() {{
            add(new VCFHeaderLine("reference", "g37"));
            add(new VCFHeaderLine("GATKCommandLine", "SelectVariants and such."));
        }};
    }

    //Return a full set of metadata lines, retaining order in a LinkedHashSet.
    public static LinkedHashSet<VCFHeaderLine> getTestMetaDataLinesSet() {
        final LinkedHashSet<VCFHeaderLine> allHeaderLines = new LinkedHashSet<VCFHeaderLine>() {{ //preserve order
            addAll(getTestDefaultFileFormatLine());
            addAll(getTestFilterLines());
            addAll(getTestFormatLines());
            addAll(getTestInfoLines());
            addAll(getTestContigLines());
            addAll(getTestMiscellaneousLines());
        }};
        Assert.assertEquals(allHeaderLines.size(),
                1 + // file format line
                        getTestFilterLines().size() + getTestFormatLines().size() +
                        getTestInfoLines().size() + getTestContigLines().size() + getTestMiscellaneousLines().size());
        return allHeaderLines;
    }

    //Return a full set of metadata lines as a VCFMetaDataLines.
    public static VCFMetaDataLines getTestMetaDataLines() {
        final VCFMetaDataLines md = new VCFMetaDataLines();
        md.addMetaDataLines(getTestMetaDataLinesSet());
        return md;
    }

    private static final int VCF_4_HEADER_STRING_COUNT = 16; // 17 -1 for the #CHROM... line

    public static String getVCFV42TestHeaderString() {
        return "##fileformat=VCFv4.2\n" +
                        "##filedate=2010-06-21\n" +
                        "##reference=NCBI36\n" +
                        "##INFO=<ID=GC, Number=0, Type=Flag, Description=\"Overlap with Gencode CCDS coding sequence\">\n" +
                        "##INFO=<ID=DP, Number=1, Type=Integer, Description=\"Total number of reads in haplotype window\">\n" +
                        "##INFO=<ID=AF, Number=A, Type=Float, Description=\"Dindel estimated population allele frequency\">\n" +
                        "##INFO=<ID=CA, Number=1, Type=String, Description=\"Pilot 1 callability mask\">\n" +
                        "##INFO=<ID=HP, Number=1, Type=Integer, Description=\"Reference homopolymer tract length\">\n" +
                        "##INFO=<ID=NS, Number=1, Type=Integer, Description=\"Number of samples with data\">\n" +
                        "##INFO=<ID=DB, Number=0, Type=Flag, Description=\"dbSNP membership build 129 - type match and indel sequence length match within 25 bp\">\n" +
                        "##INFO=<ID=NR, Number=1, Type=Integer, Description=\"Number of reads covering non-ref variant on reverse strand\">\n" +
                        "##INFO=<ID=NF, Number=1, Type=Integer, Description=\"Number of reads covering non-ref variant on forward strand\">\n" +
                        "##FILTER=<ID=NoQCALL, Description=\"Variant called by Dindel but not confirmed by QCALL\">\n" +
                        "##FORMAT=<ID=GT, Number=1, Type=String, Description=\"Genotype\">\n" +
                        "##FORMAT=<ID=HQ, Number=2, Type=Integer, Description=\"Haplotype quality\">\n" +
                        "##FORMAT=<ID=GQ, Number=1, Type=Integer, Description=\"Genotype quality\">\n" +
                        "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";
    }

    public static final String VCF42headerStrings_with_negativeOne =
            "##fileformat=VCFv4.2\n" +
                    "##filedate=2010-06-21\n" +
                    "##reference=NCBI36\n" +
                    "##INFO=<ID=GC, Number=0, Type=Flag, Description=\"Overlap with Gencode CCDS coding sequence\">\n" +
                    "##INFO=<ID=YY, Number=., Type=Integer, Description=\"Some weird value that has lots of parameters\">\n" +
                    "##INFO=<ID=AF, Number=A, Type=Float, Description=\"Dindel estimated population allele frequency\">\n" +
                    "##INFO=<ID=CA, Number=1, Type=String, Description=\"Pilot 1 callability mask\">\n" +
                    "##INFO=<ID=HP, Number=1, Type=Integer, Description=\"Reference homopolymer tract length\">\n" +
                    "##INFO=<ID=NS, Number=1, Type=Integer, Description=\"Number of samples with data\">\n" +
                    "##INFO=<ID=DB, Number=0, Type=Flag, Description=\"dbSNP membership build 129 - type match and indel sequence length match within 25 bp\">\n" +
                    "##INFO=<ID=NR, Number=1, Type=Integer, Description=\"Number of reads covering non-ref variant on reverse strand\">\n" +
                    "##INFO=<ID=NF, Number=1, Type=Integer, Description=\"Number of reads covering non-ref variant on forward strand\">\n" +
                    "##FILTER=<ID=NoQCALL, Description=\"Variant called by Dindel but not confirmed by QCALL\">\n" +
                    "##FORMAT=<ID=GT, Number=1, Type=String, Description=\"Genotype\">\n" +
                    "##FORMAT=<ID=HQ, Number=2, Type=Integer, Description=\"Haplotype quality\">\n" +
                    "##FORMAT=<ID=TT, Number=., Type=Integer, Description=\"Lots of TTs\">\n" +
                    "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";

    public static Set<VCFHeaderLine> getV42HeaderLinesWITHOUTFormatString() {
        // precondition - create a v42 VCFMetaDataLines and make sure its v42
        final Set<VCFHeaderLine> metaDataSet = getV42HeaderLinesWITHFormatString();
        final VCFMetaDataLines metaDataLines = new VCFMetaDataLines();
        metaDataLines.addMetaDataLines(metaDataSet);
        final VCFHeaderLine versionLine = metaDataLines.getFileFormatLine();
        Assert.assertEquals(
                metaDataLines.getVCFVersion(),
                VCFHeaderVersion.VCF4_2);

        // remove the 4.2 version line from the original set, verify, and return the set with no fileformat string
        metaDataSet.remove(versionLine);
        Assert.assertNull(getVersionLineFromHeaderLineSet(metaDataSet));
        return metaDataSet;
    }

    public static Set<VCFHeaderLine> getV42HeaderLinesWITHFormatString() {
        // precondition - create a v42 header and make sure its v42
        final VCFHeader header = createHeaderFromString(getVCFV42TestHeaderString());
        Assert.assertEquals(
                header.getVCFHeaderVersion(),
                VCFHeaderVersion.VCF4_2);

        // return a mutable set for test use
        return new LinkedHashSet<>(header.getMetaDataInInputOrder());
    }

    public static VCFHeader createHeaderFromString(final String headerStr) {
        final VCFCodec codec = new VCFCodec();
        codec.setVersionUpgradePolicy(VCFVersionUpgradePolicy.DO_NOT_UPGRADE);
        final VCFHeader header = (VCFHeader) codec.readActualHeader(
                new LineIteratorImpl(new SynchronousLineReader(new StringReader(headerStr))));
        Assert.assertEquals(header.getMetaDataInInputOrder().size(), VCF_4_HEADER_STRING_COUNT);
        return header;
    }

    /**
     * Find and return the VCF fileformat/version line
     *
     * Return null if no fileformat/version lines are found
     */
    private static VCFHeaderLine getVersionLineFromHeaderLineSet(final Set<VCFHeaderLine> metaDataLines) {
        VCFHeaderLine versionLine = null;
        final List<VCFHeaderLine> formatLines = new ArrayList<>();
        for (final VCFHeaderLine headerLine : metaDataLines) {
            if (VCFHeaderVersion.isFormatString(headerLine.getKey())) {
                formatLines.add(headerLine);
            }
        }

        if (!formatLines.isEmpty()) {
            if (formatLines.size() > 1) {
                //throw if there are duplicate version lines
                throw new TribbleException("Multiple version header lines found in header line list");
            }
            return formatLines.get(0);
        }

        return versionLine;
    }

}
