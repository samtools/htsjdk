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

import htsjdk.index.BinningIndex;
import htsjdk.index.FileBackedBinningIndex;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Merges BAM index files for (headerless) parts of a BAM file into a single
 * index file. The index files must have been produced using {@link BAMIndexer} with {@code fillInUninitializedValues}
 * set to false.
 *
 * A partitioned BAM is a directory containing the following files:
 * <ol>
 *     <li>A file named <i>header</i> containing all header bytes in BAM format.</li>
 *     <li>Zero or more files named <i>part-00000</i>, <i>part-00001</i>, ... etc, containing a list of alignments in BAM format.</li>
 *     <li>A file named <i>terminator</i> containing a BGZF end-of-file marker block.</li>
 * </ol>
 *
 * If an index is required, a BAM index can be generated for each (headerless) part file. These files
 * should be named <i>.part-00000.bai</i>, <i>.part-00001.bai</i>, ... etc. Note the leading <i>.</i> to make the files hidden.
 *
 * This format has the following properties:
 *
 * <ul>
 *     <li>Parts and their indexes may be written in parallel, since one part file can be written independently of the others.</li>
 *     <li>A BAM file can be created from a partitioned BAM file by concatenating all the non-hidden files (<i>header</i>, <i>part-00000</i>, <i>part-00001</i>, ..., <i>terminator</i>).</li>
 *     <li>A BAM index can be created from a partitioned BAM file by merging all of the hidden files with a <i>.bai</i> suffix. Note that this is <i>not</i> a simple file concatenation operation. See {@link BAMIndexMerger}.</li>
 * </ul>
 */
public final class BAMIndexMerger extends IndexMerger<AbstractBAMFileIndex> {

    private static final int UNINITIALIZED_WINDOW = -1;

    private SAMSequenceDictionary sequenceDictionary;
    private final List<BinningIndex> parts = new ArrayList<>();

    public BAMIndexMerger(final OutputStream out, final long headerLength) {
        super(out, headerLength);
    }

    @Override
    public void processIndex(final AbstractBAMFileIndex index, final long partLength) {
        // A CSI may share a BAI's binning scheme, but it has no linear index to merge.
        if (index.getDelegate().getSource() instanceof FileBackedBinningIndex source && source.isCsi()) {
            throw new IllegalArgumentException("Cannot merge an index that is not a BAI");
        }
        // Read now, so that the caller is free to close the index.
        final BinningIndex part = index.getDelegate().getSource().loadAll();
        if (parts.isEmpty()) {
            sequenceDictionary = index.getBamDictionary();
        } else if (part.getReferenceCount() != parts.get(0).getReferenceCount()) {
            throw new IllegalArgumentException(String.format(
                    "Cannot merge BAI files with different number of references, %s and %s.",
                    parts.get(0).getReferenceCount(), part.getReferenceCount()));
        }
        index.getBamDictionary().assertSameDictionary(sequenceDictionary);
        this.partLengths.add(partLength);
        parts.add(part);
    }

    @Override
    public void finish(final long dataFileLength) {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge zero BAI files");
        }
        // The lengths begin with the header's, so their running total is where each part starts.
        final long[] partStarts = partLengths.stream().mapToLong(i -> i).toArray();
        Arrays.parallelPrefix(partStarts, Long::sum);

