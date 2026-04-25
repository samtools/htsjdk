package htsjdk.samtools.cram.io;

import java.util.Arrays;

/**
 * Unsynchronized growable byte writer for CRAM codec encode operations. Replaces
 * {@link java.io.ByteArrayOutputStream} in the hot encode path to eliminate the overhead of
 * synchronized {@code write()} methods.
 *
 * <p>The internal buffer doubles in size when full, matching the growth strategy of
 * {@link java.io.ByteArrayOutputStream}. This is a final class (not an OutputStream subclass)
 * so the JIT can inline its methods. Thread safety is explicitly not provided — CRAM codec
 * operations are single-threaded.
 *
 * @see CRAMByteReader
 */
public final class CRAMByteWriter {
    private byte[] buf;
    private int pos;

    /** Create a writer with a default initial capacity of 256 bytes. */
    public CRAMByteWriter() {
        this(256);
    }

    /**
     * Create a writer with the specified initial capacity.
     *
     * @param initialCapacity initial buffer size in bytes
     */
    public CRAMByteWriter(final int initialCapacity) {
        this.buf = new byte[initialCapacity];
        this.pos = 0;
    }

    /**
     * Write a single byte.
     *
     * @param b the byte to write (only the low 8 bits are used)
     */
    public void write(final int b) {
        if (pos == buf.length) {
            grow(pos + 1);
        }
        buf[pos++] = (byte) b;
    }

    /**
     * Write all bytes from the given array.
     *
     * @param b the bytes to write
     */
    public void write(final byte[] b) {
        write(b, 0, b.length);
    }

    /**
     * Write {@code len} bytes from the given array starting at offset {@code off}.
     *
     * @param b   source array
     * @param off offset in source to start reading
     * @param len number of bytes to write
     */
    public void write(final byte[] b, final int off, final int len) {
        if (pos + len > buf.length) {
            grow(pos + len);
        }
        System.arraycopy(b, off, buf, pos, len);
        pos += len;
    }

    /**
     * Return a copy of the bytes written so far. Matches the contract of
     * {@link java.io.ByteArrayOutputStream#toByteArray()}.
     *
     * @return a new byte array containing the written data
     */
    public byte[] toByteArray() {
        return Arrays.copyOf(buf, pos);
    }

    /** Return the number of bytes written so far. */
    public int size() {
        return pos;
    }

    /** Return the current write position (alias for {@link #size()}). */
    public int getPosition() {
        return pos;
    }

    /** Reset the writer to empty, reusing the existing buffer. */
    public void reset() {
        pos = 0;
    }

    private void grow(final int minCapacity) {
        // Use Math.max to handle potential overflow when buf.length > 1GB
        int newCapacity = Math.max(buf.length << 1, minCapacity);
        if (newCapacity < 0) {
            // Overflow — fall back to exact size needed
            newCapacity = minCapacity;
        }
        buf = Arrays.copyOf(buf, newCapacity);
    }
}
