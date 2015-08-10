package htsjdk.samtools.seekablestream;

import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SeekableMemoryStream extends SeekableStream {
    private ByteBuffer buf;
    private String source;

    public SeekableMemoryStream(ByteBuffer buf, String source) {
        this.buf = buf;
        this.source = source;
    }

    public SeekableMemoryStream(byte[] data, int offset, int len, String source) {
        this.buf = ByteBuffer.wrap(data, offset, len);
        this.source = source;
    }

    public SeekableMemoryStream(byte[] data, String source) {
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
    public int read(byte[] buffer, int offset, int length) throws IOException {
        length = Math.min(length, buf.remaining());
        if (length == 0)
            return -1;
        buf.get(buffer, offset, length);
        return length;
    }

    @Override
    public void seek(long position) throws IOException {
        buf.position((int) position);
    }

    @Override
    public int read() throws IOException {
        if (buf.position() < buf.limit())
            return buf.get();
        else
            return -1;
    }

    @Override
    public long position() throws IOException {
        return buf.position();
    }

}
