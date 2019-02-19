/*
 * The MIT License
 *
 * Copyright (c) 2019 Nils Homer
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

import htsjdk.samtools.SAMFileHeader;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Comparator that orders intervals based on their sequence index, by coordinate
 * then by strand and finally by name.
 */
public class IntervalCoordinateComparator implements Comparator<Interval>, Serializable {
    private static final long serialVersionUID = 1L;

    private final SAMFileHeader header;

    /** Constructs a comparator using the supplied sequence header. */
    public IntervalCoordinateComparator(final SAMFileHeader header) {
        this.header = header;
    }

    @Override
    public int compare(final Interval lhs, final Interval rhs) {
        final int lhsIndex = this.header.getSequenceIndex(lhs.getContig());
        final int rhsIndex = this.header.getSequenceIndex(rhs.getContig());
        int retval = lhsIndex - rhsIndex;

        if (retval == 0) {
            retval = lhs.getStart() - rhs.getStart();
        }
        if (retval == 0) {
            retval = lhs.getEnd() - rhs.getEnd();
        }
        if (retval == 0) {
            if (lhs.isPositiveStrand() && rhs.isNegativeStrand()) {
                retval = -1;
            } else if (lhs.isNegativeStrand() && rhs.isPositiveStrand()) {
                retval = 1;
            }
        }
        if (retval == 0) {
            if (lhs.getName() == null) {
                if (rhs.getName() == null) {
                    return 0;
                } else {
                    return -1;
                }
            } else if (rhs.getName() == null) {
                return 1;
            } else {
                return lhs.getName().compareTo(rhs.getName());
            }
        }

        return retval;
    }
}
