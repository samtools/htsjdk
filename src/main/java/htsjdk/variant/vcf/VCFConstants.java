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

    // The INFO and FORMAT keys under their top-level names, each an alias of its constant in INFO or FORMAT
    /** @deprecated use {@link INFO#ANCESTRAL_ALLELE} */
    @Deprecated
    public static final String ANCESTRAL_ALLELE_KEY = INFO.ANCESTRAL_ALLELE;

    /** @deprecated use {@link INFO#ALLELE_COUNT} */
    @Deprecated
    public static final String ALLELE_COUNT_KEY = INFO.ALLELE_COUNT;

    /** @deprecated use {@link INFO#ALLELE_FREQUENCY} */
    @Deprecated
    public static final String ALLELE_FREQUENCY_KEY = INFO.ALLELE_FREQUENCY;

    /** @deprecated use {@link INFO#ALLELE_NUMBER} */
    @Deprecated
    public static final String ALLELE_NUMBER_KEY = INFO.ALLELE_NUMBER;

    /** @deprecated use {@link INFO#RMS_BASE_QUALITY} */
    @Deprecated
    public static final String RMS_BASE_QUALITY_KEY = INFO.RMS_BASE_QUALITY;

    /** @deprecated use {@link INFO#CIGAR} */
    @Deprecated
    public static final String CIGAR_KEY = INFO.CIGAR;

    /** @deprecated use {@link INFO#IN_DBSNP} */
    @Deprecated
    public static final String DBSNP_KEY = INFO.IN_DBSNP;

    /** @deprecated use {@link FORMAT#READ_DEPTH} or {@link INFO#COMBINED_DEPTH} */
    @Deprecated
    public static final String DEPTH_KEY = FORMAT.READ_DEPTH;

    /** @deprecated use {@link INFO#END_POSITION} */
    @Deprecated
    public static final String END_KEY = INFO.END_POSITION;

    /** @deprecated use {@link FORMAT#GENOTYPE_FILTER} */
    @Deprecated
    public static final String GENOTYPE_FILTER_KEY = FORMAT.GENOTYPE_FILTER;

    /** @deprecated use {@link FORMAT#GENOTYPE} */
    @Deprecated
    public static final String GENOTYPE_KEY = FORMAT.GENOTYPE;

    /** @deprecated use {@link FORMAT#GENOTYPE_POSTERIORS} */
    @Deprecated
    public static final String GENOTYPE_POSTERIORS_KEY = FORMAT.GENOTYPE_POSTERIORS;

    /** @deprecated use {@link FORMAT#GENOTYPE_QUALITY} */
    @Deprecated
    public static final String GENOTYPE_QUALITY_KEY = FORMAT.GENOTYPE_QUALITY;

    /** @deprecated use {@link FORMAT#ALLELE_DEPTHS} */
    @Deprecated
    public static final String GENOTYPE_ALLELE_DEPTHS = FORMAT.ALLELE_DEPTHS;

    /** @deprecated use {@link FORMAT#PHRED_SCALED_GENOTYPE_LIKELIHOODS} */
    @Deprecated
    public static final String GENOTYPE_PL_KEY = FORMAT.PHRED_SCALED_GENOTYPE_LIKELIHOODS;

    /** @deprecated use {@link FORMAT#EXPECTED_ALLELE_COUNT} */
    @Deprecated
    public static final String EXPECTED_ALLELE_COUNT_KEY = FORMAT.EXPECTED_ALLELE_COUNT;

    /** @deprecated use {@link FORMAT#GENOTYPE_LIKELIHOODS} */
    @Deprecated
    public static final String GENOTYPE_LIKELIHOODS_KEY = FORMAT.GENOTYPE_LIKELIHOODS;

    /** @deprecated use {@link INFO#IN_HAPMAP2} */
    @Deprecated
    public static final String HAPMAP2_KEY = INFO.IN_HAPMAP2;

    /** @deprecated use {@link INFO#IN_HAPMAP3} */
    @Deprecated
    public static final String HAPMAP3_KEY = INFO.IN_HAPMAP3;

    /** @deprecated use {@link FORMAT#HAPLOTYPE_QUALITY} */
    @Deprecated
    public static final String HAPLOTYPE_QUALITY_KEY = FORMAT.HAPLOTYPE_QUALITY;

    /** @deprecated use {@link INFO#RMS_MAPPING_QUALITY} or {@link FORMAT#RMS_MAPPING_QUALITY} */
    @Deprecated
    public static final String RMS_MAPPING_QUALITY_KEY = INFO.RMS_MAPPING_QUALITY;

    /** @deprecated use {@link INFO#MAPPING_QUALITY_ZERO_READS} */
    @Deprecated
    public static final String MAPPING_QUALITY_ZERO_KEY = INFO.MAPPING_QUALITY_ZERO_READS;

    /** @deprecated use {@link INFO#SAMPLES_WITH_DATA} */
    @Deprecated
    public static final String SAMPLE_NUMBER_KEY = INFO.SAMPLES_WITH_DATA;

    /** @deprecated use {@link FORMAT#PHASING_QUALITY} */
    @Deprecated
    public static final String PHASE_QUALITY_KEY = FORMAT.PHASING_QUALITY;

    /** @deprecated use {@link FORMAT#PHASE_SET} */
    @Deprecated
    public static final String PHASE_SET_KEY = FORMAT.PHASE_SET;

    /** @deprecated {@code RD} is not a key the VCF specification reserves, and htsjdk does not use it */
    @Deprecated
    public static final String OLD_DEPTH_KEY = "RD";

    /** @deprecated use {@link INFO#STRAND_BIAS} */
    @Deprecated
    public static final String STRAND_BIAS_KEY = INFO.STRAND_BIAS;

    /** @deprecated use {@link INFO#SOMATIC_MUTATION} */
    @Deprecated
    public static final String SOMATIC_KEY = INFO.SOMATIC_MUTATION;

    /** @deprecated use {@link INFO#VALIDATED} */
    @Deprecated
    public static final String VALIDATED_KEY = INFO.VALIDATED;

    /** @deprecated use {@link INFO#IN_1000_GENOMES} */
    @Deprecated
    public static final String THOUSAND_GENOMES_KEY = INFO.IN_1000_GENOMES;

    /** @deprecated use {@link INFO#STRUCTURAL_VARIANT_TYPE} */
    @Deprecated
    public static final String SVTYPE = INFO.STRUCTURAL_VARIANT_TYPE;

    /** Keys the VCF specification reserves for INFO fields. */
    public static final class INFO {
        // Keys defined before VCF 4.3
        /** Ancestral allele */
        public static final String ANCESTRAL_ALLELE = "AA";
        /** Allele count in genotypes, for each ALT allele */
        public static final String ALLELE_COUNT = "AC";
        /** Allele frequency for each ALT allele */
        public static final String ALLELE_FREQUENCY = "AF";
        /** Total number of alleles in called genotypes */
        public static final String ALLELE_NUMBER = "AN";
        /** RMS base quality */
        public static final String RMS_BASE_QUALITY = "BQ";
        /** Cigar string describing how to align each alternate allele to the reference allele */
        public static final String CIGAR = "CIGAR";
        /** dbSNP membership */
        public static final String IN_DBSNP = "DB";
        /** Combined depth across samples */
        public static final String COMBINED_DEPTH = "DP";
        /** End position of the record on CHROM (deprecated by VCF 4.5, kept for earlier versions) */
        public static final String END_POSITION = "END";
        /** HapMap2 membership */
        public static final String IN_HAPMAP2 = "H2";
        /** HapMap3 membership */
        public static final String IN_HAPMAP3 = "H3";
        /** RMS mapping quality */
        public static final String RMS_MAPPING_QUALITY = "MQ";
        /** Number of MAPQ == 0 reads */
        public static final String MAPPING_QUALITY_ZERO_READS = "MQ0";
        /** Number of samples with data */
        public static final String SAMPLES_WITH_DATA = "NS";
        /** Strand bias */
        public static final String STRAND_BIAS = "SB";
        /** Somatic mutation (for cancer genomics) */
        public static final String SOMATIC_MUTATION = "SOMATIC";
        /** Validated by follow-up experiment */
        public static final String VALIDATED = "VALIDATED";
        /** 1000 Genomes membership */
        public static final String IN_1000_GENOMES = "1000G";
        /** Type of structural variant (deprecated in 4.4) */
        public static final String STRUCTURAL_VARIANT_TYPE = "SVTYPE";

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
        // Keys defined before VCF 4.3
        /** Genotype */
        public static final String GENOTYPE = "GT";
        /** Filter indicating if this genotype was called */
        public static final String GENOTYPE_FILTER = "FT";
        /** Conditional genotype quality */
        public static final String GENOTYPE_QUALITY = "GQ";
        /** Genotype posterior probabilities */
        public static final String GENOTYPE_POSTERIORS = "GP";
        /** Log10-scaled genotype likelihoods */
        public static final String GENOTYPE_LIKELIHOODS = "GL";
        /** Phred-scaled genotype likelihoods rounded to the closest integer */
        public static final String PHRED_SCALED_GENOTYPE_LIKELIHOODS = "PL";
        /** Read depth for each allele */
        public static final String ALLELE_DEPTHS = "AD";
        /** Read depth */
        public static final String READ_DEPTH = "DP";
        /** Expected alternate allele counts */
        public static final String EXPECTED_ALLELE_COUNT = "EC";
        /** Haplotype quality */
        public static final String HAPLOTYPE_QUALITY = "HQ";
        /** Phasing quality */
        public static final String PHASING_QUALITY = "PQ";
        /** Phase set */
        public static final String PHASE_SET = "PS";

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
