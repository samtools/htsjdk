/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools;

import htsjdk.samtools.util.StringUtil;

/**
 * The standard tags for a SAM record that are defined in the SAM spec.
 */
public enum SAMTag {
    AM,
    AS,
    BC,
    BQ,
    BZ,
    CB,
    CC,
    CG,
    CM,
    CO,
    CP,
    CQ,
    CR,
    CS,
    CT,
    CY,
    E2,
    FI,
    FS,
    FT,
    FZ,
    /** @deprecated for backwards compatibility only */
    @Deprecated
    GC,
    /** @deprecated for backwards compatibility only */
    @Deprecated
    GS, // for backwards compatibility
    /** @deprecated for backwards compatibility only */
    @Deprecated
    GQ,
    LB,
    H0,
    H1,
    H2,
    HI,
    IH,
    MC,
    /** @deprecated  for backwards compatibility only */
    @Deprecated
    MF,
    MI,
    MD,
    MQ,
    NH,
    NM,
    OQ,
    OP,
    OC,
    OF,
    OR,
    OX,
    PG,
    PQ,
    PT,
    PU,
    QT,
    Q2,
    QX,
    R2,
    RG,
    /**
     * @deprecated use BC instead, for backwards compatibilty only
     */
    @Deprecated
    RT,
    RX,
    /** @deprecated for backwards compatibility only */
    @Deprecated
    S2, // for backwards compatibility
    SA,
    SM,
    /** @deprecated  for backwards compatibility only */
    @Deprecated
    SQ, // for backwards compatibility
    TC,
    U2,
    UQ;

    // Cache of already-converted tags.  Should speed up SAM text generation.
    // Not synchronized because race condition is not a problem.
    private static final String[] stringTags = new String[Short.MAX_VALUE];

    private final short shortValue = SAMTag.makeBinaryTag(this.name());

    /**
     * Convert from String representation of tag name to short representation.
     *
     * @param tag 2-character String representation of a tag name.
     * @return Tag name packed as 2 ASCII bytes in a short.
     */
    public static short makeBinaryTag(String tag) {
        if (tag.length() != 2) {
            throw new IllegalArgumentException("String tag does not have length() == 2: " + tag);
        }
        return (short)(tag.charAt(1) << 8 | tag.charAt(0));
    }

    /**
     * Convert from short representation of tag name to String representation.
     *
     * @param tag Tag name packed as 2 ASCII bytes in a short.
     * @return 2-character String representation of a tag name.
     */
    public static String makeStringTag(final short tag) {
        String ret = stringTags[tag];
        if (ret == null) {
            final byte[] stringConversionBuf = new byte[2];
            stringConversionBuf[0] = (byte)(tag & 0xff);
            stringConversionBuf[1] = (byte)((tag >> 8) & 0xff);
            ret = StringUtil.bytesToString(stringConversionBuf);
            stringTags[tag] = ret;
        }
        return ret;
    }

    /**
     * Get the binary representation of this tag name.
     * @see SAMTag#makeBinaryTag(String)
     *
     * @return the binary representation of this tag name
     */
    public short getBinaryTag(){
        return this.shortValue;
    }
}
