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
 * Facility for converting between String and short representation of a SAM tag.  short representation
 * is used by HTSJDK internally and is much more efficient.  Callers are encouraged to obtain the short
 * value for a tag of interest once, and then use the SAMRecord attribute API that takes shorts rather than
 * Strings.
 *
 * Tags that are defined by the SAM spec are included in the enum {@link SAMTag} along with their precomputed short tag.
 *
 * @author alecw@broadinstitute.org
 * @deprecated as of 11/2018, the functions in this class have been absorbed by the {@link SAMTag} enum.
 */
@Deprecated
public class SAMTagUtil {

    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short AM = SAMTag.AM.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short AS = SAMTag.AS.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short BC = SAMTag.BC.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short BQ = SAMTag.BQ.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short BZ = SAMTag.BZ.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short CB = SAMTag.CB.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short CC = SAMTag.CC.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short CG = SAMTag.CG.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short CM = SAMTag.CM.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short CO = SAMTag.CO.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short CP = SAMTag.CP.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short CQ = SAMTag.CQ.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short CR = SAMTag.CR.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short CS = SAMTag.CS.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short CT = SAMTag.CT.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short CY = SAMTag.CY.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short E2 = SAMTag.E2.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short FI = SAMTag.FI.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short FS = SAMTag.FS.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short FT = SAMTag.FT.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short FZ = SAMTag.FZ.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short GC = SAMTag.GC.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short GS = SAMTag.GS.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short GQ = SAMTag.GQ.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short LB = SAMTag.LB.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short H0 = SAMTag.H0.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short H1 = SAMTag.H1.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short H2 = SAMTag.H2.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short HI = SAMTag.HI.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short IH = SAMTag.IH.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short MC = SAMTag.MC.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short MF = SAMTag.MF.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short MI = SAMTag.MI.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short MD = SAMTag.MD.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short MQ = SAMTag.MQ.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short NH = SAMTag.NH.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short NM = SAMTag.NM.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short OQ = SAMTag.OQ.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short OP = SAMTag.OP.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short OC = SAMTag.OC.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short OF = SAMTag.OF.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short OR = SAMTag.OR.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short OX = SAMTag.OX.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short PG = SAMTag.PG.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short PQ = SAMTag.PQ.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short PT = SAMTag.PT.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short PU = SAMTag.PU.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short QT = SAMTag.QT.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short Q2 = SAMTag.Q2.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short QX = SAMTag.QX.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short R2 = SAMTag.R2.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short RG = SAMTag.RG.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short RT = SAMTag.RT.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short RX = SAMTag.RX.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short S2 = SAMTag.S2.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short SA = SAMTag.SA.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short SM = SAMTag.SM.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short SQ = SAMTag.SQ.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short TC = SAMTag.TC.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short U2 = SAMTag.U2.getBinaryTag();
    /** @deprecated use {@link SAMTag#getBinaryTag()} instead. */
    @Deprecated public final short UQ = SAMTag.UQ.getBinaryTag();

    private static final SAMTagUtil SINGLETON = new SAMTagUtil();

    /**
     * Despite the fact that this class has state, it should be thread-safe because the cache
     * gets filled with the same values by any thread.
     * @deprecated use the static methods in {@link SAMTag} instead
     */
    @Deprecated
    public static SAMTagUtil getSingleton() {
        return SINGLETON;
    }

    /**
     * Convert from String representation of tag name to short representation.
     *
     * @param tag 2-character String representation of a tag name.
     * @return Tag name packed as 2 ASCII bytes in a short.
     * @deprecated u
     */
    @Deprecated
    public short makeBinaryTag(final String tag) {
        return SAMTag.makeBinaryTag(tag);
    }

    /**
     * Convert from short representation of tag name to String representation.
     *
     * @param tag Tag name packed as 2 ASCII bytes in a short.
     * @return 2-character String representation of a tag name.
     * @deprecated use {@link SAMTag#makeStringTag(short)} instead
     */
    @Deprecated
    public String makeStringTag(final short tag) {
      return SAMTag.makeStringTag(tag);
    }
}
