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
import htsjdk.variant.variantcontext.GenotypeLikelihoods;
import htsjdk.variant.variantcontext.VariantContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * a base class for compound header lines, which include info lines and format lines (so far)
 */
public abstract class VCFCompoundHeaderLine extends VCFHeaderLine implements VCFIDHeaderLine {

    public enum SupportedHeaderLineType {
        INFO(true),
        FORMAT(false);

        public final boolean allowFlagValues;

        SupportedHeaderLineType(boolean flagValues) {
            allowFlagValues = flagValues;
        }
    }

    private static final List<String> EXPECTED_TAGS = List.of("ID", "Number", "Type", "Description");

    // the field types
    private String name;
    private int count = -1;
    private VCFHeaderLineCount countType;
    private String description;
    private VCFHeaderLineType type;
    private String source;
    private String version;

    // every attribute other than the six above, in the order they appeared, so that a line round-trips intact
    private final Map<String, String> otherAttributes = new LinkedHashMap<>();

    // access methods
    @Override
    public String getID() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public VCFHeaderLineType getType() {
        return type;
    }

    public VCFHeaderLineCount getCountType() {
        return countType;
    }

    public boolean isFixedCount() {
        return countType == VCFHeaderLineCount.INTEGER;
    }

    public int getCount() {
        if (!isFixedCount()) throw new TribbleException("Asking for header line count when type is not an integer");
        return count;
    }

    public String getSource() {
        return source;
    }

    public String getVersion() {
        return version;
    }

    /**
     * Returns the value of any attribute of this line by its tag, including the standard ones ({@code ID},
     * {@code Number}, {@code Type}, {@code Description}, {@code Source}, {@code Version}) and any others the line
     * carries. Returns null if the line has no such attribute.
     */
    public String getGenericFieldValue(final String tag) {
        return getGenericFields().get(tag);
    }

    /**
     * @return every attribute of this line, standard ones first, in the order they are written
     */
    public Map<String, String> getGenericFields() {
        final Map<String, String> attributes = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> attribute : encodedAttributes().entrySet()) {
            attributes.put(attribute.getKey(), attribute.getValue().toString());
        }
        return Collections.unmodifiableMap(attributes);
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

    public void setNumberToUnbounded() {
        countType = VCFHeaderLineCount.UNBOUNDED;
        count = -1;
    }

    // our type of line, i.e. format, info, etc
    private final SupportedHeaderLineType lineType;

    /**
     * create a VCF format header line
     *
     * @param name         the name for this header line
     * @param count        the count for this header line
     * @param type         the type for this header line
     * @param description  the description for this header line
     * @param lineType     the header line type
     */
    protected VCFCompoundHeaderLine(
            String name, int count, VCFHeaderLineType type, String description, SupportedHeaderLineType lineType) {
        this(name, count, type, description, lineType, null, null);
    }

    /**
     * create a VCF format header line
     *
     * @param name         the name for this header line
     * @param count        the count type for this header line
     * @param type         the type for this header line
     * @param description  the description for this header line
     * @param lineType     the header line type
     */
    protected VCFCompoundHeaderLine(
            String name,
            VCFHeaderLineCount count,
            VCFHeaderLineType type,
            String description,
            SupportedHeaderLineType lineType) {
        this(name, count, type, description, lineType, null, null);
    }

    /**
     * create a VCF format header line
     *
     * @param name         the name for this header line
     * @param count        the count for this header line
     * @param type         the type for this header line
     * @param description  the description for this header line
     * @param lineType     the header line type
     * @param source       annotation source (case-insensitive, e.g. "dbsnp")
     * @param version      exact version (e.g. "138")
     */
    protected VCFCompoundHeaderLine(
            String name,
            int count,
            VCFHeaderLineType type,
            String description,
            SupportedHeaderLineType lineType,
            String source,
            String version) {
        super(lineType.toString(), "");
        this.name = name;
        this.countType = VCFHeaderLineCount.INTEGER;
        this.count = count;
        this.type = type;
        this.description = description;
        this.lineType = lineType;
        this.source = source;
        this.version = version;
        validate();
    }

