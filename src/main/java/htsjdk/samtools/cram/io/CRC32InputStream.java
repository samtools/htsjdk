package htsjdk.samtools.cram.io;

import htsjdk.samtools.util.RuntimeIOException;

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
    public int read() {
        try {
            final int value = delegate.read();
            if (value != -1)
                crc32.update(value);
            return value;
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public int read(@SuppressWarnings("NullableProblems") final byte[] b) {
        try {
            final int result = delegate.read(b);
            if (result != -1)
                crc32.update(b, 0, result);
            return result;
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public int read(@SuppressWarnings("NullableProblems") final byte[] b, final int off, final int length) {
        try {
            final int result = delegate.read(b, off, length);
            crc32.update(b, off, result);
            return result;
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public long skip(final long n) {
        try {
            return delegate.skip(n);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public int available() {
        try {
            return delegate.available();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            delegate.close();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public void mark(final int readLimit) {
        delegate.mark(readLimit);
    }

    @Override
    public void reset() {
        try {
            delegate.reset();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }

}
