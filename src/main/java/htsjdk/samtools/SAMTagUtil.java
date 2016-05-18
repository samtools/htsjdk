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
public class SAMTagUtil {

    // Standard tags pre-computed for convenience
    public final short RG = makeBinaryTag(SAMTag.RG.name());
    public final short LB = makeBinaryTag(SAMTag.LB.name());
    public final short PU = makeBinaryTag(SAMTag.PU.name());
    public final short PG = makeBinaryTag(SAMTag.PG.name());
    public final short AS = makeBinaryTag(SAMTag.AS.name());
    public final short SQ = makeBinaryTag(SAMTag.SQ.name());
    public final short MQ = makeBinaryTag(SAMTag.MQ.name());
    public final short NM = makeBinaryTag(SAMTag.NM.name());
    public final short H0 = makeBinaryTag(SAMTag.H0.name());
    public final short H1 = makeBinaryTag(SAMTag.H1.name());
    public final short H2 = makeBinaryTag(SAMTag.H2.name());
    public final short UQ = makeBinaryTag(SAMTag.UQ.name());
    public final short PQ = makeBinaryTag(SAMTag.PQ.name());
    public final short NH = makeBinaryTag(SAMTag.NH.name());
    public final short IH = makeBinaryTag(SAMTag.IH.name());
    public final short HI = makeBinaryTag(SAMTag.HI.name());
    public final short MD = makeBinaryTag(SAMTag.MD.name());
    public final short CS = makeBinaryTag(SAMTag.CS.name());
    public final short CQ = makeBinaryTag(SAMTag.CQ.name());
    public final short CM = makeBinaryTag(SAMTag.CM.name());
    public final short R2 = makeBinaryTag(SAMTag.R2.name());
    public final short Q2 = makeBinaryTag(SAMTag.Q2.name());
    public final short S2 = makeBinaryTag(SAMTag.S2.name());
    public final short CC = makeBinaryTag(SAMTag.CC.name());
    public final short CP = makeBinaryTag(SAMTag.CP.name());
    public final short SM = makeBinaryTag(SAMTag.SM.name());
    public final short AM = makeBinaryTag(SAMTag.AM.name());
    public final short MF = makeBinaryTag(SAMTag.MF.name());
    public final short E2 = makeBinaryTag(SAMTag.E2.name());
    public final short U2 = makeBinaryTag(SAMTag.U2.name());
    public final short OQ = makeBinaryTag(SAMTag.OQ.name());
    public final short FZ = makeBinaryTag(SAMTag.FZ.name());
    public final short SA = makeBinaryTag(SAMTag.SA.name());
    public final short MC = makeBinaryTag(SAMTag.MC.name());

    private static SAMTagUtil singleton;

    // Cache of already-converted tags.  Should speed up SAM text generation.
    // Not synchronized because race condition is not a problem.
    private final String[] stringTags = new String[Short.MAX_VALUE];

    /**
     * Despite the fact that this class has state, it should be thread-safe because the cache
     * gets filled with the same values by any thread.
     */
    public static SAMTagUtil getSingleton() {
        if (singleton == null) {
            singleton = new SAMTagUtil();
        }
        return singleton;
    }


    /**
     * Convert from String representation of tag name to short representation.
     *
     * @param tag 2-character String representation of a tag name.
     * @return Tag name packed as 2 ASCII bytes in a short.
     */
    public short makeBinaryTag(final String tag) {
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
    public String makeStringTag(final short tag) {
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
