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

import htsjdk.samtools.BAMIndexMerger;
import htsjdk.samtools.BinningIndexContent;
import htsjdk.samtools.IndexMerger;
import htsjdk.samtools.LinearIndex;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.tribble.util.LittleEndianOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
            throw new IllegalArgumentException(
                    String.format("Cannot merge tabix files with different formats, %s and %s.", index.getFormatSpec(), formatSpec));
        }
        if (!sequenceNames.equals(index.getSequenceNames())) {
            throw new IllegalArgumentException(
                    String.format("Cannot merge tabix files with different sequence names, %s and %s.", index.getSequenceNames(), sequenceNames));
        }
        indexes.add(index);
    }

    @Override
    public void finish(final long dataFileLength) throws IOException {
        if (indexes.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge zero tabix files");
        }
        final long[] offsets = partLengths.stream().mapToLong(i -> i).toArray();
        Arrays.parallelPrefix(offsets, (a, b) -> a + b); // cumulative offsets

        final List<BinningIndexContent> mergedBinningIndexContentList = new ArrayList<>();
        for (int ref = 0; ref < sequenceNames.size(); ref++) {
            final int r = ref;
            List<BinningIndexContent> binningIndexContentList = indexes.stream().map(index -> index.getIndices()[r]).collect(Collectors.toList());
            final BinningIndexContent binningIndexContent = mergeBinningIndexContent(ref, binningIndexContentList, offsets);
            mergedBinningIndexContentList.add(binningIndexContent);
        }
        final TabixIndex tabixIndex = new TabixIndex(formatSpec, sequenceNames, mergedBinningIndexContentList.toArray(new BinningIndexContent[0]));
        try (LittleEndianOutputStream los = new LittleEndianOutputStream(new BlockCompressedOutputStream(out, (File) null))) {
            tabixIndex.write(los);
        }
    }

    private static BinningIndexContent mergeBinningIndexContent(final int referenceSequence, final List<BinningIndexContent> binningIndexContentList, final long[] offsets) {
        final List<BinningIndexContent.BinList> binLists = new ArrayList<>();
        final List<LinearIndex> linearIndexes = new ArrayList<>();
        for (BinningIndexContent binningIndexContent : binningIndexContentList) {
            binLists.add(binningIndexContent == null ? null : binningIndexContent.getBins());
            linearIndexes.add(binningIndexContent == null ? null : binningIndexContent.getLinearIndex());
        }
        return new BinningIndexContent(referenceSequence, BAMIndexMerger.mergeBins(binLists, offsets), BAMIndexMerger.mergeLinearIndexes(referenceSequence, linearIndexes, offsets));
    }
}
