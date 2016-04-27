/*
 * The MIT License
 *
 * Copyright (c) 2016 Tim Fennell
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
package htsjdk.samtools.util;

/**
 * Utility code for performing quality trimming.
 *
 * @author Tim Fennell
 */
public class TrimmingUtil {
    /**
     * Implements phred-style quality trimming. Takes in an array of quality values as a
     * byte[] and return the first index within the byte array that should be clipped,
     * such that the caller can then invoke things like:
     *     int retval = findQualityTrimPoint(10, quals);
     *     final byte[] trimmedQuals = Arrays.copyOfRange(quals, 0, retval);
     *     final String trimmedBases = bases.substring(0, retval);
     *
     * If the entire read is of low quality this function may return 0!  It is left to the caller
     * to decide whether or not to trim reads down to 0-bases, or to enforce some minimum length.
     *
     * @param quals a byte[] of quality scores in phred scaling (i.e. integer values between 0 and ~60)
     * @param trimQual the lowest quality that is considered "good". In the simplest case
     *                 where a read is composed exclusively of "good" qualities followed by
     *                 "bad" qualities, this is the lowest quality value left after trimming.
     * @return The zero-based index of the first base within the quality string that should be trimmed.
     *         When no trimming is required, quals.length (i.e. an index one greater than the last valid
     *         index) will be returned.
     */
    public static int findQualityTrimPoint(final byte[] quals, final int trimQual) {
        final int length = quals.length;
        int score = 0, maxScore = 0, trimPoint = length;
        if (trimQual < 1 || length == 0) return 0;

        for (int i=length-1; i>=0; --i) {
            score += trimQual - (quals[i]);
            if (score < 0) break;
            if (score > maxScore) {
                maxScore = score;
                trimPoint = i;
            }
        }

        return trimPoint;
    }
}
