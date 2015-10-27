/*
 * The MIT License
 *
 * Copyright (c) 2015 The Broad Institute
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

/**
 * An iterator of SAMRecords that can downsample on the fly.  Allows for inclusion of secondary and/or
 * supplemental records (off by default), though this will cause memory use to increase as the decisions
 * for each read name must be cached permanently.
 *
 * @author Tim Fennell
 */
public class DownsamplingIterator implements CloseableIterator<SAMRecord>, Iterable<SAMRecord> {
    private final Iterator<SAMRecord> underlyingIterator;
    private final Random random;
    private final double probabilityOfKeeping;
    private SAMRecord nextRecord;
    private long totalReads, keptReads;
    private final Map<String, Boolean> decisions = new HashMap<String, Boolean>();
    private boolean allowSecondaryAlignments = false;
    private boolean allowSupplementalAlignments = false;
    private boolean includeNoRefReads = true;

    /** Constructs a downsampling iterator upon the supplied iterator, using the Random as the source of randomness. */
    public DownsamplingIterator(final Iterator<SAMRecord> iterator, final Random random, final double probabilityOfKeeping) {
        this.underlyingIterator = iterator;
        this.random = random;
        this.probabilityOfKeeping = probabilityOfKeeping;
    }

    /** Sets whether or not secondary alignments are allowed (true) or all discarded (false). */
    public DownsamplingIterator setAllowSecondaryAlignments(final boolean allowSecondaryAlignments) {
        this.allowSecondaryAlignments = allowSecondaryAlignments;
        return this;
    }

    /** Sets whether or not supplemental alignments are allowed (true) or all discarded (false). */
    public DownsamplingIterator setAllowSupplementalAlignments(final boolean allowSupplementalAlignments) {
        this.allowSupplementalAlignments = allowSupplementalAlignments;
        return this;
    }

    /** Sets whether the iterator will stop when no-ref reads are encountered, or keep downsampling through them. */
    public DownsamplingIterator setIncludeNoRefReads(final boolean includeNoRefReads) {
        this.includeNoRefReads = includeNoRefReads;
        return this;
    }

    /** Returns the total number of reads/records considered up to the point when the method is called. */
    public long getTotalReads() { return totalReads; }

    /** Returns the number of reads/records kept post-downsampling up to the point when the method is called. */
    public long getKeptReads() { return keptReads; }

    /** Simple implementation of iterable that returns this iterator. */
    @Override public Iterator<SAMRecord> iterator() { return this; }

    /**
     * Clears the current record and attempts to advance through the underlying iterator until a
     * record is kept during downsampling.  If no more records are kept and the end of the input
     * is reached this.nextRecord will be null.
     *
     * @return true if a record is available after advancing, false otherwise
     */
    private boolean advance() {
        this.nextRecord = null;
        final boolean oneRecPerRead = !allowSecondaryAlignments && !allowSupplementalAlignments;

        while (this.nextRecord == null && this.underlyingIterator.hasNext()) {
            final SAMRecord rec = this.underlyingIterator.next();
            if (!this.allowSecondaryAlignments    && rec.getNotPrimaryAlignmentFlag()) continue;
            if (!this.allowSupplementalAlignments && rec.getSupplementaryAlignmentFlag()) continue;
            if (!this.includeNoRefReads && rec.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) break;

            ++totalReads;

            final String key = rec.getReadName();
            final Boolean previous = oneRecPerRead ? decisions.remove(key) : decisions.get(key);
            final boolean keeper;

            if (previous == null) {
                keeper = this.random.nextDouble() <= this.probabilityOfKeeping;
                if (rec.getReadPairedFlag() || this.allowSecondaryAlignments || this.allowSupplementalAlignments) decisions.put(key, keeper);
            }
            else {
                keeper = previous;
            }

            if (keeper) {
                this.nextRecord = rec;
                ++keptReads;
            }
        }

        return this.nextRecord != null;
    }

    /** Returns true if there is another record available post-downsampling, false otherwise. */
    @Override public boolean hasNext() {
        return this.nextRecord != null || advance();
    }

    /** Returns the next record from the iterator, or throws an exception if there is no next record. */
    @Override public SAMRecord next() {
        if (this.nextRecord == null) {
            throw new NoSuchElementException("Call to next() when hasNext() == false");
        }
        else {
            final SAMRecord retval = this.nextRecord;
            advance();
            return retval;
        }
    }

    /** Unsupported operation. */
    @Override public void remove() {
        throw new UnsupportedOperationException("remove() is not supported.");
    }

    @Override public void close() {
        // Do nothing.
    }
}
