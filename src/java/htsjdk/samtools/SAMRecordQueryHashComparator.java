/*
 * The MIT License
 *
 * Copyright (c) 2015 Tim Fennell
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

import htsjdk.samtools.util.Murmur3;

import java.io.Serializable;

/**
 * SAMRecord comparator that provides an ordering based on a hash of the queryname. Has
 * the useful property that reads with the same name will be grouped together, but that
 * reads appear in an otherwise random order.  Useful for when the read names in a BAM
 * are correlated to something else (e.g. position, read group), making a straight
 * queryname sort undesirable.
 *
 * @author Tim Fennell
 */
public class SAMRecordQueryHashComparator extends SAMRecordQueryNameComparator implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Murmur3 hasher = new Murmur3(42);

    /**
     * Compares two records based on an integer hash of their read name's. If the hash
     * values are equal, falls back to the behaviour of SAMRecordQueryNameComparator
     * to break the tie.
     */
    @Override
    public int compare(final SAMRecord lhs, final SAMRecord rhs) {
        final int retval = compareHashes(lhs, rhs);
        if (retval == 0) return super.compare(lhs, rhs);
        else return retval;
    }

    /**
     * Compares two records based on an integer hash of their read names. If the hash
     * values are equal, falls back to the behaviour of SAMRecordQueryNameComparator
     * to break the tie.
     */
    @Override
    public int fileOrderCompare(final SAMRecord lhs, final SAMRecord rhs) {
        final int retval = compareHashes(lhs, rhs);
        if (retval == 0) return super.fileOrderCompare(lhs, rhs);
        else return retval;
    }

    /** Compares the hash values for two records. */
    private int compareHashes(final SAMRecord lhs, final SAMRecord rhs) {
        return Integer.compare(this.hasher.hashUnencodedChars(lhs.getReadName()), this.hasher.hashUnencodedChars(rhs.getReadName()));
    }
}
