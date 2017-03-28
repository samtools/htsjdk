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

import htsjdk.utils.Utils;

/**
 * the count encodings we use for fields in VCF header lines
 */
//TODO: this should really be called VCHFHeaderCountType
public enum VCFHeaderLineCount {
    INTEGER, A, R, G, UNBOUNDED;

    // A default int value used to represent an integral count value (not a count *type*) when the
    // actual count is derived and not a fixed integer (i.e., when isFixedCount()==false)
    public static final int VARIABLE_COUNT = -1;

    public boolean isFixedCount() { return this.equals(INTEGER); }

    /**
     * Decode a header line count string and return the corresponding VCFHeaderLineCount enum value.
     * If the value is not recognized as a valid constant, assume the string represents a fixed, numeric
     * value, and return Integer. The caller should convert and validate the actual value.
     *
     * @param vcfVersion
     * @param countTypeString
     * @return
     */
    protected static VCFHeaderLineCount decode(final VCFHeaderVersion vcfVersion, final String countTypeString) {
        Utils.nonNull(vcfVersion);
        Utils.nonNull(countTypeString);

        if (countTypeString.equals(VCFConstants.PER_ALTERNATE_COUNT)) {
            return A;
        } else if (countTypeString.equals(VCFConstants.PER_ALLELE_COUNT)) {
            return R;
        } else if (countTypeString.equals(VCFConstants.PER_GENOTYPE_COUNT)) {
            return G;
        } else if (
                (vcfVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_0) && countTypeString.equals(VCFConstants.UNBOUNDED_ENCODING_v4)) ||
                (!vcfVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_0) && countTypeString.equals(VCFConstants.UNBOUNDED_ENCODING_v3))) {
            return VCFHeaderLineCount.UNBOUNDED;
        } else {
            return VCFHeaderLineCount.INTEGER; // assume integer
        }
    }

    /**
     * Encode a count type as a string suitable for serialization to a VCF header. Note this is
     * not version aware and defaults to VCFv4 format.
     *
     * @param actualCount Must be the special value {@code VARIABLE_COUNT} unless this object is {@code VCFHeaderLineCount.INTEGER}.
     * @return String encoding of this enum, or the {@code actualCount} if the type of this count
     * is VCFHeaderLineCount.INTEGER.
     *
     * @throws IllegalArgumentException if {@code actualCount} is not the special value {@code VARIABLE_COUNT} and this
     * is not the {@code VCFHeaderLineCount.INTEGER} enum object.
     */
    public String encode(final int actualCount) {
        if (this != INTEGER && actualCount != VARIABLE_COUNT) {
            // Should only supply an actualCount if the count type == INTEGER
            throw new IllegalArgumentException("Inconsistent header line number encoding request");
        }
        switch (this) {
            case A:
                return VCFConstants.PER_ALTERNATE_COUNT;
            case R:
                return VCFConstants.PER_ALLELE_COUNT;
            case G:
                return VCFConstants.PER_GENOTYPE_COUNT;
            case UNBOUNDED:
                return VCFConstants.UNBOUNDED_ENCODING_v4;
            case INTEGER:
                return Integer.toString(actualCount);
        }
        throw new IllegalStateException("Unexpected VCFHeaderLineCount enum value");
    }

}
