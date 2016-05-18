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

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.PeekableIterator;

import java.util.Comparator;

/**
 * Iterator for SAM records that implements comparable to enable sorting of iterators.
 * The comparison is performed by comparing the next record in the iterator to the next
 * record in another iterator and returning the ordering between those SAM records.
 */
class ComparableSamRecordIterator extends PeekableIterator<SAMRecord> implements Comparable<ComparableSamRecordIterator> {
    private final Comparator<SAMRecord> comparator;
    private final SamReader reader;

    /**
     * Constructs a wrapping iterator around the given iterator that will be able
     * to compare itself to other ComparableSamRecordIterators using the given comparator.
     *
     * @param iterator   the wrapped iterator.
     * @param comparator the Comparator to use to provide ordering fo SAMRecords
     */
    public ComparableSamRecordIterator(final SamReader sam, final CloseableIterator<SAMRecord> iterator, final Comparator<SAMRecord> comparator) {
        super(iterator);
        this.reader = sam;
        this.comparator = comparator;
    }

    /** Returns the reader from which this iterator was constructed. */
    public SamReader getReader() {
        return reader;
    }

    /**
     * Compares this iterator to another comparable iterator based on the next record
     * available in each iterator.  If the two comparable iterators have different
     * comparator types internally an exception is thrown.
     *
     * @param that another iterator to compare to
     * @return a negative, 0 or positive number as described in the Comparator interface
     */
    public int compareTo(final ComparableSamRecordIterator that) {
        if (this.comparator.getClass() != that.comparator.getClass()) {
            throw new IllegalStateException("Attempt to compare two ComparableSAMRecordIterators that " +
                    "have different orderings internally");
        }

        final SAMRecord record = this.peek();
        final SAMRecord record2 = that.peek();
        return comparator.compare(record, record2);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return compareTo((ComparableSamRecordIterator) o) == 0;
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("ComparableSamRecordIterator should not be hashed because it can change value");
    }
}
