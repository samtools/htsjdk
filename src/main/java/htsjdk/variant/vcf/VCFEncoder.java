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
 */
public class VCFEncoder {

    public static final Charset VCF_CHARSET = StandardCharsets.UTF_8;
    private static final String QUAL_FORMAT_STRING = "%.2f";
    private static final String QUAL_FORMAT_EXTENSION_TO_TRIM = ".00";

    private final IntGenotypeFieldAccessors GENOTYPE_FIELD_ACCESSORS = new IntGenotypeFieldAccessors();

    private VCFHeader header;

    private boolean allowMissingFieldsInHeader = false;

    private boolean outputTrailingFormatFields = false;

    private VCFTextTransformer vcfTextTransformer;

    /**
     * Prepare a VCFEncoder that will encode records appropriate to the given VCF header, optionally
     * allowing missing fields in the header.
     */
    public VCFEncoder(final VCFHeader header, final boolean allowMissingFieldsInHeader, final boolean outputTrailingFormatFields) {
        if (header == null) {
            throw new NullPointerException("The VCF header must not be null.");
        }
        this.header = header;
        this.allowMissingFieldsInHeader = allowMissingFieldsInHeader;
        this.outputTrailingFormatFields = outputTrailingFormatFields;
        this.vcfTextTransformer = header.getVCFHeaderVersion() != null && header.getVCFHeaderVersion().isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)
            ? new VCFPercentEncodedTextTransformer()
            : new VCFPassThruTextTransformer();
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
     * @return the java.lang.Appendable 'vcfOutput'
     * @throws IOException
     */
    public void write(final Appendable vcfOutput, final VariantContext context) throws IOException {
        if (this.header == null) {
            throw new NullPointerException("The header field must be set on the VCFEncoder before encoding records.");
        }
        // CHROM
        vcfOutput.append(context.getContig()).append(VCFConstants.FIELD_SEPARATOR)
                // POS
                .append(String.valueOf(context.getStart())).append(VCFConstants.FIELD_SEPARATOR)
                // ID
                .append(context.getID()).append(VCFConstants.FIELD_SEPARATOR)
                // REF
                .append(context.getReference().getDisplayString()).append(VCFConstants.FIELD_SEPARATOR);

        // ALT
        if ( context.isVariant() ) {
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
        if ( !context.hasLog10PError()) {
            vcfOutput.append(VCFConstants.MISSING_VALUE_v4);
        } else {
            vcfOutput.append(formatQualValue(context.getPhredScaledQual()));
        }
        vcfOutput.append(VCFConstants.FIELD_SEPARATOR)
                // FILTER
                .append(getFilterString(context)).append(VCFConstants.FIELD_SEPARATOR);

        // INFO
        final Map<String, String> infoFields = new TreeMap<>();
        for (final Map.Entry<String, Object> field : context.getAttributes().entrySet()) {
            if (!this.header.hasInfoLine(field.getKey())) {
                fieldIsMissingFromHeaderError(context, field.getKey(), "INFO");
            }

            final String outputValue = formatVCFField(field.getValue());
            if (outputValue != null) {
                infoFields.put(field.getKey(), outputValue);
            }
        }
        writeInfoString(infoFields, vcfOutput);

        // FORMAT
        final GenotypesContext gc = context.getGenotypes();
        if (gc.isLazyWithData() && ((LazyGenotypesContext) gc).getUnparsedGenotypeData() instanceof String) {
            vcfOutput.append(VCFConstants.FIELD_SEPARATOR);
            vcfOutput.append(((LazyGenotypesContext) gc).getUnparsedGenotypeData().toString());
        } else {
            final List<String> genotypeAttributeKeys = context.calcVCFGenotypeKeys(this.header);
            if ( !genotypeAttributeKeys.isEmpty()) {
                for (final String format : genotypeAttributeKeys) {
                    if (!this.header.hasFormatLine(format)) {
                        fieldIsMissingFromHeaderError(context, format, "FORMAT");
                    }
                }
                final String genotypeFormatString = ParsingUtils.join(VCFConstants.GENOTYPE_FIELD_SEPARATOR, genotypeAttributeKeys);

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

    @SuppressWarnings("rawtypes")
    String formatVCFField(final Object val) {
        final String result;
        if (val == null) {
            result = VCFConstants.MISSING_VALUE_v4;
        } else if (val instanceof Double) {
            result = formatVCFDouble((Double) val);
        } else if (val instanceof Boolean) {
            result = (Boolean) val ? "" : null; // empty string for true, null for false
        } else if (val instanceof List) {
            result = formatVCFField(((List) val).toArray());
        } else if (val.getClass().isArray()) {
            final int length = Array.getLength(val);
            if (length == 0) {
                return formatVCFField(null);
            }
            final StringBuilder sb = new StringBuilder(
                formatVCFField(Array.get(val, 0)));
            for (int i = 1; i < length; i++) {
                sb.append(',');
                sb.append(formatVCFField(Array.get(val, i)));
            }
            result = sb.toString();
        } else {
            result = vcfTextTransformer.encodeText(val.toString());
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
    public void addGenotypeData(final VariantContext vc, final Map<Allele, String> alleleMap, final List<String> genotypeFormatKeys, final StringBuilder builder) {
        try {
            appendGenotypeData(vc,alleleMap,genotypeFormatKeys,builder);
        } catch (final IOException err) {
            throw new RuntimeIOException("addGenotypeData failed",err);
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
    private void appendGenotypeData(final VariantContext vc, final Map<Allele, String> alleleMap, final List<String> genotypeFormatKeys, final Appendable vcfoutput) throws IOException {
        final int ploidy = vc.getMaxPloidy(2);

        for (final String sample : this.header.getGenotypeSamples()) {
            vcfoutput.append(VCFConstants.FIELD_SEPARATOR);

            Genotype g = vc.getGenotype(sample);
            if (g == null) {
                g = GenotypeBuilder.createMissing(sample, ploidy);
            }

            final List<String> attrs = new ArrayList<>(genotypeFormatKeys.size());
            for (final String field : genotypeFormatKeys) {
                if (field.equals(VCFConstants.GENOTYPE_KEY)) {
                    if (!g.isAvailable()) {
                        throw new IllegalStateException("GTs cannot be missing for some samples if they are available for others in the record");
                    }

                    writeAllele(g.getAllele(0), alleleMap, vcfoutput);
                    for (int i = 1; i < g.getPloidy(); i++) {
                        vcfoutput.append(g.isPhased() ? VCFConstants.PHASED : VCFConstants.UNPHASED);
                        writeAllele(g.getAllele(i), alleleMap, vcfoutput);
                    }
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
                            Object val = g.hasExtendedAttribute(field) ? g.getExtendedAttribute(field) : VCFConstants.MISSING_VALUE_v4;
                            outputValue = formatVCFField(val);
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
                if ( i > 0 || genotypeFormatKeys.contains(VCFConstants.GENOTYPE_KEY)) {
                    vcfoutput.append(VCFConstants.GENOTYPE_FIELD_SEPARATOR);
                }
                vcfoutput.append(attrs.get(i));
            }
        }
    }

    /*
     * Create the info string; assumes that no values are null
     */
    private void writeInfoString(final Map<String, String> infoFields, final Appendable vcfoutput) throws IOException {
        if ( infoFields.isEmpty() ) {
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

            if ( ! entry.getValue().isEmpty()) {
                final VCFInfoHeaderLine metaData = this.header.getInfoHeaderLine(entry.getKey());
                if ( metaData == null || metaData.getCountType() != VCFHeaderLineCount.INTEGER || metaData.getCount() != 0 ) {
                    vcfoutput.append('=');
                    vcfoutput.append(entry.getValue());
                }
            }
        }
    }

    public Map<Allele, String> buildAlleleStrings(final VariantContext vc) {
        final Map<Allele, String> alleleMap = new HashMap<Allele, String>(vc.getAlleles().size() + 1);
        alleleMap.put(Allele.NO_CALL, VCFConstants.EMPTY_ALLELE); // convenience for lookup

        final List<Allele> alleles = vc.getAlleles();
        for (int i = 0; i < alleles.size(); i++) {
            alleleMap.put(alleles.get(i), String.valueOf(i));
        }

        return alleleMap;
    }

    private static void writeAllele(final Allele allele, final Map<Allele, String> alleleMap, final Appendable vcfOutput) throws IOException {
        final String encoding = alleleMap.get(allele);
        if (encoding == null) {
            throw new RuntimeException("Allele " + allele + " is not an allele in the variant context");
        }
        vcfOutput.append(encoding);
    }
}
