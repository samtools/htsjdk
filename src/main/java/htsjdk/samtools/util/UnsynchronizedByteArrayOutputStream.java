package htsjdk.samtools.util;

import htsjdk.utils.ValidationUtils;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * A growable in-memory byte sink, like {@link java.io.ByteArrayOutputStream} but without its synchronization.
 *
 * <p>{@link java.io.ByteArrayOutputStream} takes a lock on every call, which costs more than the write itself when a
 * single thread writes a byte or a few bytes at a time. Use this instead on such paths; it is not safe to share between
 * threads.
 */
public final class UnsynchronizedByteArrayOutputStream extends OutputStream {
    // Arrays a little smaller than Integer.MAX_VALUE, as some VMs reserve header words in an array
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private byte[] buffer;
    private int count;

    /**
     * @param initialCapacity the number of bytes the buffer holds before it first grows
     * @throws IllegalArgumentException if {@code initialCapacity} is negative
     */
    public UnsynchronizedByteArrayOutputStream(final int initialCapacity) {
        ValidationUtils.validateArg(initialCapacity >= 0, () -> "Negative initial capacity: " + initialCapacity);
        this.buffer = new byte[initialCapacity];
    }

    @Override
    public void write(final int b) {
        if (count == buffer.length) {
            ensureCapacity(count + 1);
        }
        buffer[count++] = (byte) b;
    }

    @Override
    public void write(final byte[] bytes, final int offset, final int length) {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        ensureCapacity(count + length);
        System.arraycopy(bytes, offset, buffer, count, length);
        count += length;
    }

    /** @return the number of bytes written since construction or the last {@link #reset()} */
    public int size() {
        return count;
    }

    /** Discards the bytes written so far, keeping the buffer for reuse. */
    public void reset() {
        count = 0;
    }

    /** @return a copy of the bytes written since construction or the last {@link #reset()} */
    public byte[] toByteArray() {
        return Arrays.copyOf(buffer, count);
    }

    private void ensureCapacity(final int minCapacity) {
        if (minCapacity < 0 || minCapacity > MAX_ARRAY_SIZE) {
            // minCapacity is negative when count + length overflowed
            throw new OutOfMemoryError("Cannot hold more than " + MAX_ARRAY_SIZE + " bytes");
        }
        if (minCapacity > buffer.length) {
            final long doubled = 2L * buffer.length;
            buffer = Arrays.copyOf(buffer, (int) Math.min(Math.max(doubled, minCapacity), MAX_ARRAY_SIZE));
        }
    }
}