    /**
     * create a VCF format header line
     *
     * @param name         the name for this header line
     * @param count        the count type for this header line
     * @param type         the type for this header line
     * @param description  the description for this header line
     * @param lineType     the header line type
     * @param source       annotation source (case-insensitive, e.g. "dbsnp")
     * @param version      exact version (e.g. "138")
     */
    protected VCFCompoundHeaderLine(
            String name,
            VCFHeaderLineCount count,
            VCFHeaderLineType type,
            String description,
            SupportedHeaderLineType lineType,
            String source,
            String version) {
        super(lineType.toString(), "");
        this.name = name;
        this.countType = count;
        this.type = type;
        this.description = description;
        this.lineType = lineType;
        this.source = source;
        this.version = version;
        validate();
    }

    /**
     * The definition of one line (ID, Number, Type, Description) with the attributes of another that a definition
     * does not cover: Source, Version and any non-standard ones.
     */
    protected VCFCompoundHeaderLine(final VCFCompoundHeaderLine definition, final VCFCompoundHeaderLine attributes) {
        super(definition.lineType.toString(), "");
        this.name = definition.name;
        this.count = definition.count;
        this.countType = definition.countType;
        this.type = definition.type;
        this.description = definition.description;
        this.lineType = definition.lineType;
        this.source = attributes.source;
        this.version = attributes.version;
        this.otherAttributes.putAll(attributes.otherAttributes);
        validate();
    }

