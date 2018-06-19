package htsjdk.samtools;

import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.File;
import java.nio.file.Path;
import java.util.*;


public class CSIIndex implements BrowseableBAMIndex {

    private int binDepth;
    private int minShift;
    private int maxBins;
    private int maxSpan;
    private byte[] auxData;
    private int nReferences;

    private int metaDataPos = -1;

    private final IndexFileBuffer mIndexBuffer;
    private SAMSequenceDictionary mBamDictionary = null;

    private final int [] sequenceIndexes;

    /**
     * Constructors
     */

    private CSIIndex(final SeekableStream stream, final SAMSequenceDictionary dictionary) {
        this(new IndexStreamBuffer(stream), stream.getSource(), dictionary);
    }

    public CSIIndex(final Path path, final SAMSequenceDictionary dictionary) {
        this(new MemoryMappedFileBuffer(path.toFile()), path.toString(), dictionary);
    }

    public CSIIndex(final File file, final SAMSequenceDictionary dictionary) {
        this(new RandomAccessFileBuffer(file), file.getName(), dictionary);
    }

    private CSIIndex(final IndexFileBuffer indexFileBuffer, final String source, final SAMSequenceDictionary dictionary) {
        mIndexBuffer = indexFileBuffer;
        mBamDictionary = dictionary;

        verifyCSIMagicNumber(source);
        readMinShiftAndBinDepth();
        readAuxDataAndNRef();

        sequenceIndexes = new int[getNumberOfReferences() + 1];
        Arrays.fill(sequenceIndexes, -1);
    }

    /**
     * Getters and setters
     */

    public int getBinDepth() {
        return binDepth;
    }

    private void setBinDepth(int binDepth) { this.binDepth = binDepth; }

    public int getMinShift() {
        return minShift;
    }

    private void setMinShift(int minShift) {
        this.minShift = minShift;
    }

    public int getMaxBins() {
        return maxBins;
    }

    private void setMaxBins(int binDepth) { this.maxBins = ((1<<3*binDepth) - 1)/7; }

    public int getMaxSpan() {
        return maxSpan;
    }

    private void setMaxSpan(int binDepth, int minShift) {
        this.maxSpan = 1<<(minShift + 3*(binDepth - 1));
    }

    public byte[] getAuxData() { return auxData; }

    private void setAuxData(byte[] auxData) { this.auxData = auxData; }

    public int getNumberOfReferences() { return nReferences; }

    private void setNumberOfReferences(int nReferences) { this.nReferences = nReferences; }

    /**
     * Computes the number of bins on the given level.
     * @param levelNumber Level for which to compute the size.
     * @return
     */

    @Override
    public int getLevelSize(int levelNumber) {
        if (levelNumber >= getBinDepth()) {
            throw new SAMException("Level number is too big (" + levelNumber + ").");
        }
        return 1<<3*(levelNumber);
    }

    /**
     * Extends the functionality of @see AbstractBAMFileIndex.getFirstBinInLevel
     */

    public int getFirstBinOnLevel (final int levelNumber) {
        if (levelNumber >= getBinDepth()) {
            throw new SAMException("Level number is too big (" + levelNumber + ").");
        }
        return ((1<<3*levelNumber) - 1)/7;
    }

    @Override
    public int getLevelForBin(Bin bin) {
        if(bin == null || bin.getBinNumber() > getMaxBins()) {
            throw new SAMException("Tried to get level for invalid bin: " +  bin);
        }
        for (int i = getBinDepth()-1; i > -1 ; i--) {
              if (bin.getBinNumber() >= getFirstBinOnLevel(i)) {
                  return i;
              }
        }
        throw new SAMException("Unable to find correct level for bin " + bin);
    }

    @Override
    public int getFirstLocusInBin(Bin bin) {
        if(bin == null || bin.getBinNumber() > getMaxBins()) {
            throw new SAMException("Tried to get first locus for invalid bin: " + bin);
        }
        int level = getLevelForBin(bin);
        int firstBinOnLevel = getFirstBinOnLevel(level);
        int levelSize = getLevelSize(level);

        return (bin.getBinNumber() - firstBinOnLevel)*(getMaxSpan()/levelSize) + 1;
    }

    @Override
    public int getLastLocusInBin(Bin bin) {
        if(bin == null || bin.getBinNumber() > getMaxBins()) {
            throw new SAMException("Tried to get last locus for invalid bin: " + bin);
        }
        int level = getLevelForBin(bin);
        int firstBinOnLevel = getFirstBinOnLevel(level);
        int levelSize = getLevelSize(level);

        return (bin.getBinNumber() - firstBinOnLevel + 1)*(getMaxSpan()/levelSize);
    }

    @Override
    public BinList getBinsOverlapping(int referenceIndex, int startPos, int endPos) {
        final BitSet regionBins = GenomicIndexUtil.regionToBins(startPos, endPos, getMinShift(), getBinDepth());
        if (regionBins == null) {
            return null;
        }
        return new BinList(referenceIndex,regionBins);
    }

