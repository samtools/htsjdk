package htsjdk.variant.vcf;

import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.GenotypesContext;
import htsjdk.variant.variantcontext.LazyGenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.IntGenotypeFieldAccessors;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Functions specific to encoding VCF records.
 *
 * <p>When the output version is 4.3 or later, INFO and FORMAT {@code String} and {@code Character} values in record
 * bodies are percent-encoded, each element of a list on its own. Genotypes still held as the text they were read
 * from are written as that text only when nothing about it would have to change: the source and the output are
 * both before 4.3 or both 4.3 or later, so the text needs neither encoding nor decoding, and a source of 4.4 or
 * later, whose genotypes may start with a phase indicator, goes to an output of 4.4 or later. Otherwise the
 * genotypes are decoded and encoded like any other.
 */
public class VCFEncoder {

    /** The encoding used for VCF files: UTF-8, as required by VCF 4.3+. */
    public static final Charset VCF_CHARSET = StandardCharsets.UTF_8;

    private static final String QUAL_FORMAT_STRING = "%.2f";
    private static final String QUAL_FORMAT_EXTENSION_TO_TRIM = ".00";

    private final IntGenotypeFieldAccessors GENOTYPE_FIELD_ACCESSORS = new IntGenotypeFieldAccessors();

    /** How the values of one INFO or FORMAT key are percent-encoded, decided from its header line. */
    enum ValueEncoding {
        /** Nothing: the output is before 4.3, or the values are {@code Integer}, {@code Float} or {@code Flag}. */
        NONE,
        /** One value: every special character in it is encoded, a comma included. */
        SCALAR,
        /**
         * A list of values: each element is encoded on its own and the commas between them are kept. A
         * {@code String} value of such a key is taken to be a list already joined with commas, as the reader
         * leaves a FORMAT value, so its commas are kept too.
         */
        LIST
    }

    private VCFHeader header;
    private final boolean percentEncode;
    private final boolean leadingPhaseAllowed;
    private final boolean outputIs45Plus;
    private final boolean outputHasLaaFormat;

    private boolean allowMissingFieldsInHeader = false;

    private boolean outputTrailingFormatFields = false;

    /**
     * Prepare a VCFEncoder that will encode records appropriate to the given VCF header, optionally
     * allowing missing fields in the header. Uses the header's version with a floor of 4.2.
     */
    public VCFEncoder(
            final VCFHeader header,
            final boolean allowMissingFieldsInHeader,
            final boolean outputTrailingFormatFields) {
        this(header, allowMissingFieldsInHeader, outputTrailingFormatFields, resolveVersion(header));
    }

