package htsjdk.samtools.cram.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

/**
 * An input stream that calculates CRC32 of all the bytes passed through it. The java {@link java.util.zip.CRC32}
 * class is used to internally.
 */
public class CRC32_InputStream extends InputStream {
    private InputStream delegate;
    private CRC32 crc32 = new CRC32();

    public CRC32_InputStream(InputStream delegate) {
        super();
        this.delegate = delegate;
    }

    public int getCRC32() {
        return (int) (0xFFFFFFFF & crc32.getValue());
    }

    @Override
    public int read() throws IOException {
        int value = delegate.read();
        if (value != -1)
            crc32.update(value);
        return value;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int result = delegate.read(b);
        if (result != -1)
            crc32.update(b, 0, result);
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = delegate.read(b, off, len);
        crc32.update(b, off, result);
        return result;
    }

    @Override
    public long skip(long n) throws IOException {
        return delegate.skip(n);
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void mark(int readlimit) {
        delegate.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        delegate.reset();
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }

}