    @Override
    public BAMFileSpan getSpanOverlapping(Bin bin) { return null;}


    @Override
    public BAMFileSpan getSpanOverlapping(int referenceIndex, int startPos, int endPos) {
        final BAMIndexContent queryResults = query(referenceIndex, startPos, endPos);
        int initialBinNumber = getFirstBinOnLevel(getBinDepth() - 1) + (startPos >> getMinShift());
        long minimumOffset = 0L;
        Bin targetBin;

        if(queryResults == null) {
            return null;
        }

        /** Compute 'minimumOffset' by searching the lowest level bin containing 'startPos'.
            If the computed bin is not in the index, try the next bin to the left, belonging
            to the same parent. If it is the first sibling bin, try the parent bin.
         */

        do {
            int firstBinNumber;
            targetBin = queryResults.getBins().getBin(initialBinNumber);
            if (targetBin != null) {
                break;
            }
            firstBinNumber = (getParentBinNumber(initialBinNumber)<<3) + 1;
            if (initialBinNumber > firstBinNumber) {
                initialBinNumber--;
            } else {
                initialBinNumber = getParentBinNumber(initialBinNumber);
            }
        } while(initialBinNumber != 0);

        if (initialBinNumber == 0) {
            targetBin = queryResults.getBins().getBin(initialBinNumber);
        }

        if (targetBin != null && targetBin instanceof BinWithOffset) {
            minimumOffset = ((BinWithOffset) targetBin).getlOffset();
        }

        List<Chunk> chunkList = new ArrayList<Chunk>();
        for(final Chunk chunk: queryResults.getAllChunks()) {
            chunkList.add(chunk.clone());
        }

        chunkList = Chunk.optimizeChunkList(chunkList, minimumOffset);
        return new BAMFileSpan(chunkList);
    }

    @Override
    public long getStartOfLastLinearBin() {
        return -1;
    }

    @Override
    public void close() { mIndexBuffer.close(); }

    private void verifyCSIMagicNumber(final String sourceName) {
        // Verify the magic number.
        if (BAMFileConstants.CSI_MAGIC_OFFSET != position()) {
            seek(BAMFileConstants.CSI_MAGIC_OFFSET);
        }
        final byte[] buffer = new byte[BAMFileConstants.CSI_MINSHIFT_OFFSET];
        readBytes(buffer); // magic
        if (!Arrays.equals(buffer, BAMFileConstants.CSI_INDEX_MAGIC)) {
            throw new RuntimeIOException("Invalid file header in BAM CSI index " + sourceName +
                    ": " + new String(buffer));
        }
    }

    private void readMinShiftAndBinDepth() {
        if (BAMFileConstants.CSI_MINSHIFT_OFFSET != position()) {
            seek(BAMFileConstants.CSI_MINSHIFT_OFFSET);
        }
        setMinShift(readInteger()); // min_shift
        setBinDepth(readInteger() + 1); // depth - HTSlib doesn't count the first level (bin 0)
        setMaxBins(binDepth);
        setMaxSpan(binDepth, minShift);
    }

    private void readAuxDataAndNRef() {
        if (BAMFileConstants.CSI_AUXDATA_OFFSET != position()) {
            seek(BAMFileConstants.CSI_AUXDATA_OFFSET);
        }
        //set the aux data length first
        byte[] auxData = new byte[readInteger()]; // l_aux
        readBytes(auxData); // aux
        setAuxData(auxData);
        setNumberOfReferences(readInteger()); // n_ref
        metaDataPos = position(); // save the metadata position for delayed reading
    }

    public int getParentBinNumber(int binNumber) {
        if (binNumber >= getMaxBins()) {
            throw new SAMException("Tried to get parent bin for invalid bin (" + binNumber + ").");
        }
        if (binNumber == 0) {
            return 0;
        }
        return (binNumber - 1) >> 3;
    }

