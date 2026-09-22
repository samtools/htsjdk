/*
 * Copyright (c) 2012 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package htsjdk.variant.vcf;

import java.util.Locale;

public final class VCFConstants {
    public static final Locale VCF_LOCALE = Locale.US;

    // reserved INFO/FORMAT field keys
    public static final String ANCESTRAL_ALLELE_KEY = "AA";
    public static final String ALLELE_COUNT_KEY = "AC";
    public static final String ALLELE_FREQUENCY_KEY = "AF";
    public static final String ALLELE_NUMBER_KEY = "AN";
    public static final String RMS_BASE_QUALITY_KEY = "BQ";
    public static final String CIGAR_KEY = "CIGAR";
    public static final String DBSNP_KEY = "DB";
    public static final String DEPTH_KEY = "DP";
    public static final String END_KEY = "END";

    public static final String GENOTYPE_FILTER_KEY = "FT";
    public static final String GENOTYPE_KEY = "GT";
    public static final String GENOTYPE_POSTERIORS_KEY = "GP";
    public static final String GENOTYPE_QUALITY_KEY = "GQ";
    public static final String GENOTYPE_ALLELE_DEPTHS =
            "AD"; // AD isn't reserved, but is specifically handled by VariantContext
    public static final String GENOTYPE_PL_KEY = "PL"; // phred-scaled genotype likelihoods
    public static final String EXPECTED_ALLELE_COUNT_KEY = "EC";

    @Deprecated
    public static final String GENOTYPE_LIKELIHOODS_KEY = "GL"; // log10 scaled genotype likelihoods

    public static final String HAPMAP2_KEY = "H2";
    public static final String HAPMAP3_KEY = "H3";
    public static final String HAPLOTYPE_QUALITY_KEY = "HQ";
    public static final String RMS_MAPPING_QUALITY_KEY = "MQ";
    public static final String MAPPING_QUALITY_ZERO_KEY = "MQ0";
    public static final String SAMPLE_NUMBER_KEY = "NS";
    public static final String PHASE_QUALITY_KEY = "PQ";
    public static final String PHASE_SET_KEY = "PS";
    public static final String OLD_DEPTH_KEY = "RD";
    public static final String STRAND_BIAS_KEY = "SB";
    public static final String SOMATIC_KEY = "SOMATIC";
    public static final String VALIDATED_KEY = "VALIDATED";
    public static final String THOUSAND_GENOMES_KEY = "1000G";

    // INFO keys with stable definitions — registered as standard header lines
    /** INFO Total read depth for each allele */
    public static final String INFO_ALLELE_DEPTHS_KEY = "AD";
    /** INFO Read depth for each allele on the forward strand */
    public static final String INFO_ALLELE_DEPTHS_FORWARD_KEY = "ADF";
    /** INFO Read depth for each allele on the reverse strand */
    public static final String INFO_ALLELE_DEPTHS_REVERSE_KEY = "ADR";
    /** INFO Imprecise structural variation */
    public static final String IMPRECISE_KEY = "IMPRECISE";
    /** INFO Indicates a novel structural variation */
    public static final String NOVEL_KEY = "NOVEL";
    /** INFO Type of associated event */
    public static final String EVENTTYPE_KEY = "EVENTTYPE";
    /** INFO Claim made by the structural variant call (D, J, or DJ) */
    public static final String SVCLAIM_KEY = "SVCLAIM";
    /** INFO Total number of repeat sequences in this allele */
    public static final String RN_KEY = "RN";
    /** INFO Repeat unit sequence of the corresponding repeat sequence */
    public static final String RUS_KEY = "RUS";
    /** INFO Repeat unit length of the corresponding repeat sequence */
    public static final String RUL_KEY = "RUL";
    /** INFO Repeat unit count of corresponding repeat sequence */
    public static final String RUC_KEY = "RUC";
    /** INFO Total number of bases in the corresponding repeat sequence */
    public static final String RB_KEY = "RB";
    /** INFO Confidence interval around RUC */
    public static final String CIRUC_KEY = "CIRUC";
    /** INFO Confidence interval around RB */
    public static final String CIRB_KEY = "CIRB";
    /** INFO Number of bases in each individual repeat unit (VCF 4.5) */
    public static final String RUB_KEY = "RUB";

    // INFO keys whose Number or Type changed between versions — constant only, no standard header line
    /** INFO Type of structural variant (deprecated in 4.4) */
    public static final String SVTYPE = "SVTYPE";
    /** INFO Length of structural variant (Number changed from {@code .} to {@code A} in 4.4) */
    public static final String SVLEN_KEY = "SVLEN";
    /** INFO Confidence interval around POS for symbolic SVs (Number changed in 4.4) */
    public static final String CIPOS_KEY = "CIPOS";
    /** INFO Confidence interval around END for symbolic SVs (Number changed in 4.4) */
    public static final String CIEND_KEY = "CIEND";
    /** INFO Confidence interval for the SVLEN field (Number changed in 4.4) */
    public static final String CILEN_KEY = "CILEN";
    /** INFO Length of base pair identical micro-homology at breakpoints (Number changed in 4.4) */
    public static final String HOMLEN_KEY = "HOMLEN";
    /** INFO Sequence of base pair identical micro-homology at breakpoints (Number changed in 4.4) */
    public static final String HOMSEQ_KEY = "HOMSEQ";
    /** INFO ID of the assembled alternate allele in the assembly file (Number changed in 4.4) */
    public static final String BKPTID_KEY = "BKPTID";
    /** INFO Mobile element info (Number changed in 4.4) */
    public static final String MEINFO_KEY = "MEINFO";
    /** INFO Mobile element transduction info (Number changed in 4.4) */
    public static final String METRANS_KEY = "METRANS";
    /** INFO ID of this element in Database of Genomic Variation (Number changed in 4.4) */
    public static final String DGVID_KEY = "DGVID";
    /** INFO ID of this element in DBVAR (Number changed in 4.4) */
    public static final String DBVARID_KEY = "DBVARID";
    /** INFO ID of this element in DBRIP (Number changed in 4.4) */
    public static final String DBRIPID_KEY = "DBRIPID";
    /** INFO ID of mate breakend (Number changed in 4.4) */
    public static final String MATEID_KEY = "MATEID";
    /** INFO ID of partner breakend (Number changed in 4.4) */
    public static final String PARID_KEY = "PARID";
    /** INFO ID of associated event (Number changed in 4.4) */
    public static final String EVENT_KEY = "EVENT";
    /** INFO Copy number of allele (Number and Type changed in 4.4) */
    public static final String INFO_CN_KEY = "CN";
    /** INFO Confidence interval around copy number (Number and Type changed in 4.4) */
    public static final String INFO_CICN_KEY = "CICN";

    // FORMAT keys with stable definitions — registered as standard header lines
    /** FORMAT Phred-scaled genotype posterior probabilities (VCF 4.3) */
    public static final String GENOTYPE_POSTERIORS_PHRED_KEY = "PP";
    /** FORMAT Phase set list (VCF 4.4) */
    public static final String PHASE_SET_LIST_KEY = "PSL";
    /** FORMAT Phase set list ordinal (VCF 4.4) */
    public static final String PHASE_SET_LIST_ORDINAL_KEY = "PSO";
    /** FORMAT Phase set list quality (VCF 4.4) */
    public static final String PHASE_SET_LIST_QUALITY_KEY = "PSQ";
    /** FORMAT Length of a sample's {@code <*>} reference block (VCF 4.5) */
    public static final String LEN_KEY = "LEN";
    /** FORMAT Local alternate alleles: the ALT indices a sample's local-allele fields refer to (VCF 4.5) */
    public static final String LAA_KEY = "LAA";
    /** FORMAT Local-allele representation of AD (VCF 4.5) */
    public static final String LAD_KEY = "LAD";
    /** FORMAT Local-allele representation of ADF (VCF 4.5) */
    public static final String LADF_KEY = "LADF";
    /** FORMAT Local-allele representation of ADR (VCF 4.5) */
    public static final String LADR_KEY = "LADR";
    /** FORMAT Local-allele representation of EC (VCF 4.5) */
    public static final String LEC_KEY = "LEC";
    /** FORMAT Local-allele representation of GL (VCF 4.5) */
    public static final String LGL_KEY = "LGL";
    /** FORMAT Local-allele representation of GP (VCF 4.5) */
    public static final String LGP_KEY = "LGP";
    /** FORMAT Local-allele representation of PL (VCF 4.5) */
    public static final String LPL_KEY = "LPL";
    /** FORMAT Local-allele representation of PP (VCF 4.5) */
    public static final String LPP_KEY = "LPP";
    /** FORMAT RMS mapping quality (VCF 4.3) */
    public static final String FORMAT_MQ_KEY = "MQ";
    /** FORMAT Confidence interval around copy number (VCF 4.4) */
    public static final String FORMAT_CICN_KEY = "CICN";
    /** FORMAT Copy number genotype quality */
    public static final String CNQ_KEY = "CNQ";
    /** FORMAT Copy number genotype likelihood */
    public static final String CNL_KEY = "CNL";
    /** FORMAT Copy number posterior probabilities */
    public static final String CNP_KEY = "CNP";
    /** FORMAT Phred style probability score that the variant is novel */
    public static final String NQ_KEY = "NQ";
    /** FORMAT Unique haplotype identifier */
    public static final String HAP_KEY = "HAP";
    /** FORMAT Unique identifier of ancestral haplotype */
    public static final String AHAP_KEY = "AHAP";

    // FORMAT keys whose Type changed between versions — constant only, no standard header line
    /** FORMAT Copy number (Type changed from Integer to Float in 4.4) */
    public static final String FORMAT_CN_KEY = "CN";
    /** Reserved placeholder for the LA (local alternate) number code (VCF 4.5) */
    public static final String LA_KEY = "LA";

    // separators
    public static final String FORMAT_FIELD_SEPARATOR = ":";
    public static final String GENOTYPE_FIELD_SEPARATOR = ":";
    public static final char GENOTYPE_FIELD_SEPARATOR_CHAR = ':';
    public static final String FIELD_SEPARATOR = "\t";
    public static final char FIELD_SEPARATOR_CHAR = '\t';
    public static final String FILTER_CODE_SEPARATOR = ";";
    public static final String INFO_FIELD_ARRAY_SEPARATOR = ",";
    public static final char INFO_FIELD_ARRAY_SEPARATOR_CHAR = ',';
    public static final String ID_FIELD_SEPARATOR = ";";
    public static final char ID_FIELD_SEPARATOR_CHAR = ';';
    public static final String INFO_FIELD_SEPARATOR = ";";
    public static final char INFO_FIELD_SEPARATOR_CHAR = ';';
    public static final String UNPHASED = "/";
    public static final String PHASED = "|";
    public static final String PHASED_SWITCH_PROB_v3 = "\\";
    public static final String PHASING_TOKENS = "/|\\";

    // header lines
    public static final String FILTER_HEADER_START = "##FILTER";
    public static final String FORMAT_HEADER_START = "##FORMAT";
    public static final String INFO_HEADER_START = "##INFO";
    public static final String ALT_HEADER_KEY = "ALT";
    public static final String ALT_HEADER_START = VCFHeader.METADATA_INDICATOR + ALT_HEADER_KEY;
    public static final String CONTIG_HEADER_KEY = "contig";
    public static final String CONTIG_HEADER_START = "##" + CONTIG_HEADER_KEY;

    public static final int ALT_HEADER_OFFSET = ALT_HEADER_START.length() + 1;

    public static final String PEDIGREE_HEADER_KEY = "PEDIGREE";
    public static final String PEDIGREE_HEADER_START = VCFHeader.METADATA_INDICATOR + PEDIGREE_HEADER_KEY;
    public static final int PEDIGREE_HEADER_OFFSET = PEDIGREE_HEADER_START.length() + 1;

    public static final String SAMPLE_HEADER_KEY = "SAMPLE";
    public static final String SAMPLE_HEADER_START = VCFHeader.METADATA_INDICATOR + SAMPLE_HEADER_KEY;
    public static final int SAMPLE_HEADER_OFFSET = SAMPLE_HEADER_START.length() + 1;

    public static final String META_HEADER_KEY = "META";
    public static final String META_HEADER_START = VCFHeader.METADATA_INDICATOR + META_HEADER_KEY;
    public static final int META_HEADER_OFFSET = META_HEADER_START.length() + 1;

    // old indel alleles
    public static final char DELETION_ALLELE_v3 = 'D';
    public static final char INSERTION_ALLELE_v3 = 'I';

    // special alleles
    public static final char SPANNING_DELETION_ALLELE = '*';
    public static final char NO_CALL_ALLELE = '.';
    public static final char NULL_ALLELE = '-';

    // missing/default values
    public static final String UNFILTERED = ".";
    public static final String PASSES_FILTERS_v3 = "0";
    public static final String PASSES_FILTERS_v4 = "PASS";
    public static final String EMPTY_ID_FIELD = ".";
    public static final String EMPTY_INFO_FIELD = ".";
    public static final String EMPTY_ALTERNATE_ALLELE_FIELD = ".";
    public static final String MISSING_VALUE_v4 = ".";
    public static final String MISSING_QUALITY_v3 = "-1";
    public static final Double MISSING_QUALITY_v3_DOUBLE = Double.valueOf(MISSING_QUALITY_v3);

    public static final String MISSING_GENOTYPE_QUALITY_v3 = "-1";
    public static final String MISSING_HAPLOTYPE_QUALITY_v3 = "-1";
    public static final String MISSING_DEPTH_v3 = "-1";
    public static final String UNBOUNDED_ENCODING_v4 = ".";
    public static final String UNBOUNDED_ENCODING_v3 = "-1";
    public static final String PER_ALTERNATE_COUNT = "A";
    public static final String PER_ALLELE_COUNT = "R";
    public static final String PER_GENOTYPE_COUNT = "G";
    public static final String PER_GT_ALLELE_COUNT = "P";
    public static final String PER_LOCAL_ALTERNATE_COUNT = "LA";
    public static final String PER_LOCAL_ALLELE_COUNT = "LR";
    public static final String PER_LOCAL_GENOTYPE_COUNT = "LG";
    public static final String PER_BASE_MODIFICATION_COUNT = "M";
    public static final String EMPTY_ALLELE = ".";
    public static final String EMPTY_GENOTYPE = "./.";
    public static final int MAX_GENOTYPE_QUAL = 99;

    public static final Double VCF_ENCODING_EPSILON =
            0.00005; // when we consider fields equal(), used in the Qual compare
}
