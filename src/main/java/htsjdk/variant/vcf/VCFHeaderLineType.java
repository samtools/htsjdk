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
 * the type encodings we use for fields in VCF header lines
 */
public enum VCFHeaderLineType {
    Integer,
    Float,
    String,
    Character,
    Flag;

    /**
     * Decode a header line count string and return the corresponding VCFHeaderLineCount enum value.
     * If the value is not recognized as a valid constant, we assume the string represents a numeric
     * value and return Integer. The caller should convert and validate the value.
     *
     * @param lineTypeString
     * @return VCFHeaderLineType for {@code lineTypeString}
     */
    protected static VCFHeaderLineType decode(final String lineTypeString) {
        Utils.nonNull(lineTypeString);
        return VCFHeaderLineType.valueOf(lineTypeString);
    }

    /**
     * Encode this line type as a string suitable for serialization to a VCF header. Note this is
     * not version specific and defaults to VCFv42.
     *
     * The serialized encoding is the simple name of the enum constant
     * @return string encoding of this line type
     */
    String encode() { return this.toString(); }
}
