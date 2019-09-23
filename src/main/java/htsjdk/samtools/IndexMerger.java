/*
 * The MIT License
 *
 * Copyright (c) 2018 The Broad Institute
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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges index files for (headerless) parts of a partitioned main file into a single index file.
 *
 * The file and directory structure for the partitioned file is defined in subclasses, but the general pattern is as
 * follows.
 *
 * A partitioned file (e.g BAM, CRAM, or VCF) is a directory containing the following files:
 * <ol>
 *     <li>A file named <i>header</i> containing all header bytes.</li>
 *     <li>Zero or more files named <i>part-00000</i>, <i>part-00001</i>, ... etc, containing file records, with no header.</li>
 *     <li>A file named <i>terminator</i> containing a file terminator. (This may be absent if it is not required by the format.)</li>
 * </ol>
 *
 * For indexing, an index file is generated for each (headerless) part file. These files
 * should be named <i>.part-00000.ext</i>, <i>.part-00001.ext</i>, ... etc, where <i>.ext</i> is the usual index
 * extension, e.g. <i>.bai</i> for a BAM index. Note the leading <i>.</i> to make the files hidden.
 *
 * This class will merge all the indexes for the parts files into a single index file.
 *
 * @param <T> the type of the index (e.g. {@link AbstractBAMFileIndex} for a BAM index).
 */
public abstract class IndexMerger<T> {
    protected final OutputStream out;
    protected final List<Long> partLengths;

    /**
     * Create an index merger.
     * @param out the output stream to write the merged index to. Must be uncompressed since the implementation of this
     *            class will provide appropriate compression.
     * @param headerLength the length of the header file, in bytes.
     */
    public IndexMerger(final OutputStream out, final long headerLength) {
        this.out = out;
        this.partLengths = new ArrayList<>();
        this.partLengths.add(headerLength);
    }

    /**
     * Process the next index and add to the merged index.
     * @param index the index to merge
     * @param partLength the length of the part file corresponding to the index, in bytes.
     */
    public abstract void processIndex(final T index, final long partLength);

    /**
     * Finish merging the indexes, and close the output stream.
     * @param dataFileLength the length of the total data file, in bytes.
     * @throws IOException if an error occurs
     */
    public abstract void finish(final long dataFileLength) throws IOException;
}
