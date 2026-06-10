package htsjdk.samtools;

import htsjdk.samtools.util.RuntimeIOException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
class RandomAccessFileBuffer implements IndexFileBuffer {
    private static final int PAGE_SIZE = 4 * 1024;
    private static final int PAGE_OFFSET_MASK = PAGE_SIZE - 1;
    private static final int PAGE_MASK = ~PAGE_OFFSET_MASK;
    private static final int INVALID_PAGE = 1;
    private final Path mPath;
    private FileChannel mFileChannel;
    private final int mFileLength;
    private long mFilePointer = 0;
    private int mCurrentPage = INVALID_PAGE;
    private final byte[] mBuffer = new byte[PAGE_SIZE];

    RandomAccessFileBuffer(final Path path) {
        mPath = path;
        try {
            mFileChannel = FileChannel.open(path, StandardOpenOption.READ);
            final long fileLength = mFileChannel.size();
            if (fileLength > Integer.MAX_VALUE) {
                throw new RuntimeIOException("BAM index file " + mPath + " is too large: " + fileLength);
            }
            mFileLength = (int) fileLength;
        } catch (final IOException exc) {
            throw new RuntimeIOException(exc.getMessage(), exc);
        }
    }

    @Override
    public void readBytes(final byte[] bytes) {
        int resultOffset = 0;
        int resultLength = bytes.length;
        if (mFilePointer + resultLength > mFileLength) {
            throw new RuntimeIOException("Attempt to read past end of BAM index file (file is truncated?): " + mPath);
        }
        while (resultLength > 0) {
            loadPage(mFilePointer);
            final int pageOffset = (int) mFilePointer & PAGE_OFFSET_MASK;
            final int copyLength = Math.min(resultLength, PAGE_SIZE - pageOffset);
            System.arraycopy(mBuffer, pageOffset, bytes, resultOffset, copyLength);
            mFilePointer += copyLength;
            resultOffset += copyLength;
            resultLength -= copyLength;
        }
    }

    @Override
    public int readInteger() {
        // This takes advantage of the fact that integers in BAM index files are always 4-byte aligned.
        loadPage(mFilePointer);
        final int pageOffset = (int) mFilePointer & PAGE_OFFSET_MASK;
        mFilePointer += 4;
        return ((mBuffer[pageOffset + 0] & 0xFF)
                | ((mBuffer[pageOffset + 1] & 0xFF) << 8)
                | ((mBuffer[pageOffset + 2] & 0xFF) << 16)
                | ((mBuffer[pageOffset + 3] & 0xFF) << 24));
    }

    @Override
    public long readLong() {
        // BAM index files are always 4-byte aligned, but not necessrily 8-byte aligned.
        // So, rather than fooling with complex page logic we simply read the long in two 4-byte chunks.
        final long lower = readInteger();
        final long upper = readInteger();
        return ((upper << 32) | (lower & 0xFFFFFFFFL));
    }

    @Override
    public void skipBytes(final int count) {
        mFilePointer += count;
    }

    @Override
    public void seek(final long position) {
        mFilePointer = position;
    }

    @Override
    public long position() {
        return mFilePointer;
    }

    @Override
    public void close() {
        mFilePointer = 0;
        mCurrentPage = INVALID_PAGE;
        if (mFileChannel != null) {
            try {
                mFileChannel.close();
            } catch (final IOException exc) {
                throw new RuntimeIOException(exc.getMessage(), exc);
            }
            mFileChannel = null;
        }
    }

    private void loadPage(final long filePosition) {
        final int page = (int) filePosition & PAGE_MASK;
        if (page == mCurrentPage) {
            return;
        }
        try {
            mFileChannel.position(page);
            final int readLength = Math.min(mFileLength - page, PAGE_SIZE);
            int totalRead = 0;
            while (totalRead < readLength) {
                final int bytesRead = mFileChannel.read(ByteBuffer.wrap(mBuffer, totalRead, readLength - totalRead));
                if (bytesRead < 0) {
                    throw new IOException("Unexpected end of file");
                }
                totalRead += bytesRead;
            }
            mCurrentPage = page;
        } catch (final IOException exc) {
            throw new RuntimeIOException("Exception reading BAM index file " + mPath + ": " + exc.getMessage(), exc);
        }
    }
}
