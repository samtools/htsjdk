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

import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;
import htsjdk.utils.ValidationUtils;
import htsjdk.variant.variantcontext.GenotypeLikelihoods;
import htsjdk.variant.variantcontext.VariantContext;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for compound header lines, which include INFO lines and FORMAT lines.
 *
 * Compound header lines are distinguished only in that are required to have TYPE and NUMBER attributes
 * (VCFHeaderLineCount, a VCFHeaderLineType, and a count).
 */
public abstract class VCFCompoundHeaderLine extends VCFSimpleHeaderLine {
    private static final long serialVersionUID = 1L;
    protected static final Log logger = Log.getInstance(VCFCompoundHeaderLine.class);

    // regex pattern corresponding to legal info/format field keys
    protected static final Pattern VALID_HEADER_ID_PATTERN = Pattern.compile("^[A-Za-z_][0-9A-Za-z_.]*$");
    protected static final String UNBOUND_DESCRIPTION = "Not provided in original VCF header";

    protected static final String NUMBER_ATTRIBUTE = "Number";
    protected static final String TYPE_ATTRIBUTE = "Type";

    // List of expected tags that have a predefined order (used by the parser to verify order only). The
    // header line class itself should verify that all required tags are present.
    protected static final List<String> expectedTagOrder = Collections.unmodifiableList(
            new ArrayList<String>(4) {{
            add(ID_ATTRIBUTE);
            add(NUMBER_ATTRIBUTE);
            add(TYPE_ATTRIBUTE);
            add(DESCRIPTION_ATTRIBUTE);
        }}
    );

    // immutable, cached binary representations of compound header line attributes
    private final VCFHeaderLineType type;
    private final VCFHeaderLineCount countType;
    private final int count;

    /**
     * create a VCF compound header line with count type = VCFHeaderLineCount.INTEGER
     *
     * @param key          the key (header line type) for this header line
     * @param headerLineID the is or this header line
     * @param count        the count for this header line, sets countType type as VCFHeaderLineCount.INTEGER
     * @param type         the type for this header line
     * @param description  the description for this header line
     */
    protected VCFCompoundHeaderLine(
            final String key,
            final String headerLineID,
            final int count,
            final VCFHeaderLineType type,
            final String description)
    {
        this(key, createAttributeMap(headerLineID, VCFHeaderLineCount.INTEGER, count, type, description), VCFHeader.DEFAULT_VCF_VERSION);
    }

    /**
     * create a VCF compound header line
     *
     * @param key          the key (header line type) for this header line
     * @param headerLineID the id for this header line
     * @param countType    the count type for this header line
     * @param type         the type for this header line
     * @param description  the description for this header line
     */
    protected VCFCompoundHeaderLine(
            final String key,
            final String headerLineID,
            final VCFHeaderLineCount countType,
            final VCFHeaderLineType type,
            final String description) {
        this(key, createAttributeMap(headerLineID, countType, VCFHeaderLineCount.VARIABLE_COUNT, type, description), VCFHeader.DEFAULT_VCF_VERSION);
    }

    /**
     * create a VCF compound header line from an attribute map
     *
     * @param key       the key (header line type) for this header line
     * @param mapping   the header line attribute map
     * @param vcfVersion   the VCF header version. This may be null, in which case
     */
    protected VCFCompoundHeaderLine(final String key, final Map<String, String> mapping, final VCFHeaderVersion vcfVersion) {
        super(key, mapping);
        ValidationUtils.nonNull(vcfVersion);

        this.type = decodeLineType(getGenericFieldValue(TYPE_ATTRIBUTE));
        final String countString = getGenericFieldValue(NUMBER_ATTRIBUTE);
        this.countType = decodeCountType(countString, vcfVersion);
        this.count = decodeCount(countString, this.countType);
        validateForVersionOrThrow(vcfVersion);
    }

    /**
     * Return the description for this header line.
     * @return the header line's description
     */
    public String getDescription() {
        final String description = getGenericFieldValue(DESCRIPTION_ATTRIBUTE);
        return description == null ?
                UNBOUND_DESCRIPTION :
                description;
    }

