package htsjdk.samtools;

import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class BAMCSIFileIndex implements BrowseableBAMIndex {

    private int binDepth;
    private int minShift;
    private int maxBins;
    private int maxSpan;
    private byte[] auxData;
    private int nReferences;

    private int metaDataPos = -1;

    private final IndexFileBuffer mIndexBuffer;
    private SAMSequenceDictionary mBamDictionary = null;

    final int [] sequenceIndexes;

    /**
     * Constructors
     */

    private BAMCSIFileIndex(final SeekableStream stream, final SAMSequenceDictionary dictionary)
    {
        mBamDictionary = dictionary;
        mIndexBuffer = new IndexStreamBuffer(stream);

        verifyBAMMagicNumber(stream.getSource());
        readMinShiftAndBinDepth();
        readAuxDataAndNRef();

        sequenceIndexes = new int[getNumberOfReferences() + 1];
        Arrays.fill(sequenceIndexes, -1);
    }

    public BAMCSIFileIndex(final File file, final SAMSequenceDictionary dictionary, final boolean useMemoryMapping) {
        mBamDictionary = dictionary;
        mIndexBuffer = (useMemoryMapping ? new MemoryMappedFileBuffer(file) : new RandomAccessFileBuffer(file));

        verifyBAMMagicNumber(file.getName());
        readMinShiftAndBinDepth();
        readAuxDataAndNRef();


        sequenceIndexes = new int[getNumberOfReferences() + 1];
        Arrays.fill(sequenceIndexes, -1);
    }

    public BAMCSIFileIndex(final File file, final SAMSequenceDictionary dictionary) {
        this(file, dictionary, true);
    }

    public BAMCSIFileIndex(final File file, final SAMSequenceDictionary dictionary, int minShift, int binDepth) {
        this(file, dictionary, true);
        setMinShift(minShift);
        setBinDepth(binDepth);
        setMaxBins(1<<3*(binDepth - 1) + 1);
        setMaxSpan(1<<(minShift + 3*(binDepth - 1)));
    }

    /**
     * Getters and setters
     */

    public int getBinDepth() {
        return binDepth;
    }

    public void setBinDepth(int binDepth) {
        this.binDepth = binDepth;
    }

    public int getMinShift() {
        return minShift;
    }

    public void setMinShift(int minShift) {
        this.minShift = minShift;
    }

    public int getMaxBins() {
        return maxBins;
    }

    public void setMaxBins(int maxBins) {
        this.maxBins = maxBins;
    }

    public int getMaxSpan() {
        return maxSpan;
    }

    public void setMaxSpan(int maxSpan) {
        this.maxSpan = maxSpan;
    }

    public byte[] getAuxData() { return auxData; }

    public void setAuxData(byte[] auxData) { this.auxData = auxData; }

    public int getNumberOfReferences() { return nReferences; }

    public void setNumberOfReferences(int nReferences) { this.nReferences = nReferences; }


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
            throw new SAMException("Tried to get level for invalid bin.");
        }
        for (int i = getBinDepth()-1; i > -1 ; i--) {
              if (bin.getBinNumber() >= getFirstBinOnLevel(i)) {
                  return i;
              }
        }
        throw new SAMException("Unable to find correct bin for bin " + bin);
    }

    @Override
    public int getFirstLocusInBin(Bin bin) {
        if(bin == null || bin.getBinNumber() > getMaxBins()) {
            throw new SAMException("Tried to get first locus for invalid bin.");
        }
        int level = getLevelForBin(bin);
        int firstBinOnLevel = getFirstBinOnLevel(level);
        int levelSize = getLevelSize(level);

        return (bin.getBinNumber() - firstBinOnLevel)*(getMaxSpan()/levelSize) + 1;
    }

    @Override
    public int getLastLocusInBin(Bin bin) {
        if(bin == null || bin.getBinNumber() > getMaxBins()) {
            throw new SAMException("Tried to get last locus for invalid bin.");
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
    public void close() {}

    private void verifyBAMMagicNumber(final String sourceName) {
        // Verify the magic number.
        if (BAMFileConstants.CSI_MAGIC_OFFSET != position()) {
            seek(BAMFileConstants.CSI_MAGIC_OFFSET);
        }
        final byte[] buffer = new byte[BAMFileConstants.CSI_MISHIFT_OFFSET];
        readBytes(buffer); // magic
        if (!Arrays.equals(buffer, BAMFileConstants.CSI_INDEX_MAGIC)) {
            throw new RuntimeIOException("Invalid file header in BAM CSI index " + sourceName +
                    ": " + new String(buffer));
        }
    }

    private void readMinShiftAndBinDepth() {
        if (BAMFileConstants.CSI_MISHIFT_OFFSET != position()) {
            seek(BAMFileConstants.CSI_MISHIFT_OFFSET);
        }
        setMinShift(readInteger()); // min_shift
        setBinDepth(readInteger() + 1); // depth - HTSlib doesn't count the first level (bin 0)
        setMaxBins(((1<<3*binDepth) - 1)/7);
        setMaxSpan(1<<(minShift + 3*(binDepth - 1)));
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
        if (binNumber > getMaxBins()) {
            throw new SAMException("Tried to get parent bin for invalid bin (" + binNumber + ").");
        }
        return (binNumber - 1) >> 3;
    }

    private int getParentBinNumber(Bin bin) {
        if (bin == null || bin.getBinNumber() > getMaxBins()) {
            throw new SAMException("Tried to get parent bin for invalid bin (" + bin.getBinNumber() + ").");
        }
        return (bin.getBinNumber() - 1) >> 3;
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
        // return 4680 + (sequenceLength >> 14); // note 4680 = getFirstBinInLevel(getNumIndexLevels() - 1)
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
            // System.out.println("# bin[" + binNumber + "] = " + indexBin + ", nChunks = " + nChunks + ", lOffset = " + lOffset);
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
                skipBytes(16 * nChunks);
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



    private void skipToSequence(final int sequenceIndex) {
        //Use sequence position cache if available
        if(sequenceIndexes[sequenceIndex] != -1){
            seek(sequenceIndexes[sequenceIndex]);
            return;
        }

        for (int i = 0; i < sequenceIndex; i++) {
            // System.out.println("# Sequence TID: " + i);
            final int nBins = readInteger(); // n_bin
            // System.out.println("# nBins: " + nBins);
            for (int j = 0; j < nBins; j++) {
                readInteger(); // bin
                readLong(); // loffset
                final int nChunks = readInteger(); // n_chunk
                // System.out.println("# bin[" + j + "] = " + bin + ", lOffset = " + lOffset + ", nChunks = " + nChunks);
                skipBytes(16 * nChunks);
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

    private abstract static class IndexFileBuffer {
        abstract void readBytes(final byte[] bytes);
        abstract int readInteger();
        abstract long readLong();
        abstract void skipBytes(final int count);
        abstract void seek(final int position);
        abstract int position();
        abstract void close();
    }

    /**
     * Traditional implementation of BAM index file access using memory mapped files.
     */
    private static class MemoryMappedFileBuffer extends IndexFileBuffer {
        private MappedByteBuffer mFileBuffer;

        MemoryMappedFileBuffer(final File file) {
            try {
                // Open the file stream.
                final FileInputStream fileStream = new FileInputStream(file);
                final FileChannel fileChannel = fileStream.getChannel();
                mFileBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0L, fileChannel.size());
                mFileBuffer.order(ByteOrder.LITTLE_ENDIAN);
                fileChannel.close();
                fileStream.close();
            } catch (final IOException exc) {
                throw new RuntimeIOException(exc.getMessage(), exc);
            }
        }

        @Override
        void readBytes(final byte[] bytes) {
            mFileBuffer.get(bytes);
        }

        @Override
        int readInteger() {
            return mFileBuffer.getInt();
        }

        @Override
        long readLong() {
            return mFileBuffer.getLong();
        }

        @Override
        void skipBytes(final int count) {
            mFileBuffer.position(mFileBuffer.position() + count);
        }

        @Override
        void seek(final int position) {
            mFileBuffer.position(position);
        }

        @Override
        int position() {
            return mFileBuffer.position();
        }

        @Override
        void close() {
            mFileBuffer = null;
        }
    }

    /**
     * Alternative implementation of BAM index file access using regular I/O instead of memory mapping.
     *
     * This implementation can be more scalable for certain applications that need to access large numbers of BAM files.
     * Java provides no way to explicitly release a memory mapping.  Instead, you need to wait for the garbage collector
     * to finalize the MappedByteBuffer.  Because of this, when accessing many BAM files or when querying many BAM files
     * sequentially, you cannot easily control the physical memory footprint of the java process.
     * This can limit scalability and can have bad interactions with load management software like LSF, forcing you
     * to reserve enough physical memory for a worst case scenario.
     * The use of regular I/O allows you to trade somewhat slower performance for a small, fixed memory footprint
     * if that is more suitable for your application.
     */
    private static class RandomAccessFileBuffer extends IndexFileBuffer {
        private static final int PAGE_SIZE = 4 * 1024;
        private static final int PAGE_OFFSET_MASK = PAGE_SIZE-1;
        private static final int PAGE_MASK = ~PAGE_OFFSET_MASK;
        private static final int INVALID_PAGE = 1;
        private final File mFile;
        private RandomAccessFile mRandomAccessFile;
        private final int mFileLength;
        private int mFilePointer = 0;
        private int mCurrentPage = INVALID_PAGE;
        private final byte[] mBuffer = new byte[PAGE_SIZE];

        RandomAccessFileBuffer(final File file) {
            mFile = file;
            try {
                mRandomAccessFile = new RandomAccessFile(file, "r");
                final long fileLength = mRandomAccessFile.length();
                if (fileLength > Integer.MAX_VALUE) {
                    throw new RuntimeIOException("BAM index file " + mFile + " is too large: " + fileLength);
                }
                mFileLength = (int) fileLength;
            } catch (final IOException exc) {
                throw new RuntimeIOException(exc.getMessage(), exc);
            }
        }

        @Override
        void readBytes(final byte[] bytes) {
            int resultOffset = 0;
            int resultLength = bytes.length;
            if (mFilePointer + resultLength > mFileLength) {
                throw new RuntimeIOException("Attempt to read past end of BAM index file (file is truncated?): " + mFile);
            }
            while (resultLength > 0) {
                loadPage(mFilePointer);
                final int pageOffset = mFilePointer & PAGE_OFFSET_MASK;
                final int copyLength = Math.min(resultLength, PAGE_SIZE - pageOffset);
                System.arraycopy(mBuffer, pageOffset, bytes, resultOffset, copyLength);
                mFilePointer += copyLength;
                resultOffset += copyLength;
                resultLength -= copyLength;
            }
        }

        @Override
        int readInteger() {
            // This takes advantage of the fact that integers in BAM index files are always 4-byte aligned.
            loadPage(mFilePointer);
            final int pageOffset = mFilePointer & PAGE_OFFSET_MASK;
            mFilePointer += 4;
            return((mBuffer[pageOffset + 0] & 0xFF) |
                    ((mBuffer[pageOffset + 1] & 0xFF) << 8) |
                    ((mBuffer[pageOffset + 2] & 0xFF) << 16) |
                    ((mBuffer[pageOffset + 3] & 0xFF) << 24));
        }

        @Override
        long readLong() {
            // BAM index files are always 4-byte aligned, but not necessrily 8-byte aligned.
            // So, rather than fooling with complex page logic we simply read the long in two 4-byte chunks.
            final long lower = readInteger();
            final long upper = readInteger();
            return ((upper << 32) | (lower & 0xFFFFFFFFL));
        }

        @Override
        void skipBytes(final int count) {
            mFilePointer += count;
        }

        @Override
        void seek(final int position) {
            mFilePointer = position;
        }

        @Override
        int position() {
            return mFilePointer;
        }

        @Override
        void close() {
            mFilePointer = 0;
            mCurrentPage = INVALID_PAGE;
            if (mRandomAccessFile != null) {
                try {
                    mRandomAccessFile.close();
                } catch (final IOException exc) {
                    throw new RuntimeIOException(exc.getMessage(), exc);
                }
                mRandomAccessFile = null;
            }
        }

        private void loadPage(final int filePosition) {
            final int page = filePosition & PAGE_MASK;
            if (page == mCurrentPage) {
                return;
            }
            try {
                mRandomAccessFile.seek(page);
                final int readLength = Math.min(mFileLength - page, PAGE_SIZE);
                mRandomAccessFile.readFully(mBuffer, 0, readLength);
                mCurrentPage = page;
            } catch (final IOException exc) {
                throw new RuntimeIOException("Exception reading BAM index file " + mFile + ": " + exc.getMessage(), exc);
            }
        }
    }

    static class IndexStreamBuffer extends IndexFileBuffer {
        private final SeekableStream in;
        private final ByteBuffer tmpBuf;

        /** Continually reads from the provided {@link SeekableStream} into the buffer until the specified number of bytes are read, or
         * until the stream is exhausted, throwing a {@link RuntimeIOException}. */
        private static void readFully(final SeekableStream in, final byte[] buffer, final int offset, final int length) {
            int read = 0;
            while (read < length) {
                final int readThisLoop;
                try {
                    readThisLoop = in.read(buffer, read, length - read);
                } catch (final IOException e) {
                    throw new RuntimeIOException(e);
                }
                if (readThisLoop == -1) break;
                read += readThisLoop;
            }
            if (read != length) throw new RuntimeIOException("Expected to read " + length + " bytes, but expired stream after " + read + ".");
        }

        public IndexStreamBuffer(final SeekableStream s) {
            in = s;
            tmpBuf = ByteBuffer.allocate(8); // Enough to fit a long.
            tmpBuf.order(ByteOrder.LITTLE_ENDIAN);
        }

        @Override
        public void close() {
            try { in.close(); }
            catch (final IOException e) { throw new RuntimeIOException(e); }
        }

        @Override
        public void readBytes(final byte[] bytes) {
            readFully(in, bytes, 0, bytes.length);
        }

        @Override
        public void seek(final int position) {
            try { in.seek(position); }
            catch (final IOException e) { throw new RuntimeIOException(e); }
        }

        @Override
        public int readInteger() {
            readFully(in, tmpBuf.array(), 0, 4);
            return tmpBuf.getInt(0);
        }

        @Override
        public long readLong() {
            readFully(in, tmpBuf.array(), 0, 8);
            return tmpBuf.getLong(0);
        }

        @Override
        public void skipBytes(final int count) {
            try {
                for (int s = count; s > 0;) {
                    final int skipped = (int)in.skip(s);
                    if (skipped <= 0)
                        throw new RuntimeIOException("Failed to skip " + s);
                    s -= skipped;
                }
            } catch (final IOException e) { throw new RuntimeIOException(e); }
        }

        @Override
        public int position() {
            try {
                return (int) in.position();
            } catch (final IOException e) { throw new RuntimeIOException(e); }
        }
    }


}
