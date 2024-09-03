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
 * Comparator for natural "queryname" (i.e. SO:queryname SS:natural) ordering of SAMRecords.
 */
public class SAMRecordQueryNameNaturalComparator extends SAMRecordQueryNameComparator implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Compares two strings in a "natural" order. */
    protected int compare(final String a, final String b) {
        final int lenA = a.length(), lenB = b.length();
        int ia = 0, ib = 0;

        while (ia < lenA && ib < lenB) {
            final char chA = a.charAt(ia);
            final char chB = b.charAt(ib);
            final boolean aIsDigit = Character.isDigit(chA);
            final boolean bIsDigit = Character.isDigit(chB);
            ia += 1;
            ib += 1;

            if (aIsDigit && bIsDigit) {
                long numA = Character.digit(chA, 10);
                int digitsA = 1;
                while (ia < lenA && Character.isDigit(a.charAt(ia))) {
                    numA *= 10;
                    numA += Character.digit(a.charAt(ia), 10);
                    ia += 1;
                    digitsA += 1;
                }

                long numB = Character.digit(chB, 10);
                int digitsB = 1;
                while (ib < lenB && Character.isDigit(b.charAt(ib))) {
                    numB *= 10;
                    numB += Character.digit(b.charAt(ib), 10);
                    ib += 1;
                    digitsB += 1;
                }

                int comp = Long.compare(numA, numB);
                if (comp == 0) comp = digitsB - digitsA; // more leading zeros sorts first
                if (comp != 0) return comp;
            }
            else {
                final int comp = Character.compare(chA, chB);
                if (comp != 0) return comp;
            }
        }

        // The strings are equal until one or both are out of characters to difference
        // is solely based on length
        return lenA - lenB;
    }

    /**
     * Less stringent compare method than the regular compare.  If the two records
     * are equal enough that their ordering in a sorted SAM file would be arbitrary,
     * this method returns 0.
     *
     * @return negative if samRecord1 < samRecord2,  0 if equal, else positive
     */
    @Override
    public int fileOrderCompare(final SAMRecord samRecord1, final SAMRecord samRecord2) {
        return compare(samRecord1.getReadName(), samRecord2.getReadName());
    }
}
