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
 * Comparator for "queryname" ordering of SAMRecords.
 */
public class SAMRecordQueryNameComparator implements SAMRecordComparator, Serializable {
    private static final long serialVersionUID = 1L;

    private static boolean isDigit(final char c) {
        return Character.isDigit(c);
    }

    @Override
    public int compare(final SAMRecord samRecord1, final SAMRecord samRecord2) {
        final int retval = compareReadNames(samRecord1.getReadName(), samRecord2.getReadName());
        if (retval == 0) return Integer.compare(samRecord1.getFlags()&0xc0, samRecord2.getFlags()&0xc0);
        else return retval;
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
        return compareReadNames(samRecord1.getReadName(), samRecord2.getReadName());
    }

    /**
     * Encapsulate algorithm for comparing read names in queryname-sorted file, since there have been
     * conversations about changing the behavior.
     */
    public static int compareReadNames(final String name1, final String name2) {
        int index1 = 0;
        int index2 = 0;
        final int len1 = name1.length();
        final int len2 = name2.length();

        // keep going while we have characters left
        while (index1 < len1 && index2 < len2) {
            if (!isDigit(name1.charAt(index1)) || !isDigit(name2.charAt(index2))) { // no more
                if (name1.charAt(index1) != name2.charAt(index2)) {
                    return (int) name1.charAt(index1) - (int) name2.charAt(index2);
                } else {
                    index1++;
                    index2++;
                }
            } else { // next characters are digits
                // skip over leading zeros
                while (index1 < len1 && name1.charAt(index1) == '0') index1++;
                while (index2 < len2 && name2.charAt(index2) == '0') index2++;
                // skip over any matching digits
                while (index1 < len1 && index2 < len2 &&
                        isDigit(name1.charAt(index1)) &&
                        isDigit(name2.charAt(index2)) &&
                        name1.charAt(index1) == name2.charAt(index2)) {
                    index1++;
                    index2++;
                }

                boolean isDigit1 = index1 < len1 && isDigit(name1.charAt(index1));
                boolean isDigit2 = index2 < len2 && isDigit(name2.charAt(index2));

                if (isDigit1 && isDigit2) {
                    // skip until we hit a non-digit or the end.
                    int i = 0;
                    while (index1 + i < len1 && index2 + i < len2 &&
                            isDigit(name1.charAt(index1 + i)) &&
                            isDigit(name2.charAt(index2 + i))) {
                        i++;
                    }
                    if (index1 + i < len1 && isDigit(name1.charAt(index1 + i))) return 1;
                    else if (index2 + i < len2 && isDigit(name2.charAt(index2 + i))) return -1;
                    else return (int)name1.charAt(index1) - (int)name2.charAt(index2);
                }
                else if (isDigit1) return 1;
                else if (isDigit2) return -1;
                else if (index1 != index2) return index2 - index1;
            }
        }

        if (index1 < len1) return 1;
        else if (index2 < len2) return -1;
        else return 0;
    }
}
