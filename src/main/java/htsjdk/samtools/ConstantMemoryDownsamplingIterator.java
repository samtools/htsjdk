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
import htsjdk.samtools.util.PeekableIterator;

import java.util.Iterator;

/**
 * A DownsamplingIterator that runs in constant (and very small) memory. For each read the read name is hashed
 * using the Murmur3_32 hash algorithm to obtain an integer value that is, enough for our purposes, uniformly
 * distributed between the min and max int values even for highly similar inputs.  The proportion is used to
 * calculate a maximum acceptable hash value within the range.  Records whose hash value is below the limit
 * are emitted, records whose hash value is above the limit are discarded.
 *
 * Does not make any attempt to be accurate (have actual proportion == requested proportion) beyond what would
 * be expected for a random process and so may become quite inaccurate when downsampling to small numbers of
 * reads.
 *
 * @author Tim Fennell
 */
class ConstantMemoryDownsamplingIterator extends DownsamplingIterator {
    private final PeekableIterator<SAMRecord> underlyingIterator;
    private final int maxHashValue;
    private final Murmur3 hasher;


    /** Constructs a downsampling iterator upon the supplied iterator, using the Random as the source of randomness. */
    ConstantMemoryDownsamplingIterator(final Iterator<SAMRecord> iterator, final double proportion, final int seed) {
        super(proportion);
        this.hasher = new Murmur3(seed);
        this.underlyingIterator = new PeekableIterator<SAMRecord>(iterator);

        final long range = (long) Integer.MAX_VALUE - (long) Integer.MIN_VALUE;
        this.maxHashValue = Integer.MIN_VALUE + (int) Math.round(range * proportion);

        advanceToNextAcceptedRead();
    }

    /** Returns true if there is another record available post-downsampling, false otherwise. */
    @Override public boolean hasNext() {
        // The underlying iterator is always left at the next return-able read, so if it has a next read, so do we
        return this.underlyingIterator.hasNext();
    }

    /**
     * Advances the underlying, peekable, iterator until the next records is one that is to be emitted.
     * @return true if there is at least one emittable record ready for emission, false otherwise
     */
    private boolean advanceToNextAcceptedRead() {
        while (this.underlyingIterator.hasNext() && this.hasher.hashUnencodedChars(this.underlyingIterator.peek().getReadName()) > this.maxHashValue) {
            this.underlyingIterator.next();
            recordDiscardedRecord();
        }

        return this.underlyingIterator.hasNext();
    }

    /** Returns the next record from the iterator, or throws an exception if there is no next record. */
    @Override public SAMRecord next() {
        final SAMRecord rec = this.underlyingIterator.next();
        recordAcceptedRecord();
        advanceToNextAcceptedRead();
        return rec;
    }
}