    public int getParentBinNumber(Bin bin) {
        if (bin == null) {
            throw new SAMException("Tried to get parent bin for null bin.");
        }
        return getParentBinNumber(bin.getBinNumber());
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
            return getMaxBins();
        }
    }

    /**
     * The maximum bin number for a reference sequence of a given length
     */
    private int getMaxBinNumberForSequenceLength(final int sequenceLength) {
        return getFirstBinOnLevel(getBinDepth() - 1) + (sequenceLength >> getMinShift());
    }

    protected BAMIndexContent query(final int referenceSequence, final int startPos, final int endPos) {
        if (metaDataPos > 0 && position() != metaDataPos) {
            seek(metaDataPos);
        }

        final List<Chunk> metaDataChunks = new ArrayList<Chunk>();

        final int sequenceCount = getNumberOfReferences();

        if (referenceSequence >= sequenceCount || endPos < startPos) {
            return null;
        }

        final BitSet regionBins = GenomicIndexUtil.regionToBins(startPos, endPos, getMinShift(), getBinDepth());
        if (regionBins == null) {
            return null;
        }

        skipToSequence(referenceSequence);

        final int binCount = readInteger(); // n_bin
        boolean metaDataSeen = false;
        final Bin[] bins = new BinWithOffset[getMaxBinNumberForReference(referenceSequence) +1];
        for (int binNumber = 0; binNumber < binCount; binNumber++) {
            final int indexBin = readInteger(); // bin
            final long lOffset = readLong(); // l_offset
            final int nChunks = readInteger();  // n_chunk
            List<Chunk> chunks;

            Chunk lastChunk = null;
            if (regionBins.get(indexBin)) {
                chunks = new ArrayList<Chunk>(nChunks);
                for (int ci = 0; ci < nChunks; ci++) {
                    final long chunkBegin = readLong(); // chunk_beg
                    final long chunkEnd = readLong(); // chunk_end
                    lastChunk = new Chunk(chunkBegin, chunkEnd);
                    chunks.add(lastChunk);
                }
            } else if (indexBin == getMaxBins() + 1) {
                // meta data - build the bin so that the count of bins is correct;
                // but don't attach meta chunks to the bin, or normal queries will be off
                for (int ci = 0; ci < nChunks; ci++) {
                    final long chunkBegin = readLong();
                    final long chunkEnd = readLong();
                    lastChunk = new Chunk(chunkBegin, chunkEnd);
                    metaDataChunks.add(lastChunk);
                }
                metaDataSeen = true;
                continue; // don't create a Bin
            } else {
                skipBytes(BAMFileConstants.CSI_CHUNK_SIZE * nChunks);
                chunks = Collections.emptyList();
            }
            final BinWithOffset bin = new BinWithOffset(referenceSequence, indexBin, lOffset);
            bin.setChunkList(chunks);
            bin.setLastChunk(lastChunk);
            bins[indexBin] = bin;
        }

        return new BAMIndexContent(referenceSequence, bins, binCount - (metaDataSeen? 1 : 0), new BAMIndexMetaData(metaDataChunks), null);
    }

    /**
     * Return meta data for the given reference including information about number of aligned, unaligned, and noCoordinate records
     *
     * @param reference the reference of interest
     * @return meta data for the reference
     */

    public BAMIndexMetaData getMetaData(final int reference) {
        if (metaDataPos > 0 && position() != metaDataPos) {
            seek(metaDataPos);
        }

        final List<Chunk> metaDataChunks = new ArrayList<Chunk>();

        final int sequenceCount = getNumberOfReferences();

        if (reference >= sequenceCount) {
            return null;
        }

        skipToSequence(reference);

        final int binCount = readInteger(); // n_bin
        for (int binNumber = 0; binNumber < binCount; binNumber++) {
            final int indexBin = readInteger(); // bin
            final long lOffset = readLong(); // loffset
            final int nChunks = readInteger(); // n_chunk
            if (indexBin == getMaxBins() + 1) {
                for (int ci = 0; ci < nChunks; ci++) {
                    final long chunkBegin = readLong(); // chunk_beg
                    final long chunkEnd = readLong(); // chunk_end
                    metaDataChunks.add(new Chunk(chunkBegin, chunkEnd));
                }
            } else {
                skipBytes(BAMFileConstants.CSI_CHUNK_SIZE * nChunks);
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
        if (metaDataPos > 0 && position() != metaDataPos) {
            seek(metaDataPos);
        }

        skipToSequence(getNumberOfReferences());
        try { // in case of old index file without meta data
            return readLong();
        } catch (final Exception e) {
            return null;
        }
    }

    public BAMIndexContent getQueryResults(final int referenceSequence) {
        return query(referenceSequence, 0, Integer.MAX_VALUE);
    }


    private void skipToSequence(final int sequenceIndex) {
        //Use sequence position cache if available
        if(sequenceIndexes[sequenceIndex] != -1){
            seek(sequenceIndexes[sequenceIndex]);
            return;
        }

        for (int i = 0; i < sequenceIndex; i++) {

            final int nBins = readInteger(); // n_bin

            for (int j = 0; j < nBins; j++) {
                readInteger(); // bin
                readLong(); // loffset
                final int nChunks = readInteger(); // n_chunk
                skipBytes(BAMFileConstants.CSI_CHUNK_SIZE * nChunks);
            }
        }

        //Update sequence position cache
        sequenceIndexes[sequenceIndex] = position();
    }

    private void readBytes(final byte[] bytes) {
        mIndexBuffer.readBytes(bytes);
    }

    private int readInteger() {
        return mIndexBuffer.readInteger();
    }

    private long readLong() {
        return mIndexBuffer.readLong();
    }

    private void skipBytes(final int count) {
        mIndexBuffer.skipBytes(count);
    }

    private void seek(final int position) {
        mIndexBuffer.seek(position);
    }

    private int position(){
        return mIndexBuffer.position();
    }
}
