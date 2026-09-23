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

import htsjdk.tribble.TribbleException;
import htsjdk.variant.utils.GeneralUtils;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages header lines for standard VCF <pre>INFO</pre> and <pre>FORMAT</pre> fields.
 *
 * Provides simple mechanisms for
 *  1) registering standard lines,
 *  2) looking them up, and
 *  3) adding them to headers.
 *
 * @author Mark DePristo
 * @since 6/12
 */
public class VCFStandardHeaderLines {
    /**
     * Enabling this causes us to repair header lines even if only their descriptions differ.
     */
    private static final boolean REPAIR_BAD_DESCRIPTIONS = false;

    private static Standards<VCFFormatHeaderLine> formatStandards = new Standards<>(VCFFormatHeaderLine::new);
    private static Standards<VCFInfoHeaderLine> infoStandards = new Standards<>(VCFInfoHeaderLine::new);

    /**
     * Walks over the VCF header and repairs the standard VCF header lines in it, returning a freshly
     * allocated {@link VCFHeader} with standard VCF header lines repaired as necessary.
     */
    public static VCFHeader repairStandardHeaderLines(final VCFHeader oldHeader) {
        final Set<VCFHeaderLine> newLines = new LinkedHashSet<VCFHeaderLine>(
                oldHeader.getMetaDataInInputOrder().size());
        for (VCFHeaderLine line : oldHeader.getMetaDataInInputOrder()) {
            if (VCFHeaderVersion.isFormatString(line.getKey())) {
                continue; // the version is carried over below, so a header declaring none stays that way
            }
            if (line instanceof VCFFormatHeaderLine) {
                line = formatStandards.repair((VCFFormatHeaderLine) line);
            } else if (line instanceof VCFInfoHeaderLine) {
                line = infoStandards.repair((VCFInfoHeaderLine) line);
            }

            newLines.add(line);
        }

        final VCFHeader repairedHeader = new VCFHeader(newLines, oldHeader.getGenotypeSamples());
        repairedHeader.setVCFHeaderVersion(oldHeader.getVCFHeaderVersion());
        return repairedHeader;
    }

    /**
     * Adds header lines for each of the format fields in IDs to header, returning the set of
     * {@code IDs} without standard descriptions, unless {@code throwErrorForMissing} is true, in which
     * case this situation results in a {@link TribbleException}
     */
    public static Set<String> addStandardFormatLines(
            final Set<VCFHeaderLine> headerLines, final boolean throwErrorForMissing, final Collection<String> IDs) {
        return formatStandards.addToHeader(headerLines, IDs, throwErrorForMissing);
    }

    /**
     * @see #addStandardFormatLines(java.util.Set, boolean, java.util.Collection)
     */
    public static Set<String> addStandardFormatLines(
            final Set<VCFHeaderLine> headerLines, final boolean throwErrorForMissing, final String... IDs) {
        return addStandardFormatLines(headerLines, throwErrorForMissing, Arrays.asList(IDs));
    }

    /**
     * Returns the standard format line for {@code ID}.
     * If none exists, return null or throw an exception, depending on {@code throwErrorForMissing}.
     */
    public static VCFFormatHeaderLine getFormatLine(final String ID, final boolean throwErrorForMissing) {
        return formatStandards.get(ID, throwErrorForMissing);
    }

    /**
     * Returns the standard format line for {@code ID}.
     * If none exists, throw an {@link TribbleException}
     */
    public static VCFFormatHeaderLine getFormatLine(final String ID) {
        return formatStandards.get(ID, true);
    }

    /**
     * Adds header lines for each of the info fields in {@code IDs} to header, returning the set of
     * IDs without standard descriptions, unless {@code throwErrorForMissing} is true, in which
     * case this situation results in a {@link TribbleException}.
     */
    public static Set<String> addStandardInfoLines(
            final Set<VCFHeaderLine> headerLines, final boolean throwErrorForMissing, final Collection<String> IDs) {
        return infoStandards.addToHeader(headerLines, IDs, throwErrorForMissing);
    }

    /**
     * @see #addStandardFormatLines(java.util.Set, boolean, java.util.Collection)
     */
    public static Set<String> addStandardInfoLines(
            final Set<VCFHeaderLine> headerLines, final boolean throwErrorForMissing, final String... IDs) {
        return addStandardInfoLines(headerLines, throwErrorForMissing, Arrays.asList(IDs));
    }

    /**
     * Returns the standard info line for {@code ID}.
     * If none exists, return {@code null} or throw a {@link TribbleException}, depending on {@code throwErrorForMissing}.
     */
    public static VCFInfoHeaderLine getInfoLine(final String ID, final boolean throwErrorForMissing) {
        return infoStandards.get(ID, throwErrorForMissing);
    }

