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
import htsjdk.samtools.util.RuntimeIOException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * Provides basic, generic capabilities to be used reading BAM index files.  Users can
 * subclass this class to create new BAM index functionality for adding querying facilities,
 * changing caching behavior, etc.
 *
 * Of particular note: the AbstractBAMFileIndex is, by design, the only class aware of the
 * details of the BAM index file format (other than the four classes representing the data,
 * BAMIndexContent, Bin, Chunk, LinearIndex, and the classes for building the BAM index).
 * Anyone wanting to implement a reader for a differing
 * or extended BAM index format should implement BAMIndex directly.
 */
public abstract class AbstractBAMFileIndex implements BAMIndex {

    private final IndexFileBuffer mIndexBuffer;

    private final SAMSequenceDictionary mBamDictionary;

    long[] sequenceIndexes;

    protected AbstractBAMFileIndex(final SeekableStream stream, final SAMSequenceDictionary dictionary) {
        this(new IndexStreamBuffer(stream), stream.getSource(), dictionary);
    }

    protected AbstractBAMFileIndex(final File file, final SAMSequenceDictionary dictionary) {
        this(new MemoryMappedFileBuffer(file), file.getName(), dictionary);
    }

    protected AbstractBAMFileIndex(final File file, final SAMSequenceDictionary dictionary, final boolean useMemoryMapping) {
        this((useMemoryMapping ? new MemoryMappedFileBuffer(file) : new RandomAccessFileBuffer(file)), file.getName(), dictionary);
    }

    protected AbstractBAMFileIndex(final IndexFileBuffer indexFileBuffer, final String source, final SAMSequenceDictionary dictionary) {
        mIndexBuffer = indexFileBuffer;
        mBamDictionary = dictionary;
        verifyIndexMagicNumber(source);
        initParameters();
    }

    /**
     * Close this index and release any associated resources.
     */
    @Override
    public void close() {
        mIndexBuffer.close();
    }

    /**
     * Get the number of levels employed by this index.
     * @return Number of levels in this index.
     */
    public static int getNumIndexLevels() {
        return GenomicIndexUtil.LEVEL_STARTS.length;
    }

    private static void assertLevelIsValid (final int levelNumber) {
        if (levelNumber >= getNumIndexLevels()) {
            throw new SAMException("Level number (" + levelNumber + ") is greater than or equal to maximum (" + getNumIndexLevels() + ").");
        }
    }

    /**
     * Gets the first bin in the given level.
     * @param levelNumber Level number.  0-based.
     * @return The first bin in this level.
     */
    public static int getFirstBinInLevel(final int levelNumber) {
        assertLevelIsValid(levelNumber);

        return GenomicIndexUtil.LEVEL_STARTS[levelNumber];
    }

    /**
     * Gets the number of bins in the given level.
     * @param levelNumber Level number.  0-based.
     * @return The size (number of possible bins) of the given level.
     */
    public int getLevelSize(final int levelNumber) {
        assertLevelIsValid(levelNumber);

        if (levelNumber == getNumIndexLevels()-1) {
            return GenomicIndexUtil.MAX_BINS - GenomicIndexUtil.LEVEL_STARTS[levelNumber] - 1;
        } else {
            return GenomicIndexUtil.LEVEL_STARTS[levelNumber + 1] - GenomicIndexUtil.LEVEL_STARTS[levelNumber];
        }
    }

    /**
     * Gets the level associated with the given bin number.
     * @param bin The bin  for which to determine the level.
     * @return the level associated with the given bin number.
     */
    public int getLevelForBin(final Bin bin) {
        if(bin.getBinNumber() >= GenomicIndexUtil.MAX_BINS)
            throw new SAMException("Tried to get level for invalid bin.");
        for(int i = getNumIndexLevels()-1; i >= 0; i--) {
            if(bin.getBinNumber() >= GenomicIndexUtil.LEVEL_STARTS[i])
                return i;
        }
        throw new SAMException("Unable to find correct bin for bin "+bin);
    }

    /**
     * Gets the first locus that this bin can index into.
     * @param bin The bin to test.
     * @return The last position that the given bin can represent.
     */
    public int getFirstLocusInBin(final Bin bin) {
        final int level = getLevelForBin(bin);
        final int levelStart = GenomicIndexUtil.LEVEL_STARTS[level];
        final int levelSize = ((level==getNumIndexLevels()-1) ? GenomicIndexUtil.MAX_BINS-1 : GenomicIndexUtil.LEVEL_STARTS[level+1]) - levelStart;
        return (bin.getBinNumber() - levelStart)*(GenomicIndexUtil.BIN_GENOMIC_SPAN /levelSize)+1;
    }

