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

import htsjdk.index.HtsFileSpan;
import htsjdk.index.HtsQueryIndex;
import htsjdk.samtools.util.FileExtensions;
import java.util.Optional;

/**
 * A basic interface for querying BAM indices.
 *
 * <p>This is {@link HtsQueryIndex} plus the parts of a BAI that do not generalise to other index
 * formats: linear bins, and the per-reference record counts a CRAI does not carry.
 *
 * @author mhanna
 * @version 0.1
 */
public interface BAMIndex extends HtsQueryIndex {

    /**
     * @deprecated since June 2019 Use {@link FileExtensions#BAI_INDEX} instead.
     */
    @Deprecated
    String BAMIndexSuffix = FileExtensions.BAI_INDEX;
    /**
     * @deprecated since June 2019 Use {@link FileExtensions#BAI_INDEX} instead.
     */
    @Deprecated
    String BAI_INDEX_SUFFIX = FileExtensions.BAI_INDEX;
    /**
     * @deprecated since June 2019 Use {@link FileExtensions#CSI} instead.
     */
    @Deprecated
    String CSI_INDEX_SUFFIX = FileExtensions.CSI;

    /**
     * Gets the compressed chunks which should be searched for the contents of records contained by the span
     * referenceIndex:startPos-endPos, inclusive.  See the BAM spec for more information on how a chunk is
     * represented.
     *
     * @param referenceIndex The contig.
     * @param startPos Genomic start of query.
     * @param endPos Genomic end of query.
     * @return A file span listing the chunks in the BAM file.
     */
    @Override
    BAMFileSpan getSpanOverlapping(final int referenceIndex, final int startPos, final int endPos);

    /**
     * Gets the start of the last linear bin in the index. {@link #getSpanOfUnplaced()} is the
     * format-neutral form of the same information.
     * @return The chunk indicating the start of the last bin in the linear index.
     */
    long getStartOfLastLinearBin();

    /**
     * {@inheritDoc}
     *
     * <p>Derived from the last linear bin, which is all a BAI records about where unplaced records
     * start: present from there to the end of the file, whether or not any record there is
     * unplaced. Empty when the file has no mapped reads; any unplaced records then start at the
     * first record, a position a BAI cannot express.
     */
    @Override
    default Optional<HtsFileSpan> getSpanOfUnplaced() {
        final long startOfLastLinearBin = getStartOfLastLinearBin();
        // -1 means the file has no mapped reads, so there is no "after the mapped reads" to point at.
        return startOfLastLinearBin == -1
                ? Optional.empty()
                : Optional.of(new BAMFileSpan(new Chunk(startOfLastLinearBin, Long.MAX_VALUE)));
    }

    /**
     * Gets meta data for the given reference including information about number of aligned, unaligned, and noCoordinate records
     * @param reference the reference of interest
     * @return meta data for the reference
     */
    BAMIndexMetaData getMetaData(int reference);

    /**
     * Close the index and release any associated resources.
     */
    @Override
    void close();
}
