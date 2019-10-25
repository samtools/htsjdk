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

import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.Log;

import java.io.OutputStream;

/**
 * Merges SBI files for parts of a file that have been concatenated.
 */
public final class SBIIndexMerger extends IndexMerger<SBIIndex> {

    private static final Log log = Log.getInstance(SBIIndexMerger.class);

    private SBIIndexWriter indexWriter;
    private long granularity = -1;
    private long offset;
    private long recordCount;
    private long finalVirtualOffset;

    /**
     * Prepare to merge SBI indexes.
     *
     * @param out          the stream to write the merged index to
     * @param headerLength the length of any header that precedes the first part of the data file with
     *                     an index
     */
    public SBIIndexMerger(final OutputStream out, long headerLength) {
        super(out, headerLength);
        this.indexWriter = new SBIIndexWriter(out);
        this.offset = headerLength;
    }

    /**
     * Add an index for a part of the data file to the merged index. This method should be called for
     * each index for the data file parts, in order.
     */
    @Override
    public void processIndex(final SBIIndex index, final long partLength) {
        final long[] virtualOffsets = index.getVirtualOffsets();
        for (int i = 0; i < virtualOffsets.length - 1; i++) {
            indexWriter.writeVirtualOffset(BlockCompressedFilePointerUtil.shift(virtualOffsets[i], offset));
        }
        finalVirtualOffset = BlockCompressedFilePointerUtil.shift(virtualOffsets[virtualOffsets.length - 1], offset);

        final SBIIndex.Header header = index.getHeader();
        offset += header.getFileLength();
        recordCount += header.getTotalNumberOfRecords();
        if (granularity == -1) { // first time being set
            granularity = header.getGranularity();
        } else if (granularity > 0 && granularity != header.getGranularity()) {
            log.warn("Different granularities so setting to 0 (unspecified)");
            granularity = 0;
        }
    }

    /**
     * Complete the index, and close the output stream.
     */
    @Override
    public void finish(final long dataFileLength) {
        final SBIIndex.Header header =
                new SBIIndex.Header(
                        dataFileLength,
                        SBIIndexWriter.EMPTY_MD5,
                        SBIIndexWriter.EMPTY_UUID,
                        recordCount,
                        granularity);
        indexWriter.finish(header, finalVirtualOffset);
    }
}