    /**
     * Returns the standard info line for {@code ID}.
     * If none exists throw a {@link TribbleException}.
     */
    public static VCFInfoHeaderLine getInfoLine(final String ID) {
        return getInfoLine(ID, true);
    }

    private static void registerStandard(final VCFInfoHeaderLine line) {
        infoStandards.add(line);
    }

    private static void registerStandard(final VCFFormatHeaderLine line) {
        formatStandards.add(line);
    }

    //
    // VCF header line constants
    //
    static {
        // FORMAT lines
        registerStandard(new VCFFormatHeaderLine(VCFConstants.GENOTYPE_KEY, 1, VCFHeaderLineType.String, "Genotype"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.GENOTYPE_QUALITY_KEY, 1, VCFHeaderLineType.Integer, "Genotype Quality"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.DEPTH_KEY,
                1,
                VCFHeaderLineType.Integer,
                "Approximate read depth (reads with MQ=255 or with bad mates are filtered)"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.GENOTYPE_PL_KEY,
                VCFHeaderLineCount.G,
                VCFHeaderLineType.Integer,
                "Normalized, Phred-scaled likelihoods for genotypes as defined in the VCF specification"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.GENOTYPE_ALLELE_DEPTHS,
                VCFHeaderLineCount.R,
                VCFHeaderLineType.Integer,
                "Allelic depths for the ref and alt alleles in the order listed"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.GENOTYPE_FILTER_KEY,
                VCFHeaderLineCount.UNBOUNDED,
                VCFHeaderLineType.String,
                "Genotype-level filter"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.PHASE_SET_KEY,
                1,
                VCFHeaderLineType.Integer,
                "Phasing set (typically the position of the first variant in the set)"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.PHASE_QUALITY_KEY, 1, VCFHeaderLineType.Integer, "Read-backed phasing quality"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.PHRED_SCALED_GENOTYPE_POSTERIORS,
                VCFHeaderLineCount.G,
                VCFHeaderLineType.Integer,
                "Phred-scaled genotype posterior probabilities rounded to the closest integer"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.PHASE_SET_LIST, VCFHeaderLineCount.P, VCFHeaderLineType.String, "Phase set list"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.PHASE_SET_LIST_ORDINAL,
                VCFHeaderLineCount.P,
                VCFHeaderLineType.Integer,
                "Phase set list ordinal"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.PHASE_SET_LIST_QUALITY,
                VCFHeaderLineCount.P,
                VCFHeaderLineType.Integer,
                "Phase set list quality"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.REFERENCE_BLOCK_LENGTH,
                1,
                VCFHeaderLineType.Integer,
                "Length of <*> reference block"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.LOCAL_ALTERNATE_ALLELES,
                VCFHeaderLineCount.UNBOUNDED,
                VCFHeaderLineType.Integer,
                "1-based indices into ALT, indicating which alleles are relevant (local) for the current sample"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.LOCAL_ALLELE_DEPTHS,
                VCFHeaderLineCount.LR,
                VCFHeaderLineType.Integer,
                "Local-allele representation of AD"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.LOCAL_ALLELE_DEPTHS_FORWARD_STRAND,
                VCFHeaderLineCount.LR,
                VCFHeaderLineType.Integer,
                "Local-allele representation of ADF"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.LOCAL_ALLELE_DEPTHS_REVERSE_STRAND,
                VCFHeaderLineCount.LR,
                VCFHeaderLineType.Integer,
                "Local-allele representation of ADR"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.LOCAL_EXPECTED_ALLELE_COUNT,
                VCFHeaderLineCount.LA,
                VCFHeaderLineType.Integer,
                "Local-allele representation of EC"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.LOCAL_GENOTYPE_LIKELIHOODS,
                VCFHeaderLineCount.LG,
                VCFHeaderLineType.Float,
                "Local-allele representation of GL"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.LOCAL_GENOTYPE_POSTERIORS,
                VCFHeaderLineCount.LG,
                VCFHeaderLineType.Float,
                "Local-allele representation of GP"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.LOCAL_PHRED_SCALED_GENOTYPE_LIKELIHOODS,
                VCFHeaderLineCount.LG,
                VCFHeaderLineType.Integer,
                "Local-allele representation of PL"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.LOCAL_PHRED_SCALED_GENOTYPE_POSTERIORS,
                VCFHeaderLineCount.LG,
                VCFHeaderLineType.Integer,
                "Local-allele representation of PP"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.RMS_MAPPING_QUALITY, 1, VCFHeaderLineType.Integer, "RMS mapping quality"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.CONFIDENCE_INTERVAL_AROUND_COPY_NUMBER,
                2,
                VCFHeaderLineType.Float,
                "Confidence interval around copy number"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.COPY_NUMBER_GENOTYPE_QUALITY,
                1,
                VCFHeaderLineType.Float,
                "Copy number genotype quality"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.COPY_NUMBER_GENOTYPE_LIKELIHOODS,
                VCFHeaderLineCount.G,
                VCFHeaderLineType.Float,
                "Copy number genotype likelihood"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.COPY_NUMBER_POSTERIOR_PROBABILITIES,
                VCFHeaderLineCount.G,
                VCFHeaderLineType.Float,
                "Copy number posterior probabilities"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.NOVELTY_QUALITY,
                1,
                VCFHeaderLineType.Integer,
                "Phred style probability score that the variant is novel"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.HAPLOTYPE_ID, 1, VCFHeaderLineType.Integer, "Unique haplotype identifier"));
        registerStandard(new VCFFormatHeaderLine(
                VCFConstants.FORMAT.ANCESTRAL_HAPLOTYPE_ID,
                1,
                VCFHeaderLineType.Integer,
                "Unique identifier of ancestral haplotype"));

        // INFO lines
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.END_KEY, 1, VCFHeaderLineType.Integer, "Stop position of the interval"));
        registerStandard(new VCFInfoHeaderLine(VCFConstants.DBSNP_KEY, 0, VCFHeaderLineType.Flag, "dbSNP Membership"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.DEPTH_KEY,
                1,
                VCFHeaderLineType.Integer,
                "Approximate read depth; some reads may have been filtered"));
        registerStandard(
                new VCFInfoHeaderLine(VCFConstants.STRAND_BIAS_KEY, 1, VCFHeaderLineType.Float, "Strand Bias"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.ALLELE_FREQUENCY_KEY,
                VCFHeaderLineCount.A,
                VCFHeaderLineType.Float,
                "Allele Frequency, for each ALT allele, in the same order as listed"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.ALLELE_COUNT_KEY,
                VCFHeaderLineCount.A,
                VCFHeaderLineType.Integer,
                "Allele count in genotypes, for each ALT allele, in the same order as listed"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.ALLELE_NUMBER_KEY,
                1,
                VCFHeaderLineType.Integer,
                "Total number of alleles in called genotypes"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.MAPPING_QUALITY_ZERO_KEY,
                1,
                VCFHeaderLineType.Integer,
                "Total Mapping Quality Zero Reads"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.RMS_MAPPING_QUALITY_KEY, 1, VCFHeaderLineType.Float, "RMS Mapping Quality"));
        registerStandard(new VCFInfoHeaderLine(VCFConstants.SOMATIC_KEY, 0, VCFHeaderLineType.Flag, "Somatic event"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.ALLELE_DEPTHS,
                VCFHeaderLineCount.R,
                VCFHeaderLineType.Integer,
                "Total read depth for each allele"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.ALLELE_DEPTHS_FORWARD_STRAND,
                VCFHeaderLineCount.R,
                VCFHeaderLineType.Integer,
                "Read depth for each allele on the forward strand"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.ALLELE_DEPTHS_REVERSE_STRAND,
                VCFHeaderLineCount.R,
                VCFHeaderLineType.Integer,
                "Read depth for each allele on the reverse strand"));
        registerStandard(
                new VCFInfoHeaderLine(VCFConstants.HAPMAP2_KEY, 0, VCFHeaderLineType.Flag, "HapMap2 membership"));
        registerStandard(
                new VCFInfoHeaderLine(VCFConstants.HAPMAP3_KEY, 0, VCFHeaderLineType.Flag, "HapMap3 membership"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.IMPRECISE_STRUCTURAL_VARIANT,
                0,
                VCFHeaderLineType.Flag,
                "Imprecise structural variation"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.NOVEL_STRUCTURAL_VARIANT,
                0,
                VCFHeaderLineType.Flag,
                "Indicates a novel structural variation"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.EVENT_TYPE,
                VCFHeaderLineCount.A,
                VCFHeaderLineType.String,
                "Type of associated event"));
        registerStandard(
                new VCFInfoHeaderLine(
                        VCFConstants.INFO.STRUCTURAL_VARIANT_CLAIM,
                        VCFHeaderLineCount.A,
                        VCFHeaderLineType.String,
                        "Claim made by the structural variant call. Valid values are D, J, DJ for abundance, adjacency and both respectively"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.REPEAT_SEQUENCE_COUNT,
                VCFHeaderLineCount.A,
                VCFHeaderLineType.Integer,
                "Total number of repeat sequences in this allele"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.REPEAT_UNIT_SEQUENCE,
                VCFHeaderLineCount.UNBOUNDED,
                VCFHeaderLineType.String,
                "Repeat unit sequence of the corresponding repeat sequence"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.REPEAT_UNIT_LENGTH,
                VCFHeaderLineCount.UNBOUNDED,
                VCFHeaderLineType.Integer,
                "Repeat unit length of the corresponding repeat sequence"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.REPEAT_UNIT_COUNT,
                VCFHeaderLineCount.UNBOUNDED,
                VCFHeaderLineType.Float,
                "Repeat unit count of corresponding repeat sequence"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.REPEAT_SEQUENCE_LENGTH,
                VCFHeaderLineCount.UNBOUNDED,
                VCFHeaderLineType.Integer,
                "Total number of bases in the corresponding repeat sequence"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.CONFIDENCE_INTERVAL_AROUND_REPEAT_UNIT_COUNT,
                VCFHeaderLineCount.UNBOUNDED,
                VCFHeaderLineType.Float,
                "Confidence interval around RUC"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.CONFIDENCE_INTERVAL_AROUND_REPEAT_SEQUENCE_LENGTH,
                VCFHeaderLineCount.UNBOUNDED,
                VCFHeaderLineType.Integer,
                "Confidence interval around RB"));
        registerStandard(new VCFInfoHeaderLine(
                VCFConstants.INFO.INDIVIDUAL_REPEAT_UNIT_LENGTH,
                VCFHeaderLineCount.UNBOUNDED,
                VCFHeaderLineType.Integer,
                "Number of bases in each individual repeat unit"));
    }

    private static class Standards<T extends VCFCompoundHeaderLine> {
        private final Map<String, T> standards = new HashMap<String, T>();
        private final java.util.function.BiFunction<T, T, T> repaired;

        Standards(final java.util.function.BiFunction<T, T, T> repaired) {
            this.repaired = repaired;
        }

        public T repair(final T line) {
            final T standard = get(line.getID(), false);
            if (standard != null) {
                final boolean badCountType = line.getCountType() != standard.getCountType();
                final boolean badCount = line.isFixedCount() && !badCountType && line.getCount() != standard.getCount();
                final boolean badType = line.getType() != standard.getType();
                final boolean badDesc = !line.getDescription().equals(standard.getDescription());

                // A type mismatch is logged but not corrected: the file's declared type governs
                // how values are parsed, and silently changing it would break files that rely on
                // the historical definition (e.g. PQ declared as Float). When the type differs,
                // count repair is also skipped because the standard's count may be invalid with
                // the file's type (e.g. Number=0 for a Flag standard with an Integer file type).
                final boolean needsRepair =
                        !badType && (badCountType || badCount || (REPAIR_BAD_DESCRIPTIONS && badDesc));

                if (badType && GeneralUtils.DEBUG_MODE_ENABLED) {
                    System.err.println("Standard header line " + line.getID()
                            + " has type " + line.getType() + " but the standard is " + standard.getType()
                            + "; keeping the header's type");
                }

                if (needsRepair) {
                    if (GeneralUtils.DEBUG_MODE_ENABLED) {
                        System.err.println("Repairing standard header line for field " + line.getID() + " because"
                                + (badCountType
                                        ? " -- count types disagree; header has " + line.getCountType()
                                                + " but standard is " + standard.getCountType()
                                        : "")
                                + (badCount
                                        ? " -- counts disagree; header has " + line.getCount() + " but standard is "
                                                + standard.getCount()
                                        : "")
                                + (badDesc
                                        ? " -- descriptions disagree; header has '" + line.getDescription()
                                                + "' but standard is '" + standard.getDescription() + "'"
                                        : ""));
                    }
                    return repaired.apply(standard, line);
                } else {
                    return line;
                }
            } else {
                return line;
            }
        }

        public Set<String> addToHeader(
                final Set<VCFHeaderLine> headerLines,
                final Collection<String> IDs,
                final boolean throwErrorForMissing) {
            final Set<String> missing = new HashSet<String>();
            for (final String ID : IDs) {
                final T line = get(ID, throwErrorForMissing);
                if (line == null) missing.add(ID);
                else headerLines.add(line);
            }

            return missing;
        }

        public void add(final T line) {
            if (standards.containsKey(line.getID())) {
                throw new TribbleException("Attempting to add multiple standard header lines for ID " + line.getID());
            }
            standards.put(line.getID(), line);
        }

        public T get(final String ID, final boolean throwErrorForMissing) {
            final T x = standards.get(ID);
            if (throwErrorForMissing && x == null) {
                throw new TribbleException("Couldn't find a standard VCF header line for field " + ID);
            }
            return x;
        }
    }
}
