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

/**
 * Merges tabix files for parts of a file that have been concatenated.
 */
public class TabixIndexMerger extends IndexMerger<TabixIndex> {

    private TabixFormat formatSpec;
    private final List<String> sequenceNames = new ArrayList<>();
    private final List<List<BinningIndexContent>> content = new ArrayList<>();

    public TabixIndexMerger(final OutputStream out, final long headerLength) {
        super(out, headerLength);
    }

    @Override
    public void processIndex(final TabixIndex index, final long partLength) {
        this.partLengths.add(partLength);
        if (content.isEmpty()) {
            formatSpec = index.getFormatSpec();
            if (index.getSequenceNames() != null) {
                sequenceNames.addAll(index.getSequenceNames());
            }
            for (int ref = 0; ref < sequenceNames.size(); ref++) {
                content.add(new ArrayList<>());
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
        for (int ref = 0; ref < sequenceNames.size(); ref++) {
            final List<BinningIndexContent> binningIndexContentList = content.get(ref);
            binningIndexContentList.add(index.getIndices()[ref]);
        }
    }

    @Override
    public void finish(final long dataFileLength) throws IOException {
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge zero tabix files");
        }
        final long[] offsets = partLengths.stream().mapToLong(i -> i).toArray();
        Arrays.parallelPrefix(offsets, (a, b) -> a + b); // cumulative offsets

        final List<BinningIndexContent> mergedBinningIndexContentList = new ArrayList<>();
        for (int ref = 0; ref < sequenceNames.size(); ref++) {
            final List<BinningIndexContent> binningIndexContentList = content.get(ref);
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
