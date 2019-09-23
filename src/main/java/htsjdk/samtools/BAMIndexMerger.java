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

import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    private int numReferences = -1;
    private SAMSequenceDictionary sequenceDictionary;
    private final List<AbstractBAMFileIndex> indexes = new ArrayList<>();
    private long noCoordinateCount;

    public BAMIndexMerger(final OutputStream out, final long headerLength) {
        super(out, headerLength);
    }

    @Override
    public void processIndex(final AbstractBAMFileIndex index, final long partLength) {
        this.partLengths.add(partLength);
        if (numReferences == -1) {
            numReferences = index.getNumberOfReferences();
            sequenceDictionary = index.getBamDictionary();
        }
        if (index.getNumberOfReferences() != numReferences) {
            throw new IllegalArgumentException(
                    String.format("Cannot merge BAI files with different number of references, %s and %s.", numReferences, index.getNumberOfReferences()));
        }
        index.getBamDictionary().assertSameDictionary(sequenceDictionary);
        // just store the indexes rather than computing the BAMIndexContent for each ref,
        // since there may be thousands of refs and indexes, each with thousands of bins
        indexes.add(index);
        noCoordinateCount += index.getNoCoordinateCount();
    }

    @Override
    public void finish(final long dataFileLength) {
        if (indexes.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge zero BAI files");
        }
        final long[] offsets = partLengths.stream().mapToLong(i -> i).toArray();
        Arrays.parallelPrefix(offsets, Long::sum); // cumulative offsets

        try (BinaryBAMIndexWriter writer = new BinaryBAMIndexWriter(numReferences, out)) {
            for (int ref = 0; ref < numReferences; ref++) {
                final int r = ref;
                List<BAMIndexContent> bamIndexContentList = indexes.stream().map(index -> index.getQueryResults(r)).collect(Collectors.toList());
                final BAMIndexContent bamIndexContent = mergeBAMIndexContent(ref, bamIndexContentList, offsets);
                writer.writeReference(bamIndexContent);
            }
            writer.writeNoCoordinateRecordCount(noCoordinateCount);
        }
    }

    public static AbstractBAMFileIndex openIndex(SeekableStream stream, SAMSequenceDictionary dictionary) {
        return new CachingBamFileIndexOptimizedForMerging(stream, dictionary);
    }

    private static BAMIndexContent mergeBAMIndexContent(final int referenceSequence,
                                                        final List<BAMIndexContent> bamIndexContentList, final long[] offsets) {
        final List<BinningIndexContent.BinList> binLists = new ArrayList<>();
        final List<BAMIndexMetaData> metaDataList = new ArrayList<>();
        final List<LinearIndex> linearIndexes = new ArrayList<>();
        for (BAMIndexContent bamIndexContent : bamIndexContentList) {
            if (bamIndexContent == null) {
                binLists.add(null);
                metaDataList.add(null);
                linearIndexes.add(null);
            } else {
                binLists.add(bamIndexContent.getBins());
                metaDataList.add(bamIndexContent.getMetaData());
                linearIndexes.add(bamIndexContent.getLinearIndex());
            }
        }
        return new BAMIndexContent(
                referenceSequence,
                mergeBins(binLists, offsets),
                mergeMetaData(metaDataList, offsets),
                mergeLinearIndexes(referenceSequence, linearIndexes, offsets));
    }

    /**
     * Merge bins for (headerless) BAM file parts.
     * @param binLists the bins to merge
     * @param offsets bin <i>i</i> will be shifted by offset <i>i</i>
     * @return the merged bins
     */
    public static BinningIndexContent.BinList mergeBins(final List<BinningIndexContent.BinList> binLists, final long[] offsets) {
        final List<Bin> mergedBins = new ArrayList<>();
        final int maxBinNumber = binLists.stream().filter(Objects::nonNull).mapToInt(bl -> bl.maxBinNumber).max().orElse(0);
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
        final int numberOfNonNullBins =
                binLists.stream().filter(Objects::nonNull).mapToInt(BinningIndexContent.BinList::getNumberOfNonNullBins).sum() - commonNonNullBins;
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
                throw new IllegalArgumentException(String.format("Bins have different reference sequences, %s and %s.", b.getReferenceSequence(), referenceSequence));
            }
            if (b.getBinNumber() != binNumber) {
                throw new IllegalArgumentException(String.format("Bins have different numbers, %s and %s.", b.getBinNumber(), binNumber));
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

    private static BAMIndexMetaData mergeMetaData(final List<BAMIndexMetaData> metaDataList, final long[] offsets) {
        final List<BAMIndexMetaData> newMetadataList = new ArrayList<>();
        for (int i = 0; i < metaDataList.size(); i++) {
            if (metaDataList.get(i) == null) {
                continue;
            }
            newMetadataList.add(metaDataList.get(i).shift(offsets[i]));
        }
        return mergeMetaData(newMetadataList);
    }

    private static BAMIndexMetaData mergeMetaData(final List<BAMIndexMetaData> metaDataList) {
        long firstOffset = Long.MAX_VALUE;
        long lastOffset = Long.MIN_VALUE;
        long alignedRecordCount = 0;
        long unalignedRecordCount = 0;

        for (BAMIndexMetaData metaData : metaDataList) {
            if (metaData.getFirstOffset() != -1) { // -1 is unset, see BAMIndexMetaData
                firstOffset = Math.min(firstOffset, metaData.getFirstOffset());
            }
            if (metaData.getLastOffset() != 0) { // 0 is unset, see BAMIndexMetaData
                lastOffset = Math.max(lastOffset, metaData.getLastOffset());
            }
            alignedRecordCount += metaData.getAlignedRecordCount();
            unalignedRecordCount += metaData.getUnalignedRecordCount();
        }

        if (firstOffset == Long.MAX_VALUE) {
            firstOffset = -1;
        }
        if (lastOffset == Long.MIN_VALUE) {
            lastOffset = -1;
        }

        final List<Chunk> chunkList = new ArrayList<>();
        chunkList.add(new Chunk(firstOffset, lastOffset));
        chunkList.add(new Chunk(alignedRecordCount, unalignedRecordCount));
        return new BAMIndexMetaData(chunkList);
    }

    /**
     * Merge linear indexes for (headerless) BAM file parts.
     * @param referenceSequence the reference sequence number for the linear indexes being merged
     * @param linearIndexes the linear indexes to merge
     * @param offsets linear index <i>i</i> will be shifted by offset <i>i</i>
     * @return the merged linear index
     */
    public static LinearIndex mergeLinearIndexes(final int referenceSequence, final List<LinearIndex> linearIndexes, final long[] offsets) {
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
