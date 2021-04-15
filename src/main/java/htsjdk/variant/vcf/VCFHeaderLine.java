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
import htsjdk.utils.Utils;

import java.io.Serializable;
import java.util.Map;

/**
 * <p> Class VCFHeaderLine </p>
 * <p> A class representing a key=value entry in the VCF header, and the base class for all structured header lines </p>
 */
public class VCFHeaderLine implements Comparable, Serializable {
    public static final long serialVersionUID = 1L;

    // immutable - we don't want to let the hash value change
    private final String mKey;
    private final String mValue;

    /**
     * create a VCF header line
     *
     * @param key     the key for this header line
     * @param value   the value for this header line
     */
    public VCFHeaderLine(String key, String value) {
        mKey = key;
        mValue = value;
        validate();
    }

    /**
     * Get the key
     *
     * @return the key
     */
    public String getKey() {
        return mKey;
    }

    /**
     * Get the value
     *
     * @return the value
     */
    //TODO: subclasses such as VCFSimpleHeaderLine use a null for the value, so it should be overridden
    // by those classes so they return an encoded version of the value part of the string (or perhaps
    // they should pass that in to this class' constructor so it will never be null...which would
    // require all of these classes to be fully immutable
    //TODO: OR update javadoc to say this can be null
    public String getValue() {
        return mValue;
    }

    /**
     * @return true if this is a structured header line (has a unique ID and key/value pairs), otherwise false
     */
    public boolean isIDHeaderLine() { return false; }

    /**
     * Return the unique ID for this line. Returns null iff isStructuredHeaderLine is false.
     * @return
     */
    public String getID() { return null; }

    /**
     * Validate the state of this header line. Require the key be valid as an "id".
     */
    private void validate() {
        validateKeyOrID(mKey, "key");
    }