    public VCFHeaderLineType getType() { return type; }

    public VCFHeaderLineCount getCountType() { return countType; }

    /**
     * @return true if this header line has a fixed integer count type ({@link #getCountType()}
     * equals {@link VCFHeaderLineCount#INTEGER})
     */
    public boolean isFixedCount() { return countType.isFixedCount(); }

    /**
     * @return the integer count for this header line if the header has a fixed integer
     * count type ({@link #isFixedCount()} is true). A TribbleException is thrown if the
     * header line does not have a fixed integer count type ({@link #getCountType()} equals
     * {@link VCFHeaderLineCount#INTEGER}).
     *
     * @throws TribbleException if the {@link VCFHeaderLineCount} is not a fixed integer
     */
    public int getCount() {
        if (!isFixedCount()) {
            throw new TribbleException("Header line count request when count type is not an integer");
        }
        return count;
    }

    public String getSource() {
        return getGenericFieldValue(SOURCE_ATTRIBUTE);
    }

    public String getVersion() {
        return getGenericFieldValue(VERSION_ATTRIBUTE);
    }

    @Override
    public Optional<VCFValidationFailure<VCFHeaderLine>> validateForVersion(final VCFHeaderVersion vcfTargetVersion) {
        // The VCF 4.3 spec does not phrase this restriction as one on the form of the ID value of
        // INFO/FORMAT lines but instead on the INFO/FORMAT fixed field key values (c.f. section 1.6.1).
        // However, the key values correspond to INFO/FORMAT header lines defining the attribute and its type,
        // so we do the validation here
        if (vcfTargetVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
            final Optional<String> validationFailure = validateID(getID());
            if (validationFailure.isPresent()) {
                if (VCFUtils.isStrictVCFVersionValidation()) {
                    return Optional.of(
                            new VCFValidationFailure<>(vcfTargetVersion, this, validationFailure.get()));
                } else {
                    // warn for older versions - this line can't be used as a v4.3 line
                    logger.warn(validationFailure.get());
                }
            }
        }

        return super.validateForVersion(vcfTargetVersion);
    }

    /**
     * @param id the candidate ID
     * @return an Optional error message indicating the reason for the validation failure
     */
    @Override
    protected Optional<String> validateID(final String id) {
        return VALID_HEADER_ID_PATTERN.matcher(id).matches()
            ? super.validateID(id)
            : Optional.of(String.format("ID: %s does not match header line ID regex: %s", id, VALID_HEADER_ID_PATTERN));
    }

    /**
     * Get the number of values expected for this header field, given the properties of VariantContext vc
     *
     * If the count is a fixed count, return that.  For example, a field with size of 1 in the header returns 1
     * If the count is of type A, return vc.getNAlleles - 1
     * If the count is of type R, return vc.getNAlleles
     * If the count is of type G, return the expected number of genotypes given the number of alleles in VC and the
     *   max ploidy among all samples.  Note that if the max ploidy of the VC is 0 (there's no GT information
     *   at all, then implicitly assume diploid samples when computing G values.
     * If the count is UNBOUNDED return -1
     *
     * @param vc
     * @return
     */
    public int getCount(final VariantContext vc) {
        switch (countType) {
            case INTEGER:
                return count;
            case UNBOUNDED:
                return -1;
            case A:
                return vc.getNAlleles() - 1;
            case R:
                return vc.getNAlleles();
            case G:
                final int ploidy = vc.getMaxPloidy(2);
                return GenotypeLikelihoods.numLikelihoods(vc.getNAlleles(), ploidy);
            default:
                throw new TribbleException("Unknown count type: " + countType);
        }
    }

    /**
     * Specify annotation source
     * <p>
     * This value is optional starting with VCFv4.2.
     *
     * @param source  annotation source (case-insensitive, e.g. "dbsnp")
     */
    @Deprecated // after 2.24.1
    public void setSource(final String source) {
        updateGenericField(SOURCE_ATTRIBUTE, source);
    }

