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
package htsjdk.tribble.index.tabix;

import htsjdk.index.BinningIndex;
import htsjdk.samtools.IndexMerger;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.tribble.util.LittleEndianOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Merges tabix files for parts of a VCF file that have been concatenated.
 *
 * A partitioned VCF is a directory containing the following files:
 * <ol>
 *     <li>A file named <i>header</i> containing all header bytes in VCF format.</li>
 *     <li>Zero or more files named <i>part-00000</i>, <i>part-00001</i>, ... etc, containing a list of VCF records.</li>
 *     <li>A file named <i>terminator</i> containing a BGZF end-of-file marker block (only if the VCF is bgzip-compressed).</li>
 * </ol>
 *
 * If the VCF is bgzip-compressed then the header and part files must be all bgzip-compressed.
 *
 * For a compressed VCF, if an index is required, then a tabix index can be generated for each (headerless) part file. These files
 * should be named <i>.part-00000.tbi</i>, <i>.part-00001.tbi</i>, ... etc. Note the leading <i>.</i> to make the files hidden.
 *
 * This format has the following properties:
 *
 * <ul>
 *     <li>Parts and their indexes may be written in parallel, since one part file can be written independently of the others.</li>
 *     <li>A VCF file can be created from a partitioned VCF file by concatenating all the non-hidden files (<i>header</i>, <i>part-00000</i>, <i>part-00001</i>, ..., <i>terminator</i>).</li>
 *     <li>A VCF index can be created from a partitioned VCF file by merging all of the hidden files with a <i>.tbi</i> suffix. Note that this is <i>not</i> a simple file concatenation operation. See {@link TabixIndexMerger}.</li>
 * </ul>
 */
public class TabixIndexMerger extends IndexMerger<TabixIndex> {

    private TabixFormat formatSpec;
    private final List<String> sequenceNames = new ArrayList<>();
    private List<TabixIndex> indexes = new ArrayList<>();

    public TabixIndexMerger(final OutputStream out, final long headerLength) {
        super(out, headerLength);
    }

    @Override
    public void processIndex(final TabixIndex index, final long partLength) {
        this.partLengths.add(partLength);
        if (indexes.isEmpty()) {
            formatSpec = index.getFormatSpec();
            if (index.getSequenceNames() != null) {
                sequenceNames.addAll(index.getSequenceNames());
            }
        }
        if (!index.getFormatSpec().equals(formatSpec)) {
            throw new IllegalArgumentException(String.format(
                    "Cannot merge tabix files with different formats, %s and %s.", index.getFormatSpec(), formatSpec));
        }
        if (!sequenceNames.equals(index.getSequenceNames())) {
            throw new IllegalArgumentException(String.format(
                    "Cannot merge tabix files with different sequence names, %s and %s.",
                    index.getSequenceNames(), sequenceNames));
        }
        indexes.add(index);
    }

    @Override
    public void finish(final long dataFileLength) throws IOException {
        if (indexes.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge zero tabix files");
        }
        // partLengths leads with the header's length, so the running total before each part is where it starts.
        final long[] partOffsets = new long[indexes.size()];
        long offset = 0;
        for (int i = 0; i < partOffsets.length; i++) {
            offset += partLengths.get(i);
            partOffsets[i] = offset;
        }

        final BinningIndex merged = BinningIndex.merge(
                indexes.stream().map(TabixIndex::getBinningIndex).collect(Collectors.toList()), partOffsets);
        final TabixIndex tabixIndex =
                new TabixIndex(formatSpec, sequenceNames, merged, indexes.get(0).getIndexType());
        try (LittleEndianOutputStream los =
                new LittleEndianOutputStream(new BlockCompressedOutputStream(out, (Path) null))) {
            tabixIndex.write(los);
        }
    }
}
