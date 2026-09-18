package htsjdk.samtools;

import htsjdk.samtools.util.BlockCompressedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Hands a block-compressed stream to a reader that buffers, such as a line reader, without losing track of where
 * in the file each byte came from. A reader that buffers runs ahead of what its caller has used, so the stream's
 * own file pointer says nothing about a line; but if no read ever crosses a BGZF block, a byte's virtual offset
 * follows from where the read it came in began. The reader must hold the bytes of one read at a time, which
 * {@link htsjdk.samtools.util.SamLineReader} does.
 */
final class BlockAtATimeInputStream extends InputStream {
    private final BlockCompressedInputStream blockCompressedStream;

    // The latest read: how many bytes had been handed over before it, how long it was, and the virtual offsets
    // at which it began and ended.
    private long bytesBeforeRead;
    private int readLength;
    private long pointerBeforeRead;
    private long pointerAfterRead;

    BlockAtATimeInputStream(final BlockCompressedInputStream blockCompressedStream) {
        this.blockCompressedStream = blockCompressedStream;
        pointerBeforeRead = blockCompressedStream.getFilePointer();
        pointerAfterRead = pointerBeforeRead;
    }

    /** Reads from the current block only, so fewer bytes than asked for is the usual result. */
    @Override
    public int read(final byte[] buffer, final int offset, final int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        final int leftInBlock = blockCompressedStream.available(); // moves on to the next block if this one is spent
        if (leftInBlock == 0) {
            return -1;
        }
        bytesBeforeRead += readLength;
        pointerBeforeRead = blockCompressedStream.getFilePointer();
        readLength = blockCompressedStream.read(buffer, offset, Math.min(length, leftInBlock));
        pointerAfterRead = blockCompressedStream.getFilePointer();
        return readLength;
    }

    @Override
    public int read() throws IOException {
        final byte[] single = new byte[1];
        return read(single, 0, 1) == -1 ? -1 : single[0] & 0xff;
    }

    /**
     * The virtual offset of a byte of the latest read, or of the byte that follows it.
     *
     * @param byteCount how many bytes of the stream precede the byte in question, counted from the first byte this
     *     stream handed over
     */
    long filePointerAt(final long byteCount) {
        final long intoRead = byteCount - bytesBeforeRead;
        if (intoRead < 0 || intoRead > readLength) {
            throw new IllegalStateException("Byte " + byteCount + " is not part of the latest read");
        }
        // Just past the read is wherever the stream says it now is: at the end of a block, that is the start of
        // the next one, which is how an index made by htslib would put it too.
        return intoRead == readLength ? pointerAfterRead : pointerBeforeRead + intoRead;
    }

    /**
     * Moves to a virtual offset. Whatever the reader above has buffered is stale from here on, and it must be told
     * so; its count of bytes consumed then matches this stream's again.
     */
    void seek(final long filePointer) throws IOException {
        blockCompressedStream.seek(filePointer);
        bytesBeforeRead += readLength;
        readLength = 0;
        pointerBeforeRead = filePointer;
        pointerAfterRead = filePointer;
    }

    @Override
    public void close() throws IOException {
        blockCompressedStream.close();
    }
}
