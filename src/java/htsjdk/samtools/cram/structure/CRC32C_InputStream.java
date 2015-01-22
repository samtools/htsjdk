package htsjdk.samtools.cram.structure;

import java.io.IOException;
import java.io.InputStream;

public class CRC32C_InputStream extends InputStream {
    private InputStream delegate;
    private CRC32C crc32 = new CRC32C();

    public CRC32C_InputStream(InputStream delegate) {
        super();
        this.delegate = delegate;
    }

    public int getCRC32C() {
        return crc32.getIntValue();
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