    /**
     * Specify annotation version
     * <p>
     * This value is optional starting with VCFv4.2.
     *
     * @param version exact version (e.g. "138")
     */
    @Deprecated // after version 2.24.1
    public void setVersion(final String version) {
        updateGenericField(VERSION_ATTRIBUTE, version);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof VCFCompoundHeaderLine)) return false;
        if (!super.equals(o)) return false;

        final VCFCompoundHeaderLine that = (VCFCompoundHeaderLine) o;

        if (count != that.count) return false;
        if (type != that.type) return false;
        return countType == that.countType;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + countType.hashCode();
        result = 31 * result + count;
        return result;
    }

    private VCFHeaderLineType decodeLineType(final String lineTypeString) {
        if (lineTypeString == null) {
            throw new TribbleException(String.format("A line type attribute is required for %s header lines", getKey()));
        } else {
            try {
                return VCFHeaderLineType.valueOf(lineTypeString);
            } catch (IllegalArgumentException e) {
                throw new TribbleException(String.format(
                        "\"%s\" is not a valid type for %s header lines (note that types are case-sensitive)",
                        lineTypeString,
                        getKey()));
            }
        }
    }

    private VCFHeaderLineCount decodeCountType(final String countString, final VCFHeaderVersion vcfVersion) {
        if (countString == null) {
            throw new TribbleException.InvalidHeader(
                    String.format("A count type/value must be provided for %s header lines.", getID()));
        }
        return VCFHeaderLineCount.decode(vcfVersion, countString);
    }

    private int decodeCount(final String countString, final VCFHeaderLineCount requestedCountType) {
        int lineCount = VCFHeaderLineCount.VARIABLE_COUNT;
        if (requestedCountType.isFixedCount()) {
            if (countString == null) {
                throw new TribbleException.InvalidHeader(String.format("Missing count value in VCF header field %s", getID()));
            }
            try {
                lineCount = Integer.parseInt(countString);
            } catch (NumberFormatException e) {
                throw new TribbleException.InvalidHeader(String.format("Invalid count value %s in VCF header field %s", lineCount, getID()));
            }
            if (getType() == VCFHeaderLineType.Flag) {
                if (lineCount != 0) {
                    // This check is here on behalf of INFO lines (which are the only header line type allowed to have Flag
                    // type). A Flag type with a count value other than 0 violates the spec (at least v4.2 and v4.3), but
                    // to retain backward compatibility with previous implementations, we accept (and repair) and the line here.
                    logger.warn(String.format("FLAG fields must have a count value of 0, but saw count %d for header line %s. A value of 0 will be used",
                            lineCount,
                            getID()));
                    updateGenericField(NUMBER_ATTRIBUTE, "0");
                    lineCount = 0;
                }
            } else if (lineCount <= 0) {
                throw new TribbleException.InvalidHeader(
                        String.format("Invalid count number %d for fixed count in header line with ID %s. For fixed count, the count number must be 1 or higher.",
                                lineCount,
                                getID()));
            }
        }
        return lineCount;
    }

    // Create a backing attribute map out of VCFCompoundHeaderLine elements
    private static LinkedHashMap<String, String> createAttributeMap(
            final String headerLineID,
            final VCFHeaderLineCount countType,
            final int count,
            final VCFHeaderLineType type,
            final String description) {
        return new LinkedHashMap<String, String>() {
            { put(ID_ATTRIBUTE, headerLineID); }
            { put(NUMBER_ATTRIBUTE, countType.encode(count)); }
            { put(TYPE_ATTRIBUTE, type.encode()); }
            {
                // Handle the case where there's no description provided, ALLOW_UNBOUND_DESCRIPTIONS is the default
                // note: if no description was provided, don't cache it, which means we don't round trip it
                if (description != null) {
                    put(DESCRIPTION_ATTRIBUTE, description);
                }
            }
        };
    }

    /**
     * Compare two VCFCompoundHeaderLine (FORMAT or INFO) lines to determine if they have compatible number types,
     * and return a VCFCompoundHeaderLine that can be used to represent the result of merging these lines. In the
     * case where the merged line requires "promoting" one of the types to the other, a new line of the appropriate
     * type is created by calling the {@code compoundHeaderLineResolver} to produce new line of the correct
     * subclass (INFO or FORMAT).
     *
     * @param line1 first line to merge
     * @param line2 second line to merge
     * @param conflictWarner conflict warning manager
     * @param compoundHeaderLineResolver function that accepts two compound header lines of the same type (info or
     *                                   format, and returns a new header line representing the combination of the
     *                                   two input header lines
     * @param <T> type of VCFCompoundHeaderLine to merge (subclass of VCFCompoundHeaderLine)
     * @return the merged line if one can be created
     */
    static <T extends VCFCompoundHeaderLine> T getMergedCompoundHeaderLine(
            final T line1,
            final T line2,
            final VCFHeaderMerger.HeaderMergeConflictWarnings conflictWarner,
            BiFunction<T, T, T> compoundHeaderLineResolver)
    {
        ValidationUtils.nonNull(line1);
        ValidationUtils.nonNull(line2);
        ValidationUtils.validateArg(line1.getKey().equals(line2.getKey()) && line1.getID().equals(line2.getID()),
                "header lines must have the same type to merge");
        T mergedLine = line1;

        if (!line1.equalsExcludingExtraAttributes(line2)) {
            if (getCompoundLineDifferenceScore(line1, line2) > 1) {
                // merge lines if they have zero or one mergeable differences, but if there are multiple
                // differences, call the headers incompatible and bail, since we need to choose one line
                // or the other as the merge line (we can't do generic field-level resolution)
                throw new TribbleException(
                        String.format("Incompatible header merge, can't merge lines with multiple attribute differences %s/%s.",
                                line1, line2));
            }
            if (line1.getType().equals(line2.getType())) {
                // The lines have a common type.
                // The Number entry is an Integer that describes the number of values that can be
                // included with the INFO field. For example, if the INFO field contains a single
                // number, then this value should be 1. However, if the INFO field describes a pair
                // of numbers, then this value should be 2 and so on. If the number of possible
                // values varies, is unknown, or is unbounded, then this value should be '.'.
                conflictWarner.warn("Promoting header field Number to . due to number differences in header lines: " + line1 + " " + line2);
                mergedLine = compoundHeaderLineResolver.apply(line1, line2);
            } else if (line1.getType() == VCFHeaderLineType.Integer && line2.getType() == VCFHeaderLineType.Float) {
                // promote key to Float
                conflictWarner.warn("Promoting Integer to Float in header: " + line2);
                mergedLine = line2;
            } else if (line1.getType() == VCFHeaderLineType.Float && line2.getType() == VCFHeaderLineType.Integer) {
                // promote key to Float
                conflictWarner.warn("Promoting Integer to Float in header: " + line2);
            } else {
                throw new IllegalStateException("Attempt to merge incompatible headers, can't merge these lines: " + line1 + " " + line2);
            }
        }
        if (!line1.getDescription().equals(line2.getDescription())) {
            conflictWarner.warn("Allowing unequal description fields through: keeping " + line2 + " excluding " + line1);
        }

        return mergedLine;
    }

    boolean equalsExcludingExtraAttributes(final VCFCompoundHeaderLine other) {
        return count == other.count &&
                countType == other.countType &&
                type == other.type &&
                getKey().equals(other.getKey()) &&
                getID().equals(other.getID());
    }

    private static <T extends VCFCompoundHeaderLine> int getCompoundLineDifferenceScore(final T line1, final T line2) {
        final int dataTypeDiffers = line1.getType().equals(line2.getType()) ? 0 : 1; // data type
        final int countTypeDiffers = line1.getCountType().equals(line2.getCountType()) ? 0 : 1; // count type
        // getCount is only valid if the getCountType==Integer
        final int countDiffers =
                (countTypeDiffers == 0 &&
                        line1.getCountType().equals(VCFHeaderLineCount.INTEGER) &&
                        line2.getCountType().equals(VCFHeaderLineCount.INTEGER) &&
                        line1.getCount() != line2.getCount()) ? 1 : 0;
        return dataTypeDiffers + countTypeDiffers + countDiffers;
    }
}
