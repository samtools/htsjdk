package htsjdk.samtools.cram.io;

/**
 * Unsynchronized reader over a {@code byte[]} for CRAM codec decode operations. Replaces
 * {@link java.io.ByteArrayInputStream} in the hot decode path to eliminate the overhead of
 * synchronized {@code read()} methods (which showed up as ~10% of total decode CPU in profiling).
 *
 * <p>This is a final class (not an InputStream subclass) so the JIT can inline its methods.
 * Thread safety is explicitly not provided — CRAM codec operations are single-threaded.
 *
 * @see CRAMByteWriter
 */
public final class CRAMByteReader {
    private final byte[] buf;
    private int pos;

    /**
     * Create a reader over the given byte array, starting at position 0.
     *
     * @param buf the data to read from (not copied — caller must not modify while reading)
     */
    public CRAMByteReader(final byte[] buf) {
        this.buf = buf;
        this.pos = 0;
    }

    /**
     * Read one byte, returning it as an unsigned int (0-255), or -1 if at end of buffer.
     * Matches the contract of {@link java.io.InputStream#read()}.
     *
     * @return the next byte as an unsigned int, or -1 at end of data
     */
    public int read() {
        return pos < buf.length ? (buf[pos++] & 0xFF) : -1;
    }

    /**
     * Read up to {@code len} bytes into the destination array.
     *
     * @param b   destination array
     * @param off offset in destination to start writing
     * @param len maximum number of bytes to read
     * @return the number of bytes actually read, or -1 if at end of data
     */
    public int read(final byte[] b, final int off, final int len) {
        if (pos >= buf.length) return -1;
        final int toRead = Math.min(len, buf.length - pos);
        System.arraycopy(buf, pos, b, off, toRead);
        pos += toRead;
        return toRead;
    }

    /**
     * Read exactly {@code len} bytes, returning them as a new array.
     *
     * @param len number of bytes to read
     * @return a new byte array of length {@code len}
     * @throws IllegalStateException if fewer than {@code len} bytes remain
     */
    public byte[] readFully(final int len) {
        if (buf.length - pos < len) {
            throw new IllegalStateException(
                    String.format("Attempted to read %d bytes but only %d remain", len, buf.length - pos));
        }
        final byte[] result = new byte[len];
        System.arraycopy(buf, pos, result, 0, len);
        pos += len;
        return result;
    }

    /** Return the number of bytes remaining to be read. */
    public int available() {
        return buf.length - pos;
    }

    /** Return the current read position within the buffer. */
    public int getPosition() {
        return pos;
    }

    /** Return a reference to the underlying byte array. */
    public byte[] getBuffer() {
        return buf;
    }
}