        try (BinaryCodec codec = new BinaryCodec(out)) {
            BAMIndexer.writeBai(BinningIndex.merge(parts, Arrays.copyOf(partStarts, parts.size())), codec);
        }
    }

    public static AbstractBAMFileIndex openIndex(SeekableStream stream, SAMSequenceDictionary dictionary) {
        return new DiskBasedBAMFileIndex(stream, dictionary);
    }

    /**
     * Merge bins for (headerless) BAM file parts.
     * @param binLists the bins to merge
     * @param offsets bin <i>i</i> will be shifted by offset <i>i</i>
     * @return the merged bins
     * @deprecated merge whole indexes with {@link BinningIndex#merge}
     */
    @Deprecated
    public static BinningIndexContent.BinList mergeBins(
            final List<BinningIndexContent.BinList> binLists, final long[] offsets) {
        final List<Bin> mergedBins = new ArrayList<>();
        final int maxBinNumber = binLists.stream()
                .filter(Objects::nonNull)
                .mapToInt(bl -> bl.maxBinNumber)
                .max()
                .orElse(0);
        int commonNonNullBins = 0;
        for (int i = 0; i <= maxBinNumber; i++) {
            final List<Bin> nonNullBins = new ArrayList<>();
            for (int j = 0; j < binLists.size(); j++) {
                final BinningIndexContent.BinList binList = binLists.get(j);
                if (binList == null) {
                    continue;
                }
                final Bin bin = binList.getBin(i);
                if (bin != null) {
                    nonNullBins.add(bin.shift(offsets[j]));
                }
            }
            if (!nonNullBins.isEmpty()) {
                mergedBins.add(mergeBins(nonNullBins));
                commonNonNullBins += nonNullBins.size() - 1;
            }
        }
        final int numberOfNonNullBins = binLists.stream()
                        .filter(Objects::nonNull)
                        .mapToInt(BinningIndexContent.BinList::getNumberOfNonNullBins)
                        .sum()
                - commonNonNullBins;
        return new BinningIndexContent.BinList(mergedBins.toArray(new Bin[0]), numberOfNonNullBins);
    }

    private static Bin mergeBins(final List<Bin> bins) {
        if (bins.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge empty bins");
        }
        if (bins.size() == 1) {
            return bins.get(0);
        }
        final int referenceSequence = bins.get(0).getReferenceSequence();
        final int binNumber = bins.get(0).getBinNumber();
        final List<Chunk> allChunks = new ArrayList<>();
        for (Bin b : bins) {
            if (b.getReferenceSequence() != referenceSequence) {
                throw new IllegalArgumentException(String.format(
                        "Bins have different reference sequences, %s and %s.",
                        b.getReferenceSequence(), referenceSequence));
            }
            if (b.getBinNumber() != binNumber) {
                throw new IllegalArgumentException(
                        String.format("Bins have different numbers, %s and %s.", b.getBinNumber(), binNumber));
            }
            allChunks.addAll(b.getChunkList());
        }
        Collections.sort(allChunks);
        final Bin bin = new Bin(referenceSequence, binNumber);
        for (Chunk newChunk : allChunks) {
            bin.addChunk(newChunk);
        }
        return bin;
    }

    /**
     * Merge linear indexes for (headerless) BAM file parts.
     * @param referenceSequence the reference sequence number for the linear indexes being merged
     * @param linearIndexes the linear indexes to merge
     * @param offsets linear index <i>i</i> will be shifted by offset <i>i</i>
     * @return the merged linear index
     * @deprecated merge whole indexes with {@link BinningIndex#merge}
     */
    @Deprecated
    public static LinearIndex mergeLinearIndexes(
            final int referenceSequence, final List<LinearIndex> linearIndexes, final long[] offsets) {
        int maxIndex = -1;
        for (LinearIndex li : linearIndexes) {
            if (li == null) {
                continue;
            }
            if (li.getIndexStart() != 0) {
                throw new IllegalArgumentException("Cannot merge linear indexes that don't all start at zero");
            }
            maxIndex = Math.max(maxIndex, li.size());
        }
        if (maxIndex == -1) {
            return new LinearIndex(referenceSequence, 0, new long[0]);
        }

        final long[] entries = new long[maxIndex];
        Arrays.fill(entries, UNINITIALIZED_WINDOW);
        for (int i = 0; i < maxIndex; i++) {
            for (int liIndex = 0; liIndex < linearIndexes.size(); liIndex++) {
                final LinearIndex li = linearIndexes.get(liIndex);
                if (li == null) {
                    continue;
                }
                final long[] indexEntries = li.getIndexEntries();
                // Use the first linear index that has an index entry at position i.
                // There is no need to check later linear indexes, since their entries
                // will be guaranteed to have larger offsets (as a consequence of files
                // being coordinate-sorted).
                if (i < indexEntries.length && indexEntries[i] != UNINITIALIZED_WINDOW) {
                    entries[i] = BlockCompressedFilePointerUtil.shift(indexEntries[i], offsets[liIndex]);
                    break;
                }
            }
        }
        // Convert all uninitialized values following the procedure in
        // BinningIndexBuilder#generateIndexContent.
        long lastNonZeroOffset = 0;
        for (int i = 0; i < maxIndex; i++) {
            if (entries[i] == UNINITIALIZED_WINDOW) {
                entries[i] = lastNonZeroOffset;
            } else {
                lastNonZeroOffset = entries[i];
            }
        }
        return new LinearIndex(referenceSequence, 0, entries);
    }
}
