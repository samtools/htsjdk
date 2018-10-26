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
 * is used by SAM JDK internally and is much more efficient.  Callers are encouraged to obtain the short
 * value for a tag of interest once, and then use the SAMRecord attribute API that takes shorts rather than
 * Strings.
 *
 * @author alecw@broadinstitute.org
 */
public final class SAMTagUtil {

    /**
     * This constructor is public despite being a utility class for backwards compatibility reasons.
     */
    public SAMTagUtil(){}

    // Standard tags pre-computed for convenience
    public static final short AM = SAMTag.AM.getBinaryTag();
    public static final short AS = SAMTag.AS.getBinaryTag();
    public static final short BC = SAMTag.BC.getBinaryTag();
    public static final short BQ = SAMTag.BQ.getBinaryTag();
    public static final short BZ = SAMTag.BZ.getBinaryTag();
    public static final short CB = SAMTag.CB.getBinaryTag();
    public static final short CC = SAMTag.CC.getBinaryTag();
    public static final short CG = SAMTag.CG.getBinaryTag();
    public static final short CM = SAMTag.CM.getBinaryTag();
    public static final short CO = SAMTag.CO.getBinaryTag();
    public static final short CP = SAMTag.CP.getBinaryTag();
    public static final short CQ = SAMTag.CQ.getBinaryTag();
    public static final short CR = SAMTag.CR.getBinaryTag();
    public static final short CS = SAMTag.CS.getBinaryTag();
    public static final short CT = SAMTag.CT.getBinaryTag();
    public static final short CY = SAMTag.CY.getBinaryTag();
    public static final short E2 = SAMTag.E2.getBinaryTag();
    public static final short FI = SAMTag.FI.getBinaryTag();
    public static final short FS = SAMTag.FS.getBinaryTag();
    public static final short FT = SAMTag.FT.getBinaryTag();
    public static final short FZ = SAMTag.FZ.getBinaryTag();
    /** @deprecated reserved tag for backwards compatibility only */
    @Deprecated
    public static final short GC = SAMTag.GC.getBinaryTag();
    /** @deprecated reserved tag for backwards compatibility only */
    @Deprecated
    public static final short GS = SAMTag.GS.getBinaryTag();
    /** @deprecated reserved tag for backwards compatibility only */
    @Deprecated
    public static final short GQ = SAMTag.GQ.getBinaryTag();
    public static final short LB = SAMTag.LB.getBinaryTag();
    public static final short H0 = SAMTag.H0.getBinaryTag();
    public static final short H1 = SAMTag.H1.getBinaryTag();
    public static final short H2 = SAMTag.H2.getBinaryTag();
    public static final short HI = SAMTag.HI.getBinaryTag();
    public static final short IH = SAMTag.IH.getBinaryTag();
    public static final short MC = SAMTag.MC.getBinaryTag();
    /** @deprecated reserved tag for backwards compatibility only */
    @Deprecated
    public static final short MF = SAMTag.MF.getBinaryTag();
    public static final short MI = SAMTag.MI.getBinaryTag();
    public static final short MD = SAMTag.MD.getBinaryTag();
    public static final short MQ = SAMTag.MQ.getBinaryTag();
    public static final short NH = SAMTag.NH.getBinaryTag();
    public static final short NM = SAMTag.NM.getBinaryTag();
    public static final short OQ = SAMTag.OQ.getBinaryTag();
    public static final short OP = SAMTag.OP.getBinaryTag();
    public static final short OC = SAMTag.OC.getBinaryTag();
    public static final short OF = SAMTag.OF.getBinaryTag();
    public static final short OR = SAMTag.OR.getBinaryTag();
    public static final short OX = SAMTag.OX.getBinaryTag();
    public static final short PG = SAMTag.PG.getBinaryTag();
    public static final short PQ = SAMTag.PQ.getBinaryTag();
    public static final short PT = SAMTag.PT.getBinaryTag();
    public static final short PU = SAMTag.PU.getBinaryTag();
    public static final short QT = SAMTag.QT.getBinaryTag();
    public static final short Q2 = SAMTag.Q2.getBinaryTag();
    public static final short QX = SAMTag.QX.getBinaryTag();
    public static final short R2 = SAMTag.R2.getBinaryTag();
    public static final short RG = SAMTag.RG.getBinaryTag();
    /** @deprecated use BC instead */
    @Deprecated
    public static final short RT = SAMTag.RT.getBinaryTag();
    public static final short RX = SAMTag.RX.getBinaryTag();
    /** @deprecated reserved tag for backwards compatibility only */
    @Deprecated
    public static final short S2 = SAMTag.S2.getBinaryTag();
    public static final short SA = SAMTag.SA.getBinaryTag();
    public static final short SM = SAMTag.SM.getBinaryTag();
    /** @deprecated reserved tag for backwards compatibility only */
    @Deprecated
    public static final short SQ = SAMTag.SQ.getBinaryTag();
    public static final short TC = SAMTag.TC.getBinaryTag();
    public static final short U2 = SAMTag.U2.getBinaryTag();
    public static final short UQ = SAMTag.UQ.getBinaryTag();

    private static final SAMTagUtil singleton = new SAMTagUtil();

    // Cache of already-converted tags.  Should speed up SAM text generation.
    // Not synchronized because race condition is not a problem.
    private static final String[] stringTags = new String[Short.MAX_VALUE];

    /**
     * Despite the fact that this class has state, it should be thread-safe because the cache
     * gets filled with the same values by any thread.
     * @deprecated All methods on this class have been made static, use them directly
     */
    @Deprecated
    public static SAMTagUtil getSingleton() {
        return singleton;
    }

    /**
     * Convert from String representation of tag name to short representation.
     *
     * @param tag 2-character String representation of a tag name.
     * @return Tag name packed as 2 ASCII bytes in a short.
     */
    public static short makeBinaryTag(final String tag) {
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
}
