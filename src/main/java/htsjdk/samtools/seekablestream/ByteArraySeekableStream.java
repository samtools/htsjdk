package htsjdk.samtools.seekablestream;

import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.IOException;

/**
* Created by vadim on 23/03/2015.
*/
public class ByteArraySeekableStream extends SeekableStream {
    private byte[] bytes;
    private long position = 0;

    public ByteArraySeekableStream(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public long length() {
        return bytes.length;
    }

    @Override
    public long position() throws IOException {
        return position;
    }

    @Override
    public void seek(long position) throws IOException {
        this.position = position;
    }

    @Override
    public int read() throws IOException {
        if (position < bytes.length)
            return 0xFF & bytes[((int) position++)];
        else return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (position >= bytes.length) {
            return -1;
        }
        if (position + len > bytes.length) {
            len = (int) (bytes.length - position);
        }
        if (len <= 0) {
            return 0;
        }
        System.arraycopy(bytes, (int) position, b, off, len);
        position += len;
        return len;
    }

    @Override
    public void close() throws IOException {
        bytes = null;
    }

    @Override
    public boolean eof() throws IOException {
        return position >= bytes.length;
    }

    @Override
    public String getSource() {
        return null;
    }
}
