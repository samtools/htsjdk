/*
* Copyright (c) 2017 The Broad Institute
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * An abstract class representing a VCF metadata line with a key and attribute=value pairs, one of
 * which represents an ID. The key determines the "type" of the structured header line (i.e., contig, FILTER,
 * INFO, ALT, PEDIGREE, META).
 *
 * The attribute/value pairs are ordered. The first entry in the map must be an ID attribute (used by the
 * VCFHeader to ensure that no two structured header lines that share the same key in a given header have the
 * same ID).
 */
public class VCFSimpleHeaderLine extends VCFHeaderLine implements VCFIDHeaderLine {
    private static final long serialVersionUID = 1L;
    protected static final Log logger = Log.getInstance(VCFSimpleHeaderLine.class);

    public static final String ID_ATTRIBUTE = "ID";
    public static final String DESCRIPTION_ATTRIBUTE = "Description";
    public static final String SOURCE_ATTRIBUTE = "Source";
    public static final String VERSION_ATTRIBUTE = "Version";

    // List of expected tags (for this base class, its ID only; subclasses with more required tags
    // should use a custom tag order if more required tags are expected
    protected static final List<String> expectedTagOrder = Collections.unmodifiableList(
            new ArrayList<String>(1) {{ add(ID_ATTRIBUTE); }});

    // Map used to retain the attribute/value pairs, in original order. The first entry in the map must be
    // an ID field. The entire map must be immutable to prevent hash values from changing, since these are
    // often stored in Sets. Its not ACTUALLY immutable in orderto allow for special cases where subclasses
    // have to be able to "repair" header lines (via a call to updateGenericField) during constructor validation.
    //
    // Otherwise the values here should never change during the lifetime of the header line.
    private final Map<String, String> genericFields = new LinkedHashMap();

    /**
     * Constructor that accepts a key and string that represetns the rest of the line (after the ##KEY=").
     * @param key the key to use for this line
     * @param line the value part of the line
     * @param version the target version to validate the line against
     */
    public VCFSimpleHeaderLine(final String key, final String line, final VCFHeaderVersion version) {
        this(key, VCFHeaderLineTranslator.parseLine(version, line, expectedTagOrder));
        validateForVersionOrThrow(version);
    }

    /**
     * Key cannot be null or empty.
     *
     * @param key key to use for this header line. can not be null.
     * @param id id name to use for this line
     * @param description string that will be added as a "Description" tag to this line
     */
    public VCFSimpleHeaderLine(final String key, final String id, final String description) {
        this(key, new LinkedHashMap<String, String>() {{
            put(ID_ATTRIBUTE, id);
            put(DESCRIPTION_ATTRIBUTE, description);
        }});
    }

    /**
     * Key cannot be null or empty.
     *
     * Note that for attributes where the order is significant, use a LinkedHashMap
     * to ensure that attribute order is honored.
     *
     * @param key key to use for this header line. can not be null.
     * @param attributeMapping field mappings to use. may not be null. must contain an "ID" field to use as
     *                         a unique id for this line
     */
    public VCFSimpleHeaderLine(final String key, final Map<String, String> attributeMapping) {
        super(key, "");

        ValidationUtils.nonNull(attributeMapping, "An attribute map is required for structured header lines");
        genericFields.putAll(attributeMapping);

        if ( genericFields.isEmpty() || !genericFields.keySet().stream().findFirst().get().equals(ID_ATTRIBUTE)) {
            throw new TribbleException(
                    String.format("The required ID tag is missing or not the first attribute: key=%s", super.getKey()));
        }
        final Optional<String> validationFailure = validateAttributeName(getGenericFieldValue(ID_ATTRIBUTE), "ID");
        if (validationFailure.isPresent()) {
            throw new TribbleException(validationFailure.get());
        }
    }

    /**
     * @return true if this is a structured header line (has a unique ID and multiple key/value pairs),
     * otherwise false
     */
    @Override
    public boolean isIDHeaderLine() { return true; }

    /**
     * Return the unique ID for this line. Returns null iff isIDHeaderLine is false.
     * @return
     */
    @Override
    public String getID() {
        return getGenericFieldValue(ID_ATTRIBUTE);
    }

    /**
     * Returns the String value associated with the given key. Returns null if there is no value. Key
     * must not be null.
     */
    public String getGenericFieldValue(final String key) {
        return this.genericFields.get(key);
    }

    /**
     * Returns a list of all attributes for this header line.
     */
    public Map<String, String> getGenericFields() {
        return Collections.unmodifiableMap(this.genericFields);
    }

    @Override
    public boolean equals( final Object o ) {
        if ( this == o ) {
            return true;
        }
        if ( o == null || getClass() != o.getClass() || ! super.equals(o) ) {
            return false;
        }

        final VCFSimpleHeaderLine that = (VCFSimpleHeaderLine) o;
        return genericFields.equals(that.genericFields);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + genericFields.hashCode();
        return result;
    }

    /**
     * create a string of a mapping pair for the target VCF version
     * @return a string, correctly formatted
     */
    @Override
    protected String toStringEncoding() {
        //NOTE: this preserves/round-trips "extra" attributes such as SOURCE, VERSION, etc.
        final StringBuilder builder = new StringBuilder();
        builder.append(getKey());
        builder.append("=<");
        builder.append(genericFields.entrySet().stream()
                .map(e -> e.getKey() + "=" + quoteAttributeValueForSerialization(e.getKey(), e.getValue()))
                .collect(Collectors.joining(",")));
        builder.append('>');
        return builder.toString();
    }

    // Called by VCFInfoHeaderLine to allow repairing of VCFInfoLines that have a Flag type and a non-zero count
    // (the combination of which is forbidden by the spec, but which we tolerate for backward compatibility with
    // previous versions of htsjdk, which silently repaired these).
    //
    // Replaces the original generic fields map with another immutable map with the updated value.
    protected void updateGenericField(final String attributeName, final String value) {
        genericFields.put(attributeName, value);
    }

    /**
     * Return true if the attribute name requires quotes.
     * @param attributeName name of the attribute being serialized
     * @return boolean indicating whether the value should be embedded n quotes during serialization
     */
    protected boolean getIsQuotableAttribute(final String attributeName) {
        // the (VF4.3) spec says that the DESCRIPTION, SOURCE, and VERSION attributes should be quoted
        // for INFO/FORMAT lines, but htsjdk seems to have historically quoted these for all structured
        // header lines
        return attributeName.equals(DESCRIPTION_ATTRIBUTE) ||
                attributeName.equals(SOURCE_ATTRIBUTE) ||
                attributeName.equals(VERSION_ATTRIBUTE);
    }

    // Add quotes around any attribute value that contains a space or comma, or is supposed to be quoted by
    // definition per the spec (i.e., Description, Source, Version for INFO lines).
    private String quoteAttributeValueForSerialization(final String attribute, final String originalValue) {
        return originalValue.contains(",") || originalValue.contains(" ") || getIsQuotableAttribute(attribute) ?
                "\""+ escapeQuotes(originalValue) + "\"" :
                originalValue;
    }

    private static String escapeQuotes(final String value) {
        // java escaping in a string literal makes this harder to read than it should be
        // without string literal escaping and quoting the regex would be: replaceAll( ([^\])" , $1\" )
        // ie replace: something that's not a backslash ([^\]) followed by a double quote
        // with: the thing that wasn't a backslash ($1), followed by a backslash, followed by a double quote
        return value.replaceAll("([^\\\\])\"", "$1\\\\\"");
    }

}