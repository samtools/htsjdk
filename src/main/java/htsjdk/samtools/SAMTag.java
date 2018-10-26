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
    /** for backwards compatibility only */
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

    private final short shortValue = SAMTagUtil.makeBinaryTag(this.name());

    /**
     * Get the binary representation of this tag name.
     * @see SAMTagUtil#makeBinaryTag(String)
     *
     * @return the binary representation of this tag name
     */
    public short getBinaryTag(){
        return this.shortValue;
    }
}