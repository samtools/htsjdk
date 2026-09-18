/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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
package htsjdk.tribble.index.tabix;

import htsjdk.index.BinningIndex;

/**
 * A feature whose start in the file is known but whose end is not: it ends where the next feature, or the end of
 * the file, begins.
 */
final class PendingFeature {
    private final int referenceIndex;
    private final int start;
    private final int end;
    private final long startFilePosition;

    /**
     * @param referenceIndex ordinal of the feature's sequence in the index
     * @param start 1-based inclusive start
     * @param end 1-based inclusive end
     * @param startFilePosition virtual offset at which the feature begins in the file
     */
    PendingFeature(final int referenceIndex, final int start, final int end, final long startFilePosition) {
        this.referenceIndex = referenceIndex;
        this.start = start;
        this.end = end;
        this.startFilePosition = startFilePosition;
    }

    /** Adds this feature to the index now that the feature following it in the file is known. */
    void addTo(final BinningIndex.Builder indexBuilder, final PendingFeature next) {
        if (referenceIndex > next.referenceIndex || (referenceIndex == next.referenceIndex && start > next.start)) {
            throw new IllegalArgumentException(
                    String.format("Features added out of order: previous (%s) > next (%s)", this, next));
        }
        addTo(indexBuilder, next.startFilePosition);
    }

    /** Adds this feature to the index given the file position at which it ends. */
    void addTo(final BinningIndex.Builder indexBuilder, final long endFilePosition) {
        if (startFilePosition >= endFilePosition) {
            throw new IllegalArgumentException(String.format(
                    "Feature start position %d >= feature end position %d", startFilePosition, endFilePosition));
        }
        indexBuilder.add(referenceIndex, start, end, startFilePosition, endFilePosition);
    }

    @Override
    public String toString() {
        return "PendingFeature{referenceIndex=" + referenceIndex + ", start=" + start + ", end=" + end
                + ", startFilePosition=" + startFilePosition + '}';
    }
}