    /**
     * Gets the last locus that this bin can index into.
     * @param bin The bin to test.
     * @return The last position that the given bin can represent.
     */
    public int getLastLocusInBin(final Bin bin) {
        final int level = getLevelForBin(bin);
        final int levelStart = GenomicIndexUtil.LEVEL_STARTS[level];
        final int levelSize = ((level==getNumIndexLevels()-1) ? GenomicIndexUtil.MAX_BINS-1 : GenomicIndexUtil.LEVEL_STARTS[level+1]) - levelStart;
        return (bin.getBinNumber()-levelStart+1)*(GenomicIndexUtil.BIN_GENOMIC_SPAN /levelSize);
    }

    public int getNumberOfReferences() {
        seek(4);
        return readInteger();
    }

    /**
     * Use to get close to the unmapped reads at the end of a BAM file.
     * @return The file offset of the first record in the last linear bin, or -1
     * if there are no elements in linear bins (i.e. no mapped reads).
     */
    @Override
    public long getStartOfLastLinearBin() {
        seek(4);

        final int sequenceCount = readInteger();
        // Because no reads may align to the last sequence in the sequence dictionary,
        // grab the last element of the linear index for each sequence, and return
        // the last one from the last sequence that has one.
        long lastLinearIndexPointer = -1;
        for (int i = 0; i < sequenceCount; i++) {
            // System.out.println("# Sequence TID: " + i);
            final int nBins = readInteger();
            // System.out.println("# nBins: " + nBins);
            for (int j1 = 0; j1 < nBins; j1++) {
                // Skip bin #
                skipBytes(4);
                final int nChunks = readInteger();
                // Skip chunks
                skipBytes(16 * nChunks);
            }
            final int nLinearBins = readInteger();
            if (nLinearBins > 0) {
                // Skip to last element of list of linear bins
                skipBytes(8 * (nLinearBins - 1));
                lastLinearIndexPointer = readLong();
            }
        }

        return lastLinearIndexPointer;
    }

    /**
     * Return meta data for the given reference including information about number of aligned, unaligned, and noCoordinate records
     *
     * @param reference the reference of interest
     * @return meta data for the reference
     */
    @Override
    public BAMIndexMetaData getMetaData(final int reference) {
        seek(4);

        final List<Chunk> metaDataChunks = new ArrayList<Chunk>();

        final int sequenceCount = readInteger();

        if (reference >= sequenceCount) {
            return null;
        }

        skipToSequence(reference);

        final int binCount = readInteger();
        for (int binNumber = 0; binNumber < binCount; binNumber++) {
            final int indexBin = readInteger();
            final int nChunks = readInteger();
            if (indexBin == GenomicIndexUtil.MAX_BINS) {
                readChunks(nChunks, metaDataChunks);
            } else {
                skipBytes(16 * nChunks);
            }
        }
        return new BAMIndexMetaData(metaDataChunks);
    }

    /**
     * Returns count of records unassociated with any reference. Call before the index file is closed
     *
     * @return meta data at the end of the bam index that indicates count of records holding no coordinates
     * or null if no meta data (old index format)
     */
    public Long getNoCoordinateCount() {

        seek(4);
        final int sequenceCount = readInteger();

        skipToSequence(sequenceCount);
        try { // in case of old index file without meta data
            return readLong();
        } catch (final Exception e) {
            return null;
        }
    }

    protected BAMIndexContent query(final int referenceSequence, final int startPos, final int endPos) {
        seek(4);

        final List<Chunk> metaDataChunks = new ArrayList<Chunk>();

        final int sequenceCount = readInteger();

        if (referenceSequence >= sequenceCount) {
            return null;
        }

        final BitSet regionBins = GenomicIndexUtil.regionToBins(startPos, endPos);
        if (regionBins == null) {
            return null;
        }

        skipToSequence(referenceSequence);

        final int binCount = readInteger();
        boolean metaDataSeen = false;
        final Bin[] bins = new Bin[getMaxBinNumberForReference(referenceSequence) +1];
        for (int binNumber = 0; binNumber < binCount; binNumber++) {
            final int indexBin = readInteger();
            final int nChunks = readInteger();
            List<Chunk> chunks = null;
            // System.out.println("# bin[" + i + "] = " + indexBin + ", nChunks = " + nChunks);
            Chunk lastChunk = null;
            if (regionBins.get(indexBin)) {
            	chunks = new ArrayList<Chunk>(nChunks);
                readChunks(nChunks, chunks);
            } else if (indexBin == GenomicIndexUtil.MAX_BINS) {
                // meta data - build the bin so that the count of bins is correct;
                // but don't attach meta chunks to the bin, or normal queries will be off
                readChunks(nChunks, metaDataChunks);
                metaDataSeen = true;
                continue; // don't create a Bin
            } else {
                skipBytes(16 * nChunks);
                chunks = Collections.emptyList();
            }
            final Bin bin = new Bin(referenceSequence, indexBin);
            bin.setChunkList(chunks);
            bin.setLastChunk(lastChunk);
            bins[indexBin] = bin;
        }

        final int nLinearBins = readInteger();

        final int regionLinearBinStart = LinearIndex.convertToLinearIndexOffset(startPos);
        final int regionLinearBinStop = endPos > 0 ? LinearIndex.convertToLinearIndexOffset(endPos) : nLinearBins-1;
        final int actualStop = Math.min(regionLinearBinStop, nLinearBins -1);

        long[] linearIndexEntries = new long[0];
        if (regionLinearBinStart < nLinearBins) {
            linearIndexEntries = new long[actualStop-regionLinearBinStart+1];
            skipBytes(8 * regionLinearBinStart);
            for(int linearBin = regionLinearBinStart; linearBin <= actualStop; linearBin++)
                linearIndexEntries[linearBin-regionLinearBinStart] = readLong();
        }

        final LinearIndex linearIndex = new LinearIndex(referenceSequence,regionLinearBinStart,linearIndexEntries);

        return new BAMIndexContent(referenceSequence, bins, binCount - (metaDataSeen? 1 : 0), new BAMIndexMetaData(metaDataChunks), linearIndex);
    }

