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

import htsjdk.samtools.seekablestream.SeekableStream;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;

/**
 * The base of the BAM index readers that callers can construct for themselves, {@link DiskBasedBAMFileIndex} and
 * {@link CSIIndex}. It reads nothing itself: every question is answered by the index that a {@link SamReader}
 * uses, which serves a BAI and a CSI alike.
 *
 * @deprecated ask a {@link SamReader} for its index, or to read an index on its own open it with
 *     {@link htsjdk.index.FileBackedBinningIndex}
 */
@Deprecated
public abstract class AbstractBAMFileIndex implements BAMIndex {

    private final BinningBAMIndex delegate;

    private final SAMSequenceDictionary mBamDictionary;

    protected AbstractBAMFileIndex(final SeekableStream stream, final SAMSequenceDictionary dictionary) {
        delegate = BinningBAMIndex.open(stream, IndexLoading.AUTO);
        mBamDictionary = dictionary;
    }

    protected AbstractBAMFileIndex(final Path path, final SAMSequenceDictionary dictionary) {
        delegate = BinningBAMIndex.open(path, IndexLoading.AUTO);
        mBamDictionary = dictionary;
    }

    /**
     * @param useMemoryMapping has no effect: an index is no longer memory-mapped
     */
    protected AbstractBAMFileIndex(
            final Path path, final SAMSequenceDictionary dictionary, final boolean useMemoryMapping) {
        this(path, dictionary);
    }

    /** The index that answers for this one. */
    BinningBAMIndex getDelegate() {
        return delegate;
    }

    /**
     * Close this index and release any associated resources.
     */
    @Override
    public void close() {
        delegate.close();
    }

    /**
     * Get the number of levels employed by a BAI.
     * @return Number of levels in a BAI.
     */
    public static int getNumIndexLevels() {
        return GenomicIndexUtil.LEVEL_STARTS.length;
    }

    /**
     * Gets the first bin in the given level of a BAI.
     * @param levelNumber Level number.  0-based.
     * @return The first bin in this level.
     */
    public static int getFirstBinInLevel(final int levelNumber) {
        if (levelNumber >= getNumIndexLevels()) {
            throw new SAMException("Level number (" + levelNumber + ") is greater than or equal to maximum ("
                    + getNumIndexLevels() + ").");
        }
        return GenomicIndexUtil.LEVEL_STARTS[levelNumber];
    }

    /**
     * Gets the number of bins in the given level.
     * @param levelNumber Level number.  0-based.
     * @return The size (number of possible bins) of the given level.
     */
    public int getLevelSize(final int levelNumber) {
        return delegate.getLevelSize(levelNumber);
    }

    /**
     * Gets the level associated with the given bin number.
     * @param bin The bin  for which to determine the level.
     * @return the level associated with the given bin number.
     */
    public int getLevelForBin(final Bin bin) {
        return delegate.getLevelForBin(bin);
    }

    /**
     * Gets the first locus that this bin can index into.
     * @param bin The bin to test.
     * @return The first position that the given bin can represent.
     */
    public int getFirstLocusInBin(final Bin bin) {
        return delegate.getFirstLocusInBin(bin);
    }

    /**
     * Gets the last locus that this bin can index into.
     * @param bin The bin to test.
     * @return The last position that the given bin can represent.
     */
    public int getLastLocusInBin(final Bin bin) {
        return delegate.getLastLocusInBin(bin);
    }

    public int getNumberOfReferences() {
        return delegate.getNumberOfReferences();
    }

    @Override
    public BAMFileSpan getSpanOverlapping(final int referenceIndex, final int startPos, final int endPos) {
        return delegate.getSpanOverlapping(referenceIndex, startPos, endPos);
    }

    /**
     * Use to get close to the unmapped reads at the end of a BAM file.
     * @return The file offset of the first record in the last linear bin, or -1
     * if there are no elements in linear bins (i.e. no mapped reads).
     */
    @Override
    public long getStartOfLastLinearBin() {
        return delegate.getStartOfLastLinearBin();
    }

    /**
     * Return meta data for the given reference including information about number of aligned, unaligned, and noCoordinate records
     *
     * @param reference the reference of interest
     * @return meta data for the reference, or null if the index has no such reference
     */
    @Override
    public BAMIndexMetaData getMetaData(final int reference) {
        return delegate.getMetaData(reference);
    }

    /**
     * Returns count of records unassociated with any reference.
     *
     * @return meta data at the end of the bam index that indicates count of records holding no coordinates
     * or null if no meta data (old index format)
     */
    public Long getNoCoordinateCount() {
        return delegate.getNoCoordinateCount();
    }

    /**
     * The maximum possible bin number for a reference sequence of the given length, in a BAI.
     */
    static int getMaxBinNumberForSequenceLength(final int sequenceLength) {
        return getFirstBinInLevel(getNumIndexLevels() - 1) + (sequenceLength >> 14);
    }

    /**
     * Gets the possible number of bins for a given reference sequence.
     * @return How many bins could possibly be used according to this indexing scheme to index a single contig.
     */
    protected int getMaxAddressibleGenomicLocation() {
        return GenomicIndexUtil.BIN_GENOMIC_SPAN;
    }

    /**
     * @deprecated Use {@link GenomicIndexUtil#regionToBins(int, int)} instead.
     *
     * Get candidate bins for the specified region
     * @param startPos 1-based start of target region, inclusive.
     * @param endPos 1-based end of target region, inclusive.
     * @return bit set for each bin that may contain SAMRecords in the target region.
     */
    @Deprecated
    protected BitSet regionToBins(final int startPos, final int endPos) {
        return GenomicIndexUtil.regionToBins(startPos, endPos);
    }

    /**
     * @deprecated Invoke {@link Chunk#optimizeChunkList} directly.
     */
    @Deprecated
    protected List<Chunk> optimizeChunkList(final List<Chunk> chunks, final long minimumOffset) {
        return Chunk.optimizeChunkList(chunks, minimumOffset);
    }

    protected final SAMSequenceDictionary getBamDictionary() {
        return mBamDictionary;
    }
}