    /**
     * Called when an attempt is made to add this VCFHeaderLine to a VCFHeader, or when an attempt is made
     * to change the version of a VCFHeader by changing it's target version. Validates that the header line
     * conforms to the target version requirements.
     *
     * Subclasses can override this to provide line-specific version validation, and the overrides should
     * also call super.validateForVersion to allow each class in the class hierarchy to do class-level validation.
     */
    //TODO: should we implement this for all/any subclasses ?
    public void validateForVersion(final VCFHeaderVersion vcfTargetVersion) {
        // If this header line is itself a fileformat/version line,
        // make sure it doesn't clash with the new targetVersion.
        if (VCFHeaderVersion.isFormatString(getKey())) {
            if (!vcfTargetVersion.getFormatString().equals(getKey())  ||
                    !vcfTargetVersion.getVersionString().equals(getValue())) {
                throw new TribbleException(
                        String.format("The fileformat header line \"%s\" is incompatible with version \"%s\"",
                                this.toStringEncoding(),
                                vcfTargetVersion.toString()));
            }
        }
        // PEDIGREE lines that are not represented as VCFPedigreeHeaderLine, meaning they do not have an ID,
        // are not allowed starting v4.3
        if (vcfTargetVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3) &&
            getKey().equals(VCFConstants.PEDIGREE_HEADER_KEY) &&
            !(this instanceof VCFPedigreeHeaderLine)
        ) {
            throw new TribbleException(
                String.format("PEDIGREE lines without IDs: \"%s\" is incompatible with version \"%s\"",
                    this.toStringEncoding(),
                    vcfTargetVersion.toString())
            );
        }
    }

    /**
     * Validate a string that is to be used as a unique id or key field.
     */
    protected static void validateKeyOrID(final String keyString, final String sourceName) {
        Utils.nonNull(sourceName);
        if (keyString == null) {
            throw new TribbleException(
                    String.format("VCFHeaderLine: %s cannot be null or empty", sourceName));
        }
        if ( keyString.contains("<") || keyString.contains(">") ) {
            throw new TribbleException(
                    String.format("VCFHeaderLine: %s cannot contain angle brackets", sourceName));
        }
        if ( keyString.contains("=") ) {
            throw new TribbleException(
                    String.format("VCFHeaderLine: %s cannot contain an equals sign", sourceName));
        }
    }

    //TODO: this method and it's overrides should be removed since they're for BCF only
    /**
     * By default the header lines won't be added to the dictionary, unless this method will be override
     * (for example in FORMAT, INFO or FILTER header lines)
     *
     * @return false
     */
    public boolean shouldBeAddedToDictionary() {
        return false;
    }

    public String toString() {
        return toStringEncoding();
    }

    /**
     * Should be overloaded in sub classes to do subclass specific
     *
     * @return the string encoding
     */
    protected String toStringEncoding() {
        return mKey + "=" + mValue;
    }

    @Override
    public boolean equals(final Object o) {
        if ( this == o ) {
            return true;
        }
        if ( o == null || getClass() != o.getClass() ) {
            return false;
        }

        final VCFHeaderLine that = (VCFHeaderLine) o;
        return mKey.equals(that.mKey) &&                                             // key not nullable
               (mValue != null ? mValue.equals(that.mValue) : that.mValue == null);  // value is nullable
    }

    @Override
    public int hashCode() {
        int result = mKey.hashCode();
        result = 31 * result + (mValue != null ? mValue.hashCode() : 0);
        return result;
    }

    @Override
    public int compareTo(Object other) {
        return toString().compareTo(other.toString());
    }

    /**
     * @param line    the line
     * @return true if the line is a VCF meta data line, or false if it is not
     */
    public static boolean isHeaderLine(String line) {
        return line != null && !line.isEmpty() && VCFHeader.HEADER_INDICATOR.equals(line.substring(0,1));
    }

    /**
     * create a string of a mapping pair for the target VCF version
     * @param keyValues a mapping of the key-&gt;value pairs to output
     * @return a string, correctly formatted
     */
    //TODO: this should be removed and folded into toStringEncoding(): String, or else
    // protected and VisibleForTesting
    public static String toStringEncoding(final Map<String, ?> keyValues) {
        final StringBuilder builder = new StringBuilder();
        builder.append('<');
        boolean start = true;
        for (final Map.Entry<String, ?> entry : keyValues.entrySet()) {
            if (start) start = false;
            else builder.append(',');

            if (entry.getValue() == null)
                throw new TribbleException.InternalCodecException("Header problem: unbound value at " + entry + " from " + keyValues);

            final String key = entry.getKey();
            builder.append(key);
            builder.append('=');

            final String value = entry.getValue().toString();
            final String escapedValue = value.contains(",") ||
                value.contains(" ") ||
                key.equals(VCFSimpleHeaderLine.DESCRIPTION_ATTRIBUTE) ||
                key.equals(VCFSimpleHeaderLine.SOURCE_ATTRIBUTE) || // As per VCFv4.2, Source and Version should be surrounded by double quotes
                key.equals(VCFSimpleHeaderLine.VERSION_ATTRIBUTE)
                ? "\"" + escapeQuotes(entry.getValue().toString()) + "\""
                : value;

            // As of VCFv4.3, fields which contain characters that have special meanings in the VCF headers
            // can be percent encoded
            if (key.equals(VCFSimpleHeaderLine.ID_ATTRIBUTE) ||
                key.equals(VCFCompoundHeaderLine.NUMBER_ATTRIBUTE) ||
                key.equals(VCFCompoundHeaderLine.TYPE_ATTRIBUTE)) {
                builder.append(escapedValue);
            } else {
                builder.append(new VCFPercentEncodedTextTransformer().encodeHeaderText(escapedValue));
            }
        }
        builder.append('>');
        return builder.toString();
    }

    private static String escapeQuotes(final String value) {
        // java escaping in a string literal makes this harder to read than it should be
        // without string literal escaping and quoting the regex would be: replaceAll( ([^\])" , $1\" )
        // ie replace: something that's not a backslash ([^\]) followed by a double quote
        // with: the thing that wasn't a backslash ($1), followed by a backslash, followed by a double quote
        return value.replaceAll("([^\\\\])\"", "$1\\\\\"");
    }
}
