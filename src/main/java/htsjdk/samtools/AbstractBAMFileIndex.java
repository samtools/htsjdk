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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
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

    private SAMSequenceDictionary mBamDictionary = null;
    
    final int [] sequenceIndexes;

    protected AbstractBAMFileIndex(
        final SeekableStream stream, final SAMSequenceDictionary dictionary)
    {
        mBamDictionary = dictionary;
        mIndexBuffer = new IndexStreamBuffer(stream);

        verifyBAMMagicNumber(stream.getSource());

        sequenceIndexes = new int[readInteger() + 1];
        Arrays.fill(sequenceIndexes, -1);
    }

    protected AbstractBAMFileIndex(final File file, final SAMSequenceDictionary dictionary) {
        this(file, dictionary, true);
    }

    protected AbstractBAMFileIndex(final File file, final SAMSequenceDictionary dictionary, final boolean useMemoryMapping) {
        mBamDictionary = dictionary;
        mIndexBuffer = (useMemoryMapping ? new MemoryMappedFileBuffer(file) : new RandomAccessFileBuffer(file));

        verifyBAMMagicNumber(file.getName());

        sequenceIndexes = new int[readInteger() + 1];
        Arrays.fill(sequenceIndexes, -1);
    }

    /**
     * Close this index and release any associated resources.
     */
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

    /**
     * Gets the first bin in the given level.
     * @param levelNumber Level number.  0-based.
     * @return The first bin in this level.
     */
    public static int getFirstBinInLevel(final int levelNumber) {
        return GenomicIndexUtil.LEVEL_STARTS[levelNumber];
    }

    /**
     * Gets the number of bins in the given level.
     * @param levelNumber Level number.  0-based.
     * @return The size (number of possible bins) of the given level.
     */
    public int getLevelSize(final int levelNumber) {
        if(levelNumber == getNumIndexLevels())
            return GenomicIndexUtil.MAX_BINS+1-GenomicIndexUtil.LEVEL_STARTS[levelNumber];
        else
            return GenomicIndexUtil.LEVEL_STARTS[levelNumber+1]-GenomicIndexUtil.LEVEL_STARTS[levelNumber];
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
                for (int ci = 0; ci < nChunks; ci++) {
                    final long chunkBegin = readLong();
                    final long chunkEnd = readLong();
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
                for (int ci = 0; ci < nChunks; ci++) {
                    final long chunkBegin = readLong();
                    final long chunkEnd = readLong();
                    lastChunk = new Chunk(chunkBegin, chunkEnd);
                    chunks.add(lastChunk);
                }
            } else if (indexBin == GenomicIndexUtil.MAX_BINS) {
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
     * Get candidate bins for the specified region
     * @param startPos 1-based start of target region, inclusive.
     * @param endPos 1-based end of target region, inclusive.
     * @return bit set for each bin that may contain SAMRecords in the target region.
     */
    protected BitSet regionToBins(final int startPos, final int endPos) {
        final int maxPos = 0x1FFFFFFF;
        final int start = (startPos <= 0) ? 0 : (startPos-1) & maxPos;
        final int end = (endPos <= 0) ? maxPos : (endPos-1) & maxPos;
        if (start > end) {
            return null;
        }
        int k;
        final BitSet bitSet = new BitSet(GenomicIndexUtil.MAX_BINS);
        bitSet.set(0);
        for (k =    1 + (start>>26); k <=    1 + (end>>26); ++k) bitSet.set(k);
        for (k =    9 + (start>>23); k <=    9 + (end>>23); ++k) bitSet.set(k);
        for (k =   73 + (start>>20); k <=   73 + (end>>20); ++k) bitSet.set(k);
        for (k =  585 + (start>>17); k <=  585 + (end>>17); ++k) bitSet.set(k);
        for (k = 4681 + (start>>14); k <= 4681 + (end>>14); ++k) bitSet.set(k);
        return bitSet;
    }

    /**
     * @deprecated Invoke htsjdk.samtools.Chunk#optimizeChunkList(java.util.List<htsjdk.samtools.Chunk>, long) directly.
     */
    protected List<Chunk> optimizeChunkList(final List<Chunk> chunks, final long minimumOffset) {
        return Chunk.optimizeChunkList(chunks, minimumOffset);
    }

    private void verifyBAMMagicNumber(final String sourceName) {
        // Verify the magic number.
        seek(0);
        final byte[] buffer = new byte[4];
        readBytes(buffer);
        if (!Arrays.equals(buffer, BAMFileConstants.BAM_INDEX_MAGIC)) {
            throw new RuntimeIOException("Invalid file header in BAM index " + sourceName +
                    ": " + new String(buffer));
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
