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

/**
 * Merges BAM index files for (headerless) parts of a BAM file into a single
 * index file. The index files must have been produced using an uninitialized window (TODO).
 */
public class BAMIndexMerger {

    private static final int UNINITIALIZED_WINDOW = -1;

    /**
     * Merge BAI files for (headerless) BAM file parts.
     *
     * @param header      the header for the file
     * @param partLengths the lengths, in bytes, of the headerless BAM file parts
     * @param baiStreams  streams for the BAI files to merge
     * @param baiOut      the output stream for the resulting merged BAI
     */
    public static void merge(
            final SAMFileHeader header,
            final List<Long> partLengths,
            final List<SeekableStream> baiStreams,
            final OutputStream baiOut) {
        if (baiStreams.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge zero BAI files");
        }
        final SAMSequenceDictionary dict = header.getSequenceDictionary();
        final List<AbstractBAMFileIndex> bais = new ArrayList<>();
        for (SeekableStream baiStream : baiStreams) {
            bais.add(new CachingBAMFileIndex(baiStream, dict));
        }
        final int numReferences = bais.get(0).getNumberOfReferences();
        for (AbstractBAMFileIndex bai : bais) {
            if (bai.getNumberOfReferences() != numReferences) {
                throw new IllegalArgumentException(
                        String.format("Cannot merge BAI files with different number of references, %s and %s.", numReferences, bai.getNumberOfReferences()));
            }
        }
        final long[] offsets = partLengths.stream().mapToLong(i -> i).toArray();
        Arrays.parallelPrefix(offsets, (a, b) -> a + b); // cumulative offsets

        try (BinaryBAMIndexWriter writer =
                     new BinaryBAMIndexWriter(numReferences, baiOut)) {
            for (int ref = 0; ref < numReferences; ref++) {
                final List<BAMIndexContent> bamIndexContentList = new ArrayList<>();
                for (AbstractBAMFileIndex bai : bais) {
                    bamIndexContentList.add(bai.getQueryResults(ref));
                }
                BAMIndexContent bamIndexContent = mergeBAMIndexContent(ref, bamIndexContentList, offsets);
                writer.writeReference(bamIndexContent);
            }
            long noCoordinateCount = bais.stream().mapToLong(AbstractBAMFileIndex::getNoCoordinateCount).sum();
            writer.writeNoCoordinateRecordCount(noCoordinateCount);
        }
    }

    private static BAMIndexContent mergeBAMIndexContent(final int referenceSequence,
                                                        final List<BAMIndexContent> bamIndexContentList, final long[] offsets) {
        final List<BinningIndexContent.BinList> binLists = new ArrayList<>();
        final List<BAMIndexMetaData> metaDataList = new ArrayList<>();
        final List<LinearIndex> linearIndexes = new ArrayList<>();
        for (BAMIndexContent bamIndexContent : bamIndexContentList) {
            binLists.add(bamIndexContent.getBins());
            metaDataList.add(bamIndexContent.getMetaData());
            linearIndexes.add(bamIndexContent.getLinearIndex());
        }
        return new BAMIndexContent(
                referenceSequence,
                mergeBins(binLists, offsets),
                mergeMetaData(metaDataList, offsets),
                mergeLinearIndexes(referenceSequence, linearIndexes, offsets));
    }

    public static BinningIndexContent.BinList mergeBins(final List<BinningIndexContent.BinList> binLists, final long[] offsets) {
        final List<Bin> mergedBins = new ArrayList<>();
        final int maxBinNumber = binLists.stream().mapToInt(bl -> bl.maxBinNumber).max().orElse(0);
        int commonNonNullBins = 0;
        for (int i = 0; i <= maxBinNumber; i++) {
            List<Bin> nonNullBins = new ArrayList<>();
            for (int j = 0; j < binLists.size(); j++) {
                BinningIndexContent.BinList binList = binLists.get(j);
                Bin bin = binList.getBin(i);
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
                binLists.stream().mapToInt(BinningIndexContent.BinList::getNumberOfNonNullBins).sum() - commonNonNullBins;
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
                throw new IllegalArgumentException("Bins have different reference sequences");
            }
            if (b.getBinNumber() != binNumber) {
                throw new IllegalArgumentException("Bins have different numbers");
            }
            allChunks.addAll(b.getChunkList());
        }
        Collections.sort(allChunks);
        final Bin bin = new Bin(referenceSequence, binNumber);
        for (Chunk newChunk : allChunks) {
            // logic is from BinningIndexBuilder#processFeature
            final long chunkStart = newChunk.getChunkStart();
            final long chunkEnd = newChunk.getChunkEnd();

            final List<Chunk> oldChunks = bin.getChunkList();
            if (!bin.containsChunks()) {
                bin.addInitialChunk(newChunk);
            } else {
                final Chunk lastChunk = bin.getLastChunk();

                // Coalesce chunks that are in the same or adjacent file blocks.
                // Similar to AbstractBAMFileIndex.optimizeChunkList,
                // but no need to copy the list, no minimumOffset, and maintain bin.lastChunk
                if (BlockCompressedFilePointerUtil.areInSameOrAdjacentBlocks(
                        lastChunk.getChunkEnd(), chunkStart)) {
                    lastChunk.setChunkEnd(chunkEnd); // coalesced
                } else {
                    oldChunks.add(newChunk);
                    bin.setLastChunk(newChunk);
                }
            }
        }
        return bin;
    }

    private static BAMIndexMetaData mergeMetaData(final List<BAMIndexMetaData> metaDataList, final long[] offsets) {
        final List<BAMIndexMetaData> newMetadataList = new ArrayList<>();
        for (int i = 0; i < metaDataList.size(); i++) {
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

    public static LinearIndex mergeLinearIndexes(final int referenceSequence, final List<LinearIndex> linearIndexes, final long[] offsets) {
        int maxIndex = -1;
        for (LinearIndex li : linearIndexes) {
            if (li.getIndexStart() != 0) {
                throw new IllegalArgumentException("Cannot merge linear indexes that don't all start at zero");
            }
            maxIndex = Math.max(maxIndex, li.size());
        }
        if (maxIndex == -1) {
            throw new IllegalArgumentException("Error merging linear indexes");
        }

        final long[] entries = new long[maxIndex];
        Arrays.fill(entries, UNINITIALIZED_WINDOW);
        for (int i = 0; i < maxIndex; i++) {
            for (int liIndex = 0; liIndex < linearIndexes.size(); liIndex++) {
                final LinearIndex li = linearIndexes.get(liIndex);
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