    /**
     * create a VCF format header line
     *
     * @param line   the header line
     * @param version      the VCF header version
     * @param lineType     the header line type
     *
     */
    protected VCFCompoundHeaderLine(String line, VCFHeaderVersion version, SupportedHeaderLineType lineType) {
        super(lineType.toString(), "");

        final Map<String, String> mapping = VCFHeaderLineTranslator.parseLine(version, line, EXPECTED_TAGS);
        for (final String tag : List.of("ID", "Number", "Type")) {
            if (mapping.get(tag) == null) {
                throw new TribbleException.InvalidHeader(
                        lineType + " header line is missing its " + tag + " attribute: " + line);
            }
        }
        name = mapping.get("ID");
        count = -1;
        final String numberStr = mapping.get("Number");
        if (numberStr.equals(VCFConstants.PER_ALTERNATE_COUNT)) {
            countType = VCFHeaderLineCount.A;
        } else if (numberStr.equals(VCFConstants.PER_ALLELE_COUNT)) {
            countType = VCFHeaderLineCount.R;
        } else if (numberStr.equals(VCFConstants.PER_GENOTYPE_COUNT)) {
            countType = VCFHeaderLineCount.G;
        } else if ((version.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_0)
                        && numberStr.equals(VCFConstants.UNBOUNDED_ENCODING_v4))
                || (!version.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_0)
                        && numberStr.equals(VCFConstants.UNBOUNDED_ENCODING_v3))) {
            countType = VCFHeaderLineCount.UNBOUNDED;
        } else {
            countType = VCFHeaderLineCount.INTEGER;
            count = Integer.parseInt(numberStr);
        }

        if (count < 0 && countType == VCFHeaderLineCount.INTEGER)
            throw new TribbleException.InvalidHeader("Count < 0 for fixed size VCF header field " + name);

        try {
            type = VCFHeaderLineType.valueOf(mapping.get("Type"));
        } catch (Exception e) {
            throw new TribbleException(mapping.get("Type")
                    + " is not a valid type in the VCF specification (note that types are case-sensitive)");
        }
        if (type == VCFHeaderLineType.Flag && !allowFlagValues())
            throw new IllegalArgumentException("Flag is an unsupported type for this kind of field at line - " + line);

        description = mapping.get("Description");
        if (description == null && ALLOW_UNBOUND_DESCRIPTIONS) // handle the case where there's no description provided
        description = UNBOUND_DESCRIPTION;

        this.lineType = lineType;
        this.source = mapping.get("Source");
        this.version = mapping.get("Version");
        for (final Map.Entry<String, String> attribute : mapping.entrySet()) {
            if (!STANDARD_TAGS.contains(attribute.getKey())) {
                otherAttributes.put(attribute.getKey(), attribute.getValue());
            }
        }

        try {
            validate();
        } catch (final IllegalArgumentException e) {
            throw new TribbleException.InvalidHeader(e.getMessage() + ": " + line);
        }
    }

    private static final List<String> STANDARD_TAGS =
            List.of("ID", "Number", "Type", "Description", "Source", "Version");

    private void validate() {
        if (type != VCFHeaderLineType.Flag && countType == VCFHeaderLineCount.INTEGER && count <= 0)
            throw new IllegalArgumentException(String.format(
                    "Invalid count number, with fixed count the number should be 1 or higher: key=%s name=%s type=%s desc=%s lineType=%s count=%s",
                    getKey(), name, type, description, lineType, count));
        if (name == null || type == null || description == null || lineType == null)
            throw new IllegalArgumentException(String.format(
                    "Invalid VCFCompoundHeaderLine: key=%s name=%s type=%s desc=%s lineType=%s",
                    getKey(), name, type, description, lineType));
        if (name.contains("<") || name.contains(">"))
            throw new IllegalArgumentException("VCFHeaderLine: ID cannot contain angle brackets");
        if (name.contains("=")) throw new IllegalArgumentException("VCFHeaderLine: ID cannot contain an equals sign");

        if (type == VCFHeaderLineType.Flag && count != 0) {
            count = 0;
            if (GeneralUtils.DEBUG_MODE_ENABLED) {
                System.err.println("FLAG fields must have a count value of 0, but saw " + count + " for header line "
                        + getID() + ". Changing it to 0 inside the code");
            }
        }
    }

    /**
     * make a string representation of this header line
     * @return a string representation
     */
    @Override
    protected String toStringEncoding() {
        return lineType.toString() + "=" + VCFHeaderLine.toStringEncoding(encodedAttributes());
    }

    /** The attributes as written: the standard ones in their conventional order, then any others in theirs. */
    private Map<String, Object> encodedAttributes() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("ID", name);
        Object number;
        switch (countType) {
            case A:
                number = VCFConstants.PER_ALTERNATE_COUNT;
                break;
            case R:
                number = VCFConstants.PER_ALLELE_COUNT;
                break;
            case G:
                number = VCFConstants.PER_GENOTYPE_COUNT;
                break;
            case UNBOUNDED:
                number = VCFConstants.UNBOUNDED_ENCODING_v4;
                break;
            case INTEGER:
            default:
                number = count;
        }
        map.put("Number", number);
        map.put("Type", type);
        map.put("Description", description);
        if (source != null) {
            map.put("Source", source);
        }
        if (version != null) {
            map.put("Version", version);
        }
        map.putAll(otherAttributes);
        return map;
    }

    /**
     * returns true if we're equal to another compound header line
     * @param o a compound header line
     * @return true if equal
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass() || !super.equals(o)) {
            return false;
        }

        final VCFCompoundHeaderLine that = (VCFCompoundHeaderLine) o;
        return equalsExcludingDescription(that)
                && description.equals(that.description)
                && Objects.equals(source, that.source)
                && Objects.equals(version, that.version)
                && otherAttributes.equals(that.otherAttributes);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + count;
        result = 31 * result
                + (countType != null ? countType.hashCode() : 0); // only nullable field according to validate()
        result = 31 * result + description.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + lineType.hashCode();
        result = 31 * result + (source != null ? source.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + otherAttributes.hashCode();
        return result;
    }

    public boolean equalsExcludingDescription(VCFCompoundHeaderLine other) {
        return count == other.count
                && countType == other.countType
                && type == other.type
                && lineType == other.lineType
                && name.equals(other.name);
    }

    public boolean sameLineTypeAndName(VCFCompoundHeaderLine other) {
        return lineType == other.lineType && name.equals(other.name);
    }

    /**
     * do we allow flag (boolean) values? (i.e. booleans where you don't have specify the value, AQ means AQ=true)
     * @return true if we do, false otherwise
     */
    abstract boolean allowFlagValues();

    /**
     * Specify annotation source
     * <p>
     * This value is optional starting with VCFv4.2.
     *
     * @param source  annotation source (case-insensitive, e.g. "dbsnp")
     */
    public void setSource(final String source) {
        this.source = source;
    }

    /**
     * Specify annotation version
     * <p>
     * This value is optional starting with VCFv4.2.
     *
     * @param version exact version (e.g. "138")
     */
    public void setVersion(final String version) {
        this.version = version;
    }
}