    /**
     * Prepare a VCFEncoder that will encode records using the given VCF version.
     *
     * @param header the VCF header
     * @param allowMissingFieldsInHeader if true, missing header lines are not an error
     * @param outputTrailingFormatFields if true, trailing missing FORMAT fields are kept
     * @param version the VCF version to encode for
     */
    public VCFEncoder(
            final VCFHeader header,
            final boolean allowMissingFieldsInHeader,
            final boolean outputTrailingFormatFields,
            final VCFHeaderVersion version) {
        if (header == null) {
            throw new NullPointerException("The VCF header must not be null.");
        }
        if (version == null) {
            throw new NullPointerException("The VCF version must not be null.");
        }
        this.header = header;
        this.allowMissingFieldsInHeader = allowMissingFieldsInHeader;
        this.outputTrailingFormatFields = outputTrailingFormatFields;
        this.percentEncode = version.percentEncodesText();
        this.leadingPhaseAllowed = version.leadingPhaseAllowed();
        this.outputIs45Plus = version.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_5);
        this.outputHasLaaFormat = header.hasFormatLine(VCFConstants.LAA_KEY);
    }

    /**
     * Resolves the output version for a header: returns the header's version when it is 4.2 or later,
     * or 4.2 when the header declares none or an older version.
     */
    public static VCFHeaderVersion resolveVersion(final VCFHeader header) {
        if (header == null) {
            throw new NullPointerException("The VCF header must not be null.");
        }
        final VCFHeaderVersion v = header.getVCFHeaderVersion();
        if (v == null || v.isOlderThan(VCFHeaderVersion.VCF4_2)) {
            return VCFHeaderVersion.VCF4_2;
        }
        return v;
    }

    /**
     * @deprecated since 10/24/13 use the constructor
     */
    @Deprecated
    public void setVCFHeader(final VCFHeader header) {
        this.header = header;
    }

    /**
     * @deprecated since 10/24/13 use the constructor
     */
    @Deprecated
    public void setAllowMissingFieldsInHeader(final boolean allow) {
        this.allowMissingFieldsInHeader = allow;
    }

    /**
     * encodes a {@link VariantContext} as a VCF line
     *
     * Depending on the use case it may be more efficient to {@link #write(Appendable, VariantContext)} directly
     * instead of creating an intermediate string.
     *
     * @return the VCF line
     */
    public String encode(final VariantContext context) {
        try {
            final StringBuilder stringBuilder = new StringBuilder(1000);
            write(stringBuilder, context);
            return stringBuilder.toString();
        } catch (final IOException error) {
            throw new RuntimeIOException("Cannot encode variant", error);
        }
    }

    /**
     * encodes a {@link VariantContext} context as VCF, and writes it directly to an {@link Appendable}
     *
     * This may be more efficient than calling {@link #encode(VariantContext)} and then writing the result since it
     * avoids creating an intermediate string.
     *
     * @param vcfOutput the {@link Appendable} to write to
     * @param context the variant
     * @throws IOException
     */
    public void write(final Appendable vcfOutput, final VariantContext context) throws IOException {
        if (this.header == null) {
            throw new NullPointerException("The header field must be set on the VCFEncoder before encoding records.");
        }
        // CHROM
        vcfOutput
                .append(context.getContig())
                .append(VCFConstants.FIELD_SEPARATOR)
                // POS
                .append(String.valueOf(context.getStart()))
                .append(VCFConstants.FIELD_SEPARATOR)
                // ID
                .append(context.getID())
                .append(VCFConstants.FIELD_SEPARATOR)
                // REF
                .append(context.getReference().getDisplayString())
                .append(VCFConstants.FIELD_SEPARATOR);

        // ALT
        if (context.isVariant()) {
            Allele altAllele = context.getAlternateAllele(0);
            String alt = altAllele.getDisplayString();
            vcfOutput.append(alt);

            for (int i = 1; i < context.getAlternateAlleles().size(); i++) {
                altAllele = context.getAlternateAllele(i);
                alt = altAllele.getDisplayString();
                vcfOutput.append(',');
                vcfOutput.append(alt);
            }
        } else {
            vcfOutput.append(VCFConstants.EMPTY_ALTERNATE_ALLELE_FIELD);
        }

        vcfOutput.append(VCFConstants.FIELD_SEPARATOR);

        // QUAL
        if (!context.hasLog10PError()) {
            vcfOutput.append(VCFConstants.MISSING_VALUE_v4);
        } else {
            vcfOutput.append(formatQualValue(context.getPhredScaledQual()));
        }
        vcfOutput
                .append(VCFConstants.FIELD_SEPARATOR)
                // FILTER
                .append(getFilterString(context))
                .append(VCFConstants.FIELD_SEPARATOR);

        // INFO
        final Map<String, String> infoFields = new TreeMap<>();
        for (final Map.Entry<String, Object> field : context.getAttributes().entrySet()) {
            final VCFInfoHeaderLine infoLine = this.header.getInfoHeaderLine(field.getKey());
            if (infoLine == null) {
                fieldIsMissingFromHeaderError(context, field.getKey(), "INFO");
            }

            final String outputValue = formatVCFField(field.getValue(), valueEncoding(infoLine));
            if (outputValue != null) {
                infoFields.put(field.getKey(), outputValue);
            }
        }
        writeInfoString(infoFields, vcfOutput);

        // FORMAT
        final GenotypesContext gc = context.getGenotypes();
        if (gc.isLazyWithData()
                && ((LazyGenotypesContext) gc).getUnparsedGenotypeData() instanceof String
                && canPassThroughLazyGenotypes((LazyGenotypesContext) gc)) {
            vcfOutput.append(VCFConstants.FIELD_SEPARATOR);
            vcfOutput.append(
                    ((LazyGenotypesContext) gc).getUnparsedGenotypeData().toString());
        } else {
            final List<String> genotypeAttributeKeys = context.calcVCFGenotypeKeys(this.header);
            if (!genotypeAttributeKeys.isEmpty()) {
                for (final String format : genotypeAttributeKeys) {
                    if (!this.header.hasFormatLine(format)) {
                        fieldIsMissingFromHeaderError(context, format, "FORMAT");
                    }
                }
                final String genotypeFormatString =
                        ParsingUtils.join(VCFConstants.GENOTYPE_FIELD_SEPARATOR, genotypeAttributeKeys);

                vcfOutput.append(VCFConstants.FIELD_SEPARATOR);
                vcfOutput.append(genotypeFormatString);

                final Map<Allele, String> alleleStrings = buildAlleleStrings(context);
                appendGenotypeData(context, alleleStrings, genotypeAttributeKeys, vcfOutput);
            }
        }
    }

    VCFHeader getVCFHeader() {
        return this.header;
    }

    boolean getAllowMissingFieldsInHeader() {
        return this.allowMissingFieldsInHeader;
    }

    /**
     * Whether genotype text can be written as it was read, without decoding it: when the source and the output are
     * on the same side of 4.3, where percent-encoding begins; when the output can express a leading phase indicator
     * if the source (4.4 or later) could carry one; and, if the header defines LAA, when both are on the same side of
     * 4.5, from which LAA must follow GT. Text whose source version is not known is written as it is.
     */
    private boolean canPassThroughLazyGenotypes(final LazyGenotypesContext gc) {
        final VCFHeaderVersion source = gc.getHeaderVersion();
        if (source == null) {
            return true;
        }
        if (source.percentEncodesText() != percentEncode) {
            return false;
        }
        if (!leadingPhaseAllowed && source.leadingPhaseAllowed()) {
            return false;
        }
        if (outputHasLaaFormat && source.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_5) != outputIs45Plus) {
            return false;
        }
        return true;
    }

    /**
     * How the values of a key are percent-encoded, from its header line. A key the header does not declare (allowed
     * with allowMissingFieldsInHeader) is treated as a list, so that its commas stay the delimiters the reader will
     * take them for.
     */
    private ValueEncoding valueEncoding(final VCFCompoundHeaderLine line) {
        if (!percentEncode) {
            return ValueEncoding.NONE;
        }
        if (line == null) {
            return ValueEncoding.LIST;
        }
        switch (line.getType()) {
            case Integer:
            case Float:
            case Flag:
                return ValueEncoding.NONE;
            default:
                return line.isFixedCount() && line.getCount() == 1 ? ValueEncoding.SCALAR : ValueEncoding.LIST;
        }
    }

    private String getFilterString(final VariantContext vc) {
        if (vc.isFiltered()) {
            for (final String filter : vc.getFilters()) {
                if (!this.header.hasFilterLine(filter)) {
                    fieldIsMissingFromHeaderError(vc, filter, "FILTER");
                }
            }

            return ParsingUtils.join(";", ParsingUtils.sortList(vc.getFilters()));
        } else {
            return vc.filtersWereApplied() ? VCFConstants.PASSES_FILTERS_v4 : VCFConstants.UNFILTERED;
        }
    }

    private static String formatQualValue(final double qual) {
        String s = String.format(Locale.US, QUAL_FORMAT_STRING, qual);
        if (s.endsWith(QUAL_FORMAT_EXTENSION_TO_TRIM)) {
            s = s.substring(0, s.length() - QUAL_FORMAT_EXTENSION_TO_TRIM.length());
        }
        return s;
    }

    private void fieldIsMissingFromHeaderError(final VariantContext vc, final String id, final String field) {
        if (!allowMissingFieldsInHeader) {
            throw new IllegalStateException("Key " + id + " found in VariantContext field " + field
                    + " at " + vc.getContig() + ":" + vc.getStart()
                    + " but this key isn't defined in the VCFHeader.  We require all VCFs to have"
                    + " complete VCF headers by default.");
        }
    }

    static String formatVCFField(final Object val) {
        return formatVCFField(val, ValueEncoding.NONE);
    }

    /**
     * Formats an INFO or FORMAT value as VCF text: a list or array as its elements joined with commas, a Double
     * as {@link #formatVCFDouble}, a Boolean as an empty string (true) or null (false), null as the missing value,
     * and anything else as its toString, percent-encoded as the key's {@link ValueEncoding} says. When nothing
     * needs encoding a String value is returned as it is.
     */
    @SuppressWarnings("rawtypes")
    static String formatVCFField(final Object val, final ValueEncoding encoding) {
        final String result;
        if (val == null) {
            result = VCFConstants.MISSING_VALUE_v4;
        } else if (val instanceof Double) {
            result = formatVCFDouble((Double) val);
        } else if (val instanceof Boolean) {
            result = (Boolean) val ? "" : null; // empty string for true, null for false
        } else if (val instanceof List) {
            result = formatVCFField(((List) val).toArray(), encoding);
        } else if (val.getClass().isArray()) {
            final int length = Array.getLength(val);
            if (length == 0) {
                return formatVCFField(null, encoding);
            }
            // each element is one value: a comma inside it is literal, the commas between elements are delimiters
            final ValueEncoding elementEncoding =
                    encoding == ValueEncoding.NONE ? ValueEncoding.NONE : ValueEncoding.SCALAR;
            final StringBuilder sb = new StringBuilder(formatVCFField(Array.get(val, 0), elementEncoding));
            for (int i = 1; i < length; i++) {
                sb.append(',');
                sb.append(formatVCFField(Array.get(val, i), elementEncoding));
            }
            result = sb.toString();
        } else {
            final String text = val.toString();
            switch (encoding) {
                case SCALAR:
                    result = VCFPercentEncodedTextTransformer.percentEncode(text);
                    break;
                case LIST:
                    result = VCFPercentEncodedTextTransformer.percentEncodeJoinedList(text);
                    break;
                default:
                    result = text;
            }
        }

        return result;
    }

    /**
     * Takes a double value and pretty prints it to a String for display
     * <p>
     * Large doubles =&gt; gets %.2f style formatting
     * Doubles &lt; 1 / 10 but &gt; 1/100 =&gt; get %.3f style formatting
     * Double &lt; 1/100 =&gt; %.3e formatting
     *
     * @param d
     * @return
     */
    public static String formatVCFDouble(final double d) {
        final String format;
        if (d < 1) {
            if (d < 0.01) {
                if (Math.abs(d) >= 1e-20) {
                    format = "%.3e";
                } else {
                    // return a zero format
                    return "0.00";
                }
            } else {
                format = "%.3f";
            }
        } else {
            format = "%.2f";
        }

        return String.format(Locale.US, format, d);
    }

    static int countOccurrences(final char c, final String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            count += s.charAt(i) == c ? 1 : 0;
        }
        return count;
    }

    static boolean isMissingValue(final String s) {
        // we need to deal with the case that it's a list of missing values
        return (countOccurrences(VCFConstants.MISSING_VALUE_v4.charAt(0), s) + countOccurrences(',', s) == s.length());
    }

    /*
     * Add the genotype data
     */
    public void addGenotypeData(
            final VariantContext vc,
            final Map<Allele, String> alleleMap,
            final List<String> genotypeFormatKeys,
            final StringBuilder builder) {
        try {
            appendGenotypeData(vc, alleleMap, genotypeFormatKeys, builder);
        } catch (final IOException err) {
            throw new RuntimeIOException("addGenotypeData failed", err);
        }
    }

    /**
     * Add the genotype Data to a java.lang.Appendable
     * @param vc the variant
     * @param alleleMap
     * @param genotypeFormatKeys
     * @param vcfoutput VCF output
     * @throws IOException
     */
    private void appendGenotypeData(
            final VariantContext vc,
            final Map<Allele, String> alleleMap,
            final List<String> genotypeFormatKeys,
            final Appendable vcfoutput)
            throws IOException {
        final int ploidy = vc.getMaxPloidy(2);
        final int nKeys = genotypeFormatKeys.size();

        // how each key's values are percent-encoded, decided once per record rather than once per sample
        final ValueEncoding[] encodings = percentEncode ? new ValueEncoding[nKeys] : null;
        if (encodings != null) {
            for (int k = 0; k < nKeys; k++) {
                encodings[k] = valueEncoding(this.header.getFormatHeaderLine(genotypeFormatKeys.get(k)));
            }
        }

        for (final String sample : this.header.getGenotypeSamples()) {
            vcfoutput.append(VCFConstants.FIELD_SEPARATOR);

            Genotype g = vc.getGenotype(sample);
            if (g == null) {
                g = GenotypeBuilder.createMissing(sample, ploidy);
            }

            final List<String> attrs = new ArrayList<>(nKeys);
            for (int k = 0; k < nKeys; k++) {
                final String field = genotypeFormatKeys.get(k);
                if (field.equals(VCFConstants.GENOTYPE_KEY)) {
                    if (!g.isAvailable()) {
                        throw new IllegalStateException(
                                "GTs cannot be missing for some samples if they are available for others in the record");
                    }

                    writeGtField(alleleMap, vcfoutput, g, leadingPhaseAllowed);
                    continue;

                } else {
                    final String outputValue;
                    if (field.equals(VCFConstants.GENOTYPE_FILTER_KEY)) {
                        outputValue = g.isFiltered() ? g.getFilters() : VCFConstants.PASSES_FILTERS_v4;
                    } else {
                        final IntGenotypeFieldAccessors.Accessor accessor = GENOTYPE_FIELD_ACCESSORS.getAccessor(field);
                        if (accessor != null) {
                            final int[] intValues = accessor.getValues(g);
                            if (intValues == null) {
                                outputValue = VCFConstants.MISSING_VALUE_v4;
                            } else if (intValues.length == 1) { // fast path
                                outputValue = Integer.toString(intValues[0]);
                            } else {
                                final StringBuilder sb = new StringBuilder();
                                sb.append(intValues[0]);
                                for (int i = 1; i < intValues.length; i++) {
                                    sb.append(',');
                                    sb.append(intValues[i]);
                                }
                                outputValue = sb.toString();
                            }
                        } else {
                            final Object val = g.hasExtendedAttribute(field)
                                    ? g.getExtendedAttribute(field)
                                    : VCFConstants.MISSING_VALUE_v4;
                            outputValue = formatVCFField(val, encodings == null ? ValueEncoding.NONE : encodings[k]);
                        }
                    }

                    if (outputValue != null) {
                        attrs.add(outputValue);
                    }
                }
            }

            // strip off trailing missing values
            if (!outputTrailingFormatFields) {
                for (int i = attrs.size() - 1; i >= 0; i--) {
                    if (isMissingValue(attrs.get(i))) {
                        attrs.remove(i);
                    } else {
                        break;
                    }
                }
            }

            for (int i = 0; i < attrs.size(); i++) {
                if (i > 0 || genotypeFormatKeys.contains(VCFConstants.GENOTYPE_KEY)) {
                    vcfoutput.append(VCFConstants.GENOTYPE_FIELD_SEPARATOR);
                }
                vcfoutput.append(attrs.get(i));
            }
        }
    }

    /**
     * write the encoded GT field for a Genotype, for a version of VCF before 4.4
     * @param alleleMap a mapping of Allele to GT allele value (from {@link #buildAlleleStrings(VariantContext)})
     * @param vcfoutput the appendable to write to, to avoid inefficiency due to string copying
     * @param g the genotoype to encode
     * @throws IOException if appending fails with an IOException
     * @throws IllegalStateException if the genotype {@link Genotype#needsLeadingPhaseIndicator() needs a leading
     *     phase indicator}, which those versions do not have
     */
    public static void writeGtField(final Map<Allele, String> alleleMap, final Appendable vcfoutput, final Genotype g)
            throws IOException {
        writeGtField(alleleMap, vcfoutput, g, false);
    }

    /**
     * write the encoded GT field for a Genotype
     * @param alleleMap a mapping of Allele to GT allele value (from {@link #buildAlleleStrings(VariantContext)})
     * @param vcfoutput the appendable to write to, to avoid inefficiency due to string copying
     * @param g the genotoype to encode
     * @param leadingPhaseIndicatorAllowed whether the VCF being written is 4.4 or later, where a GT may start with a
     *     phase indicator ({@code |0/1})
     * @throws IOException if appending fails with an IOException
     * @throws IllegalStateException if the genotype needs a leading phase indicator and it is not allowed: dropping
     *     it would silently change the first allele's phase
     */
    public static void writeGtField(
            final Map<Allele, String> alleleMap,
            final Appendable vcfoutput,
            final Genotype g,
            final boolean leadingPhaseIndicatorAllowed)
            throws IOException {
        if (g.needsLeadingPhaseIndicator()) {
            if (!leadingPhaseIndicatorAllowed) {
                throw new IllegalStateException("The genotype of sample " + g.getSampleName() + " ("
                        + g.getGenotypeString()
                        + ") gives its first allele a phase that only VCF 4.4 and later can express");
            }
            vcfoutput.append(g.isAllelePhased(0) ? VCFConstants.PHASED : VCFConstants.UNPHASED);
        }
        writeAllele(g.getAllele(0), alleleMap, vcfoutput);
        for (int i = 1; i < g.getPloidy(); i++) {
            vcfoutput.append(g.isAllelePhased(i) ? VCFConstants.PHASED : VCFConstants.UNPHASED);
            writeAllele(g.getAllele(i), alleleMap, vcfoutput);
        }
    }

    /*
     * Create the info string; assumes that no values are null
     */
    private void writeInfoString(final Map<String, String> infoFields, final Appendable vcfoutput) throws IOException {
        if (infoFields.isEmpty()) {
            vcfoutput.append(VCFConstants.EMPTY_INFO_FIELD);
            return;
        }

        boolean isFirst = true;
        for (final Map.Entry<String, String> entry : infoFields.entrySet()) {
            if (isFirst) {
                isFirst = false;
            } else {
                vcfoutput.append(VCFConstants.INFO_FIELD_SEPARATOR);
            }

            vcfoutput.append(entry.getKey());

            if (!entry.getValue().isEmpty()) {
                final VCFInfoHeaderLine metaData = this.header.getInfoHeaderLine(entry.getKey());
                if (metaData == null
                        || metaData.getCountType() != VCFHeaderLineCount.INTEGER
                        || metaData.getCount() != 0) {
                    vcfoutput.append('=');
                    vcfoutput.append(entry.getValue());
                }
            }
        }
    }

    /**
     * Easy way to generate the GT field for a Genotype.  This will be less efficient than using
     * {@link #writeGtField(Map, Appendable, Genotype)} because of redundant Map initializations
     * @param vc a VariantContext which must contain g or the results are likely to be incorrect
     * @param g a Genotype in vc
     * @return a String containing the encoding of the GT field of g
     */
    public static String encodeGtField(VariantContext vc, Genotype g) {
        final StringBuilder builder = new StringBuilder();
        try {
            writeGtField(VCFEncoder.buildAlleleStrings(vc), builder, g);
        } catch (final IOException e) {
            throw new RuntimeException("Somehow we failed to append to a StringBuilder, this shouldn't happen.", e);
        }
        return builder.toString();
    }

    /**
     * return a Map containing Allele -> String(allele position) for all Alleles in VC
     * (as well as NO_CALL)
     * ex: A,T,TC -> { A:0, T:1, TC:2, NO_CALL:EMPTY_ALLELE}
     * This may be efficient when looking up values for many genotypes per VC
     */
    public static Map<Allele, String> buildAlleleStrings(final VariantContext vc) {
        final Map<Allele, String> alleleMap = new HashMap<>(vc.getAlleles().size() + 1);
        alleleMap.put(Allele.NO_CALL, VCFConstants.EMPTY_ALLELE); // convenience for lookup

        final List<Allele> alleles = vc.getAlleles();
        for (int i = 0; i < alleles.size(); i++) {
            alleleMap.put(alleles.get(i), String.valueOf(i));
        }

        return alleleMap;
    }

    private static void writeAllele(
            final Allele allele, final Map<Allele, String> alleleMap, final Appendable vcfOutput) throws IOException {
        final String encoding = alleleMap.get(allele);
        if (encoding == null) {
            throw new RuntimeException("Allele " + allele + " is not an allele in the variant context");
        }
        vcfOutput.append(encoding);
    }
}
