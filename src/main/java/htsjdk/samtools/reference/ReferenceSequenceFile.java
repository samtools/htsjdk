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

package htsjdk.samtools.reference;

import htsjdk.samtools.SAMSequenceDictionary;

import java.io.Closeable;
import java.io.IOException;

/**
 * An interface for working with files of reference sequences regardless of the file format
 * being used.
 *
 * @author Tim Fennell
 */
public interface ReferenceSequenceFile extends Closeable {

    /**
     * Must return a sequence dictionary with at least the following fields completed
     * for each sequence: name, length.
     *
     * @return a list of sequence records representing the sequences in this reference file
     */
    public SAMSequenceDictionary getSequenceDictionary();

    /**
     * Retrieves the next whole sequences from the file.
     * @return a ReferenceSequence or null if at the end of the file
     */
    public ReferenceSequence nextSequence();

    /**
     * Resets the ReferenceSequenceFile so that the next call to nextSequence() will return
     * the first sequence in the file.
     */
    public void reset();


    /**
     * @return true if getSequence and getSubsequenceAt methods are allowed.
     */
    public boolean isIndexed();

    /**
     * Retrieves the complete sequence described by this contig.
     * @param contig contig whose data should be returned.
     * @return The full sequence associated with this contig.
     * @throws UnsupportedOperationException if !sIndexed.
     */
    public ReferenceSequence getSequence( String contig );

    /**
     * Gets the subsequence of the contig in the range [start,stop]
     * @param contig Contig whose subsequence to retrieve.
     * @param start inclusive, 1-based start of region.
     * @param stop inclusive, 1-based stop of region.
     * @return The partial reference sequence associated with this range.
     * @throws UnsupportedOperationException if !sIndexed.
     */
    public ReferenceSequence getSubsequenceAt( String contig, long start, long stop );
    
    /**
     * @return Reference name, file name, or something other human-readable representation.
     */
    public String toString();

    public void close() throws IOException;
}
