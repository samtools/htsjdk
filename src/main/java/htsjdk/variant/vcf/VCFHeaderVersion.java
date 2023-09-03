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

import htsjdk.beta.plugin.HtsVersion;
import htsjdk.tribble.TribbleException;
import htsjdk.utils.ValidationUtils;

/**
 * information that identifies each header version
 */
public enum VCFHeaderVersion {
    // Keep this list in increasing (ordinal) order, since isAtLeastAsRecentAs depends on it
    VCF3_2("VCRv", "3.2", "format"),
    VCF3_3("VCFv", "3.3", "fileformat"),
    VCF4_0("VCFv", "4.0", "fileformat"),
    VCF4_1("VCFv", "4.1", "fileformat"),
    VCF4_2("VCFv", "4.2", "fileformat"),
    VCF4_3("VCFv", "4.3", "fileformat");

    private final String versionPrefixString;
    private final String versionString;
    private final String formatString;

    /**
     * create the enum, privately, using:
     * @param pString the version prefix string (i.e., "VCFv")
     * @param vString the version string
     * @param fString the format string
     */
     VCFHeaderVersion(final String pString, final String vString, final String fString) {
         this.versionPrefixString = pString;
         this.versionString = vString;
         this.formatString = fString;
    }

    /**
     * get the header version
     * @param version the version string
     * @return a VCFHeaderVersion object
     */
    public static VCFHeaderVersion toHeaderVersion(String version) {
        version = clean(version);
        for (VCFHeaderVersion hv : VCFHeaderVersion.values())
            if (hv.getVersionString().equals(version))
                return hv;
        return null;
    }

    /**
     * are we a valid version string of some type
     * @param version the version string (the part of the header line that specifies the version,
     *               i.e., "VCFv4.3" if the line is "##fileformat=VCFv4.3")
     * @return true if we're valid of some type, false otherwise
     */
    public static boolean isVersionString(String version){
        return toHeaderVersion(version) != null;
    }

    /**
     * are we a valid format string for some type (the key part of the header line that specifies a version,
     *               i.e., "fileformat" if the line is "##fileformat=VCFv4.3")
     * @param format the format string
     * @return true if we're valid of some type, false otherwise
     */
    public static boolean isFormatString(String format){
        format = clean(format);
        for (VCFHeaderVersion hv : VCFHeaderVersion.values())
            if (hv.formatString.equals(format))
                return true;
        return false;
    }

    /**
     *
     * @param versionLine a VCF header version line, including the leading meta data indicator,
     *                    for example "##fileformat=VCFv4.2"
     * @return the VCFHeaderVersion for this string
     * @throws TribbleException.InvalidHeader if the string is not a version string for a recognized supported version
     */
    public static VCFHeaderVersion fromHeaderVersionLine(final String versionLine) {
        ValidationUtils.nonNull(versionLine, "version line");
        final String[] lineFields = versionLine.split("=");
        if ( lineFields.length != 2 || !isFormatString(lineFields[0].substring(2)) )
            throw new TribbleException.InvalidHeader(versionLine + " is not a valid VCF version line");

        if ( !isVersionString(lineFields[1]) )
            throw new TribbleException.InvalidHeader(lineFields[1] + " is not a supported version");

        return toHeaderVersion(lineFields[1]);
    }

    /**
     * @return A VCF "##fileformat=version" metadata string for the supplied version.
     */
    public String toHeaderVersionLine() {
        return String.format("%s%s=%s", VCFHeader.METADATA_INDICATOR, getFormatString(), getVersionString());
    }

    /**
     * Convert the two part VCF header version to a corresponding three part {@link HtsVersion}.
     * @return and {@link HtsVersion} for this {@link VCFHeaderVersion}
     */
    public HtsVersion toHtsVersion() {
        final String[] versionParts = versionString.split("\\.");
        return new HtsVersion(Integer.parseInt(versionParts[0]), Integer.parseInt(versionParts[1]), HtsVersion.ANY_VERSION);
    }

    /**
     * Utility function to clean up a VCF header string
     * 
     * @param s string
     * @return  trimmed version of s
     */
    private static String clean(String s) {
        return s.trim();
    }

    /**
     * Determines whether this version is at least as recent as a given version
     *
     * @param target   the target version to compare against
     * @return true if this version is at least as recent as the target version, false otherwise
     */
    public boolean isAtLeastAsRecentAs(final VCFHeaderVersion target) {
        return this.ordinal() >= target.ordinal();
    }

    public String getVersionPrefixString() {
        return versionPrefixString;
    }

    public String getVersionString() {
        return versionPrefixString + versionString;
    }

    public String getFormatString() {
        return formatString;
    }

}
