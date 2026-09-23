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

    // reserved INFO for structural variants
    /** INFO Type of structural variant (deprecated in 4.4) */
    public static final String SVTYPE = "SVTYPE";

    /** Keys the VCF specification reserves for INFO fields. */
    public static final class INFO {
        // Keys with stable definitions — registered as standard header lines
        /** Total read depth for each allele */
        public static final String ALLELE_DEPTHS = "AD";
        /** Read depth for each allele on the forward strand */
        public static final String ALLELE_DEPTHS_FORWARD_STRAND = "ADF";
        /** Read depth for each allele on the reverse strand */
        public static final String ALLELE_DEPTHS_REVERSE_STRAND = "ADR";
        /** Imprecise structural variation */
        public static final String IMPRECISE_STRUCTURAL_VARIANT = "IMPRECISE";
        /** Indicates a novel structural variation */
        public static final String NOVEL_STRUCTURAL_VARIANT = "NOVEL";
        /** Type of associated event */
        public static final String EVENT_TYPE = "EVENTTYPE";
        /** Claim made by the structural variant call (D, J, or DJ) */
        public static final String STRUCTURAL_VARIANT_CLAIM = "SVCLAIM";
        /** Total number of repeat sequences in this allele */
        public static final String REPEAT_SEQUENCE_COUNT = "RN";
        /** Repeat unit sequence of the corresponding repeat sequence */
        public static final String REPEAT_UNIT_SEQUENCE = "RUS";
        /** Repeat unit length of the corresponding repeat sequence */
        public static final String REPEAT_UNIT_LENGTH = "RUL";
        /** Repeat unit count of corresponding repeat sequence */
        public static final String REPEAT_UNIT_COUNT = "RUC";
        /** Total number of bases in the corresponding repeat sequence */
        public static final String REPEAT_SEQUENCE_LENGTH = "RB";
        /** Confidence interval around RUC */
        public static final String CONFIDENCE_INTERVAL_AROUND_REPEAT_UNIT_COUNT = "CIRUC";
        /** Confidence interval around RB */
        public static final String CONFIDENCE_INTERVAL_AROUND_REPEAT_SEQUENCE_LENGTH = "CIRB";
        /** Number of bases in each individual repeat unit (VCF 4.5) */
        public static final String INDIVIDUAL_REPEAT_UNIT_LENGTH = "RUB";

        // Keys whose Number or Type changed between versions — constant only, no standard header line
        /** Length of structural variant (Number changed from {@code .} to {@code A} in 4.4) */
        public static final String STRUCTURAL_VARIANT_LENGTH = "SVLEN";
        /** Confidence interval around POS for symbolic SVs (Number changed in 4.4) */
        public static final String CONFIDENCE_INTERVAL_AROUND_POS = "CIPOS";
        /** Confidence interval around END for symbolic SVs (Number changed in 4.4) */
        public static final String CONFIDENCE_INTERVAL_AROUND_END = "CIEND";
        /** Confidence interval for the SVLEN field (Number changed in 4.4) */
        public static final String CONFIDENCE_INTERVAL_AROUND_STRUCTURAL_VARIANT_LENGTH = "CILEN";
        /** Length of base pair identical micro-homology at breakpoints (Number changed in 4.4) */
        public static final String MICROHOMOLOGY_LENGTH = "HOMLEN";
        /** Sequence of base pair identical micro-homology at breakpoints (Number changed in 4.4) */
        public static final String MICROHOMOLOGY_SEQUENCE = "HOMSEQ";
        /** ID of the assembled alternate allele in the assembly file (Number changed in 4.4) */
        public static final String ASSEMBLED_ALTERNATE_ALLELE_ID = "BKPTID";
        /** Mobile element info (Number changed in 4.4) */
        public static final String MOBILE_ELEMENT_INFO = "MEINFO";
        /** Mobile element transduction info (Number changed in 4.4) */
        public static final String MOBILE_ELEMENT_TRANSDUCTION_INFO = "METRANS";
        /** ID of this element in Database of Genomic Variation (Number changed in 4.4) */
        public static final String DGV_ID = "DGVID";
        /** ID of this element in DBVAR (Number changed in 4.4) */
        public static final String DBVAR_ID = "DBVARID";
        /** ID of this element in DBRIP (Number changed in 4.4) */
        public static final String DBRIP_ID = "DBRIPID";
        /** ID of mate breakend (Number changed in 4.4) */
        public static final String MATE_BREAKEND_ID = "MATEID";
        /** ID of partner breakend (Number changed in 4.4) */
        public static final String PARTNER_BREAKEND_ID = "PARID";
        /** ID of associated event (Number changed in 4.4) */
        public static final String EVENT_ID = "EVENT";
        /** Copy number of allele (Number and Type changed in 4.4) */
        public static final String COPY_NUMBER = "CN";
        /** Confidence interval around copy number (Number and Type changed in 4.4) */
        public static final String CONFIDENCE_INTERVAL_AROUND_COPY_NUMBER = "CICN";

        private INFO() {}
    }

    /** Keys the VCF specification reserves for FORMAT (per-sample) fields. */
    public static final class FORMAT {
        // Keys with stable definitions — registered as standard header lines
        /** Read depth for each allele on the forward strand (VCF 4.3) */
        public static final String ALLELE_DEPTHS_FORWARD_STRAND = "ADF";
        /** Read depth for each allele on the reverse strand (VCF 4.3) */
        public static final String ALLELE_DEPTHS_REVERSE_STRAND = "ADR";
        /** Phred-scaled genotype posterior probabilities (VCF 4.3) */
        public static final String PHRED_SCALED_GENOTYPE_POSTERIORS = "PP";
        /** Phase set list (VCF 4.4) */
        public static final String PHASE_SET_LIST = "PSL";
        /** Phase set list ordinal (VCF 4.4) */
        public static final String PHASE_SET_LIST_ORDINAL = "PSO";
        /** Phase set list quality (VCF 4.4) */
        public static final String PHASE_SET_LIST_QUALITY = "PSQ";
        /** Length of a sample's {@code <*>} reference block (VCF 4.5) */
        public static final String REFERENCE_BLOCK_LENGTH = "LEN";
        /** Local alternate alleles: the ALT indices a sample's local-allele fields refer to (VCF 4.5) */
        public static final String LOCAL_ALTERNATE_ALLELES = "LAA";
        /** Local-allele representation of AD (VCF 4.5) */
        public static final String LOCAL_ALLELE_DEPTHS = "LAD";
        /** Local-allele representation of ADF (VCF 4.5) */
        public static final String LOCAL_ALLELE_DEPTHS_FORWARD_STRAND = "LADF";
        /** Local-allele representation of ADR (VCF 4.5) */
        public static final String LOCAL_ALLELE_DEPTHS_REVERSE_STRAND = "LADR";
        /** Local-allele representation of EC (VCF 4.5) */
        public static final String LOCAL_EXPECTED_ALLELE_COUNT = "LEC";
        /** Local-allele representation of GL (VCF 4.5) */
        public static final String LOCAL_GENOTYPE_LIKELIHOODS = "LGL";
        /** Local-allele representation of GP (VCF 4.5) */
        public static final String LOCAL_GENOTYPE_POSTERIORS = "LGP";
        /** Local-allele representation of PL (VCF 4.5) */
        public static final String LOCAL_PHRED_SCALED_GENOTYPE_LIKELIHOODS = "LPL";
        /** Local-allele representation of PP (VCF 4.5) */
        public static final String LOCAL_PHRED_SCALED_GENOTYPE_POSTERIORS = "LPP";
        /** RMS mapping quality (VCF 4.3) */
        public static final String RMS_MAPPING_QUALITY = "MQ";
        /** Confidence interval around copy number (VCF 4.4) */
        public static final String CONFIDENCE_INTERVAL_AROUND_COPY_NUMBER = "CICN";
        /** Copy number genotype quality */
        public static final String COPY_NUMBER_GENOTYPE_QUALITY = "CNQ";
        /** Copy number genotype likelihood */
        public static final String COPY_NUMBER_GENOTYPE_LIKELIHOODS = "CNL";
        /** Copy number posterior probabilities */
        public static final String COPY_NUMBER_POSTERIOR_PROBABILITIES = "CNP";
        /** Phred style probability score that the variant is novel */
        public static final String NOVELTY_QUALITY = "NQ";
        /** Unique haplotype identifier */
        public static final String HAPLOTYPE_ID = "HAP";
        /** Unique identifier of ancestral haplotype */
        public static final String ANCESTRAL_HAPLOTYPE_ID = "AHAP";

        // Keys whose Type changed between versions — constant only, no standard header line
        /** Copy number (Type changed from Integer to Float in 4.4) */
        public static final String COPY_NUMBER = "CN";

        /** Reserved, with no defined content (VCF 4.5); local alleles are in {@link #LOCAL_ALTERNATE_ALLELES} */
        public static final String RESERVED_LOCAL_ALLELES = "LA";

        private FORMAT() {}
    }

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
