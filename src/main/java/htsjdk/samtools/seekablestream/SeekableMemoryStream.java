package htsjdk.samtools.seekablestream;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SeekableMemoryStream extends SeekableStream {
    private final ByteBuffer buf;
    private final String source;

    public SeekableMemoryStream(final byte[] data, final String source) {
        this.buf = ByteBuffer.wrap(data);
        this.source = source;
    }

    @Override
    public void close() throws IOException {
        buf.clear();
    }

    @Override
    public boolean eof() throws IOException {
        return buf.position() == buf.limit();
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public long length() {
        return buf.array().length - buf.arrayOffset();
    }

    @Override
    public int read(final byte[] buffer, final int offset, final int length) throws IOException {
        int availableLength = Math.min(length, buf.remaining());
        if (availableLength < 1) {
            return -1;
        }
        buf.get(buffer, offset, availableLength);
        return availableLength;
    }

    @Override
    public void seek(final long position) throws IOException {
        buf.position((int) position);
    }

    @Override
    public int read() throws IOException {
        if (buf.position() < buf.limit()) {
            return buf.get() & 0xFF;
        } else {
            return -1;
        }
    }

    @Override
    public long position() throws IOException {
        return buf.position();
    }

}
