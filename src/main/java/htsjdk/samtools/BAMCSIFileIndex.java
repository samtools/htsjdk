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
import java.util.List;

public class BAMCSIFileIndex implements BrowseableBAMIndex {

    private int binDepth = 0;
    private int minShift = 0;
    private int maxBins = 0;
    private int maxSpan = 0;

    private final IndexFileBuffer mIndexBuffer;
    private SAMSequenceDictionary mBamDictionary = null;

    final int [] sequenceIndexes;

    protected BAMCSIFileIndex(
            final SeekableStream stream, final SAMSequenceDictionary dictionary)
    {
        mBamDictionary = dictionary;
        mIndexBuffer = new IndexStreamBuffer(stream);

        verifyBAMMagicNumber(stream.getSource());

        sequenceIndexes = new int[readInteger() + 1];
        Arrays.fill(sequenceIndexes, -1);
    }

    protected BAMCSIFileIndex(final File file, final SAMSequenceDictionary dictionary) {
        this(file, dictionary, true);
    }

    protected BAMCSIFileIndex(final File file, final SAMSequenceDictionary dictionary, final boolean useMemoryMapping) {
        mBamDictionary = dictionary;
        mIndexBuffer = (useMemoryMapping ? new MemoryMappedFileBuffer(file) : new RandomAccessFileBuffer(file));

        verifyBAMMagicNumber(file.getName());

        sequenceIndexes = new int[readInteger() + 1];
        Arrays.fill(sequenceIndexes, -1);
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
        if(bin.getBinNumber() > getMaxBins())
            throw new SAMException("Tried to get level for invalid bin.");

        for (int i = getBinDepth()-1; i > -1 ; i--) {
              if (bin.getBinNumber() >= getFirstBinOnLevel(i)) {
                  return i;
              }
        }
        throw new SAMException("Unable to find correct bin for bin " + bin);
    }

    @Override
    public int getFirstLocusInBin(Bin bin) {
        if(bin.getBinNumber() > getMaxBins())
            throw new SAMException("Tried to get first locus for invalid bin.");

        int level = getLevelForBin(bin);
        int firstBinOnLevel = getFirstBinOnLevel(level);
        int levelSize = getLevelSize(level);

        return (bin.getBinNumber() - firstBinOnLevel)*(getMaxSpan()/levelSize) + 1;
    }

    @Override
    public int getLastLocusInBin(Bin bin) {
        if(bin.getBinNumber() > getMaxBins())
            throw new SAMException("Tried to get last locus for invalid bin.");

        int level = getLevelForBin(bin);
        int firstBinOnLevel = getFirstBinOnLevel(level);
        int levelSize = getLevelSize(level);

        return (bin.getBinNumber() - firstBinOnLevel + 1)*(getMaxSpan()/levelSize);
    }

    @Override
    public BinList getBinsOverlapping(int referenceIndex, int startPos, int endPos) {
        return null;
    }

    @Override
    public BAMFileSpan getSpanOverlapping(Bin bin) {
        return null;
    }

    @Override
    public BAMFileSpan getSpanOverlapping(int referenceIndex, int startPos, int endPos) {
        return null;
    }

    @Override
    public long getStartOfLastLinearBin() {
        return 0;
    }


    @Override
    public void close() {

    }

    private void verifyBAMMagicNumber(final String sourceName) {
        // Verify the magic number.
        seek(0);
        final byte[] buffer = new byte[4];
        readBytes(buffer);
        if (!Arrays.equals(buffer, BAMFileConstants.CSI_INDEX_MAGIC)) {
            throw new RuntimeIOException("Invalid file header in BAM index " + sourceName +
                    ": " + new String(buffer));
        }
    }

    protected BAMIndexContent getQueryResults(int reference) {
        return null;
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
