package htsjdk.samtools;

import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class IndexStreamBuffer implements IndexFileBuffer {
    private final SeekableStream in;
    private final ByteBuffer tmpBuf;

    /** Continually reads from the provided {@link SeekableStream} into the buffer until the specified number of bytes are read, or
     * until the stream is exhausted, throwing a {@link RuntimeIOException}. */
    private static void readFully(final SeekableStream in, final byte[] buffer, final int length) {
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
        readFully(in, bytes, bytes.length);
    }

    @Override
    public void seek(final long position) {
        try { in.seek(position); }
        catch (final IOException e) { throw new RuntimeIOException(e); }
    }

    @Override
    public int readInteger() {
        readFully(in, tmpBuf.array(), 4);
        return tmpBuf.getInt(0);
    }

    @Override
    public long readLong() {
        readFully(in, tmpBuf.array(), 8);
        return tmpBuf.getLong(0);
    }

    @Override
    public void skipBytes(final int count) {
        try {
            for (int s = count; s > 0;) {
                final int skipped = (int)in.skip(s);
                if (skipped <= 0) {
                    throw new RuntimeIOException("Failed to skip " + s);
                }
                s -= skipped;
            }
        } catch (final IOException e) { throw new RuntimeIOException(e); }
    }

    @Override
    public long position() {
        try {
            return (int) in.position();
        } catch (final IOException e) { throw new RuntimeIOException(e); }
    }
}
