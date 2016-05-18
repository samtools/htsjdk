/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
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

package htsjdk.samtools.filter;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamPairUtil;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.PeekableIterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Filtering Iterator which takes a filter and an iterator and iterates through only those records
 * which are not rejected by the filter.
 * <p/>
 * $Id$
 *
 * @author Kathleen Tibbetts
 */
public class FilteringSamIterator implements CloseableIterator<SAMRecord> {

    private final PeekableIterator<SAMRecord> iterator;
    private final SamRecordFilter filter;
    private boolean filterReadPairs = false;
    private SAMRecord next = null;

    /**
     * Constructor
     *
     * @param iterator     the backing iterator
     * @param filter       the filter (which may be a FilterAggregator)
     * @param filterByPair if true, filter reads in pairs
     */
    public FilteringSamIterator(final Iterator<SAMRecord> iterator, final SamRecordFilter filter,
                                final boolean filterByPair) {

        if (filterByPair && iterator instanceof SAMRecordIterator) {
            ((SAMRecordIterator)iterator).assertSorted(SAMFileHeader.SortOrder.queryname);
        }

        this.iterator = new PeekableIterator<SAMRecord>(iterator);
        this.filter = filter;
        this.filterReadPairs = filterByPair;
        next = getNextRecord();
    }

    /**
     * Constructor
     *
     * @param iterator the backing iterator
     * @param filter   the filter (which may be a FilterAggregator)
     */
    public FilteringSamIterator(final Iterator<SAMRecord> iterator, final SamRecordFilter filter) {
        this.iterator = new PeekableIterator<SAMRecord>(iterator);
        this.filter = filter;
        next = getNextRecord();
    }

    /**
     * Returns true if the iteration has more elements.
     *
     * @return true if the iteration has more elements.  Otherwise returns false.
     */
    public boolean hasNext() {
        return next != null;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration
     * @throws java.util.NoSuchElementException
     *
     */
    public SAMRecord next() {
        if (next == null) {
            throw new NoSuchElementException("Iterator has no more elements.");
        }
        final SAMRecord result = next;
        next = getNextRecord();
        return result;
    }

    /**
     * Required method for Iterator API.
     *
     * @throws UnsupportedOperationException
     */
    public void remove() {
        throw new UnsupportedOperationException("Remove() not supported by FilteringSamIterator");
    }

    public void close() {
        CloserUtil.close(iterator);
    }

    /**
     * Gets the next record from the underlying iterator that passes the filter
     *
     * @return SAMRecord    the next filter-passing record
     */
    private SAMRecord getNextRecord() {

        while (iterator.hasNext()) {
            final SAMRecord record = iterator.next();

            if (filterReadPairs && record.getReadPairedFlag() && record.getFirstOfPairFlag() &&
                iterator.hasNext()) {

                SamPairUtil.assertMate(record, iterator.peek());

                if (filter.filterOut(record, iterator.peek())) {
                    // skip second read
                    iterator.next();
                } else {
                    return record;
                }
            } else if (filterReadPairs && record.getReadPairedFlag() &&
                record.getSecondOfPairFlag()) {
                // assume that we did a pass(first, second) and it passed the filter
                return record;
            } else if (!filter.filterOut(record)) {
                return record;
            }
        }

        return null;
    }
}