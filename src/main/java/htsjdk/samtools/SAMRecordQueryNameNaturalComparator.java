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

import java.io.Serializable;

/**
 * Comparator for natural "queryname" ordering of SAMRecords, matching the order that samtools/htslib
 * produce for a queryname-natural sort (i.e. SAM {@code @HD SO:queryname SS:queryname:natural}).
 *
 * <p>Read names are compared so that maximal runs of digits are ordered by numeric value rather than
 * lexicographically, so e.g. {@code read2} sorts before {@code read10}. Everything else (the pairing,
 * strand, secondary/supplementary and {@code HI} tie-breaking) is inherited unchanged from
 * {@link SAMRecordQueryNameComparator}; only the read-name comparison differs.</p>
 *
 * <p>The read-name comparison is a faithful port of htslib's {@code strnum_cmp}. It walks the two
 * names digit-by-digit rather than parsing numeric runs into integers, so it produces the same result
 * as samtools even for numeric runs too large to fit in a {@code long}.</p>
 */
public class SAMRecordQueryNameNaturalComparator extends SAMRecordQueryNameComparator implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Compares two read names using natural ordering, matching htslib's {@code strnum_cmp}.
     *
     * <p>Maximal runs of ASCII digits are compared as numbers; all other characters are compared by
     * their code point. Leading zeros are ignored, so numeric runs with the same value compare equal
     * (e.g. {@code 8}, {@code 08} and {@code 008}), matching samtools. Because the comparison never
     * parses a run into a number, it is correct for numeric runs of arbitrary length.</p>
     *
     * @return a negative number if {@code a < b}, zero if they are equal, a positive number if {@code a > b}
     */
    public static int compareNatural(final String a, final String b) {
        final int lenA = a.length();
        final int lenB = b.length();
        int ia = 0;
        int ib = 0;

        while (ia < lenA && ib < lenB) {
            final char chA = a.charAt(ia);
            final char chB = b.charAt(ib);

            if (isDigit(chA) && isDigit(chB)) {
                // Skip leading zeros in each run so equal-value runs agree on their significant digits.
                while (ia < lenA && a.charAt(ia) == '0') ia++;
                while (ib < lenB && b.charAt(ib) == '0') ib++;

                // Skip the digits that the two runs have in common.
                while (ia < lenA
                        && ib < lenB
                        && isDigit(a.charAt(ia))
                        && isDigit(b.charAt(ib))
                        && a.charAt(ia) == b.charAt(ib)) {
                    ia++;
                    ib++;
                }

                final boolean aHasDigit = ia < lenA && isDigit(a.charAt(ia));
                final boolean bHasDigit = ib < lenB && isDigit(b.charAt(ib));

                if (aHasDigit && bHasDigit) {
                    // Both runs still have digits at the first differing position. The run with more
                    // remaining digits is the larger number; if they are the same length, the differing
                    // digit decides.
                    int i = 0;
                    while (ia + i < lenA && ib + i < lenB && isDigit(a.charAt(ia + i)) && isDigit(b.charAt(ib + i))) {
                        i++;
                    }
                    if (ia + i < lenA && isDigit(a.charAt(ia + i))) return 1;
                    if (ib + i < lenB && isDigit(b.charAt(ib + i))) return -1;
                    return a.charAt(ia) - b.charAt(ib);
                } else if (aHasDigit) {
                    return 1; // a's numeric run is longer, so it is the larger number
                } else if (bHasDigit) {
                    return -1; // b's numeric run is longer, so it is the larger number
                }
                // Otherwise the two runs have the same numeric value. Leading zeros do not affect the
                // ordering (matching samtools), so we simply keep comparing the characters that follow.
            } else {
                if (chA != chB) return chA - chB;
                ia++;
                ib++;
            }
        }

        // One name is a prefix of the other (or they are equal); the longer name sorts last.
        if (ia < lenA) return 1;
        if (ib < lenB) return -1;
        return 0;
    }

    /** Returns true if the character is an ASCII digit. Read names are ASCII per the SAM spec. */
    private static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Compares the read names of the two records using natural ordering. This is the only part of the
     * queryname comparison that differs from {@link SAMRecordQueryNameComparator}; the remaining
     * tie-breaking is inherited.
     *
     * @return negative if {@code samRecord1 < samRecord2}, 0 if their read names are equal, else positive
     */
    @Override
    public int fileOrderCompare(final SAMRecord samRecord1, final SAMRecord samRecord2) {
        return compareNatural(samRecord1.getReadName(), samRecord2.getReadName());
    }
}