    /**
     * The maximum possible bin number for this reference sequence.
     * This is based on the maximum coordinate position of the reference
     * which is based on the size of the reference
     */
    private int getMaxBinNumberForReference(final int reference) {
        try {
            final int sequenceLength = mBamDictionary.getSequence(reference).getSequenceLength();
            return getMaxBinNumberForSequenceLength(sequenceLength);
        } catch (final Exception e) {
            return GenomicIndexUtil.MAX_BINS;
        }
    }

    /**
     * The maxiumum bin number for a reference sequence of a given length
     */
    static int getMaxBinNumberForSequenceLength(final int sequenceLength) {
        return getFirstBinInLevel(getNumIndexLevels() - 1) + (sequenceLength >> 14);
        // return 4680 + (sequenceLength >> 14); // note 4680 = getFirstBinInLevel(getNumIndexLevels() - 1)
    }

    abstract protected BAMIndexContent getQueryResults(int reference);

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

    protected void verifyIndexMagicNumber(final String sourceName) {
        // Verify the magic number.
        seek(0);
        final byte[] buffer = new byte[4];
        readBytes(buffer);
        if (!Arrays.equals(buffer, BAMFileConstants.BAI_INDEX_MAGIC)) {
            throw new RuntimeIOException("Invalid file header in BAM index " + sourceName +
                    ": " + new String(buffer));
        }
    }

    /**
     * Initialization method used for simplifying the constructor
     * hierarchy.
     */
    protected void initParameters() {
        setSequenceIndexes(getNumberOfReferences());
    }

    protected void readChunks(int nChunks, List<Chunk> chunks) {
        Chunk lastChunk;
        for (int ci = 0; ci < nChunks; ci++) {
            final long chunkBegin = readLong();
            final long chunkEnd = readLong();
            lastChunk = new Chunk(chunkBegin, chunkEnd);
            chunks.add(lastChunk);
        }
    }

    protected void skipToSequence(final int sequenceIndex) {
    	//Use sequence position cache if available
    	if(sequenceIndexes[sequenceIndex] != -1){
    		seek(sequenceIndexes[sequenceIndex]);
    		return;
    	}

        // Use previous sequence position if in cache, which optimizes for common access pattern
        // of iterating through sequences in order.
        final int startSequenceIndex;
        final int previousSequenceIndex = sequenceIndex - 1;
        if (sequenceIndex > 0 && sequenceIndexes[previousSequenceIndex] != -1) {
            seek(sequenceIndexes[previousSequenceIndex]);
            startSequenceIndex = previousSequenceIndex;
        } else {
            startSequenceIndex = 0;
        }
    	
        for (int i = startSequenceIndex; i < sequenceIndex; i++) {
            // System.out.println("# Sequence TID: " + i);
            final int nBins = readInteger();
            // System.out.println("# nBins: " + nBins);
            for (int j = 0; j < nBins; j++) {
                readInteger(); // bin
                final int nChunks = readInteger();
                // System.out.println("# bin[" + j + "] = " + bin + ", nChunks = " + nChunks);
                skipBytes(16 * nChunks);
            }
            final int nLinearBins = readInteger();
            // System.out.println("# nLinearBins: " + nLinearBins);
            skipBytes(8 * nLinearBins);
        }
        
        //Update sequence position cache
        sequenceIndexes[sequenceIndex] = position();
    }

    protected final void readBytes(final byte[] bytes) {
        mIndexBuffer.readBytes(bytes);
    }

    protected final int readInteger() {
        return mIndexBuffer.readInteger();
    }

    protected final long readLong() {
        return mIndexBuffer.readLong();
    }

    protected final void skipBytes(final int count) {
        mIndexBuffer.skipBytes(count);
    }

    protected final void seek(final long position) {
        mIndexBuffer.seek(position);
    }
    
    protected final long position(){
    	return mIndexBuffer.position();
    }

    protected final SAMSequenceDictionary getBamDictionary() {
        return mBamDictionary;
    }

    protected final void setSequenceIndexes (int nReferences) {
        sequenceIndexes = new long[nReferences + 1];
        Arrays.fill(sequenceIndexes, -1);
    }
}
