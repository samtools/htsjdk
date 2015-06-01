package htsjdk.samtools.cram.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

/**
 * An input stream that calculates CRC32 of all the bytes passed through it. The java {@link java.util.zip.CRC32}
 * class is used to internally.
 */
public class CRC32InputStream extends InputStream {
    private final InputStream delegate;
    private final CRC32 crc32 = new CRC32();

    public CRC32InputStream(final InputStream delegate) {
        super();
        this.delegate = delegate;
    }

    public int getCRC32() {
        return (int) (crc32.getValue());
    }

    @Override
    public int read() throws IOException {
        final int value = delegate.read();
        if (value != -1)
            crc32.update(value);
        return value;
    }

    @Override
    public int read(@SuppressWarnings("NullableProblems") final byte[] b) throws IOException {
        final int result = delegate.read(b);
        if (result != -1)
            crc32.update(b, 0, result);
        return result;
    }

    @Override
    public int read(@SuppressWarnings("NullableProblems") final byte[] b, final int off, final int length) throws IOException {
        final int result = delegate.read(b, off, length);
        crc32.update(b, off, result);
        return result;
    }

    @Override
    public long skip(final long n) throws IOException {
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
    public void mark(final int readLimit) {
        delegate.mark(readLimit);
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
