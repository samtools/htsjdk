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

/**
 * A interface representing a collection of (possibly) discontinuous segments in the
 * BAM file, possibly representing the results of an index query.
 */
public interface SAMFileSpan extends Cloneable {
    /**
     * Gets a pointer over the data immediately following this span.
     * @return The a pointer to data immediately following this span.
     */
    public SAMFileSpan getContentsFollowing();

    /**
     * Remove all pointers in this file span before the given file span starts.
     * @param fileSpan The filespan before which to eliminate.
     * @return The portion of the chunk list after the given chunk.
     */
    public SAMFileSpan removeContentsBefore(final SAMFileSpan fileSpan);

    /**
     * Does this file span point to any data, or is it completely empty?
     * @return True if the file span is empty, false otherwise.
     */
    public boolean isEmpty();
}
