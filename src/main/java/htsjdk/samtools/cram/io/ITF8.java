package htsjdk.samtools.cram.io;

import htsjdk.samtools.util.RuntimeEOFException;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Methods to read and write int values as per ITF8 specification in CRAM.
 *
 * ITF8 encodes ints as 1 to 5 bytes depending on the highest set bit.
 *
 * (using 1-based counting)
 * If highest bit < 8:
 *      write out [bits 1-8]
 * Highest bit = 8-14:
 *      write a byte 1,0,[bits 9-14]
 *      write out [bits 1-8]
 * Highest bit = 15-21:
 *      write a byte 1,1,0,[bits 17-21]
 *      write out [bits 9-16]
 *      write out [bits 1-8]
 * Highest bit = 22-28:
 *      write a byte 1,1,1,0,[bits 25-28]
 *      write out [bits 17-24]
 *      write out [bits 9-16]
 *      write out [bits 1-8]
 * Highest bit > 28:
 *      write a byte 1,1,1,1,[bits 29-32]
 *      write out [bits 21-28]                      **** note the change in pattern here
 *      write out [bits 13-20]
 *      write out [bits 5-12]
 *      write out [bits 1-8]
 *
 */
public class ITF8 {

    static final int MAX_BYTES = 5;
    static final int MAX_BITS = 8 * MAX_BYTES;

    /**
     * Reads an unsigned (32 bit) integer from an {@link InputStream}. The sign bit should be interpreted as a value bit.
     *
     * @param inputStream the stream to read from
     * @return the value read
     */
    public static int readUnsignedITF8(final InputStream inputStream) {
        try {
            final int b1 = inputStream.read();
            if (b1 == -1)
                throw new RuntimeEOFException();

            if ((b1 & 128) == 0)
                return b1;

            if ((b1 & 64) == 0)
                return ((b1 & 127) << 8) | inputStream.read();

            if ((b1 & 32) == 0) {
                final int b2 = inputStream.read();
                final int b3 = inputStream.read();
                return ((b1 & 63) << 16) | b2 << 8 | b3;
            }

            if ((b1 & 16) == 0)
                return ((b1 & 31) << 24) | inputStream.read() << 16 | inputStream.read() << 8 | inputStream.read();

            return ((b1 & 15) << 28) | inputStream.read() << 20 | inputStream.read() << 12 | inputStream.read() << 4 | (15 & inputStream.read());
        }
        catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Reads an unsigned (32 bit) integer from a {@link ByteBuffer}. The sign bit should be interpreted as a value bit.
     *
     * @param buffer the bytes to read from
     * @return unsigned integer value from the buffer
     */
    public static int readUnsignedITF8(final ByteBuffer buffer) {
        final int b1 = 0xFF & buffer.get();

        if ((b1 & 128) == 0)
            return b1;

        if ((b1 & 64) == 0)
            return ((b1 & 127) << 8) | (0xFF & buffer.get());

        if ((b1 & 32) == 0) {
            final int b2 = 0xFF & buffer.get();
            final int b3 = 0xFF & buffer.get();
            return ((b1 & 63) << 16) | b2 << 8 | b3;
        }

        if ((b1 & 16) == 0)
            return ((b1 & 31) << 24) | (0xFF & buffer.get()) << 16 | (0xFF & buffer.get()) << 8 | (0xFF & buffer.get());

        return ((b1 & 15) << 28) | (0xFF & buffer.get()) << 20 | (0xFF & buffer.get()) << 12 | (0xFF & buffer.get()) << 4
                | (15 & buffer.get());
    }

    /**
     * Reads an unsigned (32 bit) integer from an array of bytes. The sign bit should be interpreted as a value bit.
     *
     * @param data the bytes to read from
     * @return the value read
     */
    public static int readUnsignedITF8(final byte[] data) {
        final ByteBuffer buffer = ByteBuffer.wrap(data);
        final int value = readUnsignedITF8(buffer);
        buffer.clear();

        return value;
    }

    /**
     * Writes an unsigned (32 bit) integer to an {@link OutputStream} encoded as ITF8. The sign bit is interpreted as a value bit.
     *
     * @param value the value to be written out
     * @param outputStream    the stream to write to
     * @return number of bits written
     */
    public static int writeUnsignedITF8(final int value, final OutputStream outputStream) {
        try {
            if ((value >>> 7) == 0) {
                // no control bits
                outputStream.write(value);
                return 8;
            }

            if ((value >>> 14) == 0) {
                // 1 control bit
                outputStream.write(((value >> 8) | 0x80));
                outputStream.write((value & 0xFF));
                return 16;
            }

            if ((value >>> 21) == 0) {
                // 2 control bits
                outputStream.write(((value >> 16) | 0xC0));
                outputStream.write(((value >> 8) & 0xFF));
                outputStream.write((value & 0xFF));
                return 24;
            }

            if ((value >>> 28) == 0) {
                // 3 control bits
                outputStream.write(((value >> 24) | 0xE0));
                outputStream.write(((value >> 16) & 0xFF));
                outputStream.write(((value >> 8) & 0xFF));
                outputStream.write((value & 0xFF));
                return 32;
            }

            // 4 control bits
            outputStream.write(((value >> 28) | 0xF0));
            outputStream.write(((value >> 20) & 0xFF));
            outputStream.write(((value >> 12) & 0xFF));
            outputStream.write(((value >> 4) & 0xFF));
            outputStream.write((value & 0xFF));
            return 40;
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Writes an unsigned (32 bit) integer to an {@link OutputStream} encoded as ITF8. The sign bit is interpreted as a value bit.
     *
     * @param value the value to be written out
     * @param buffer   the {@link ByteBuffer} to write to
     */
    public static int writeUnsignedITF8(final int value, final ByteBuffer buffer) {
        if ((value >>> 7) == 0) {
            // no control bits
            buffer.put((byte) value);
            return 8;
        }

        if ((value >>> 14) == 0) {
            // 1 control bit
            buffer.put((byte) ((value >> 8) | 0x80));
            buffer.put((byte) (value & 0xFF));
            return 16;
        }

        if ((value >>> 21) == 0) {
            // 2 control bits
            buffer.put((byte) ((value >> 16) | 0xC0));
            buffer.put((byte) ((value >> 8) & 0xFF));
            buffer.put((byte) (value & 0xFF));
            return 24;
        }

        if ((value >>> 28) == 0) {
            // 3 control bits
            buffer.put((byte) ((value >> 24) | 0xE0));
            buffer.put((byte) ((value >> 16) & 0xFF));
            buffer.put((byte) ((value >> 8) & 0xFF));
            buffer.put((byte) (value & 0xFF));
            return 32;
        }

        // 4 control bits
        buffer.put((byte) ((value >> 28) | 0xF0));
        buffer.put((byte) ((value >> 20) & 0xFF));
        buffer.put((byte) ((value >> 12) & 0xFF));
        buffer.put((byte) ((value >> 4) & 0xFF));
        buffer.put((byte) (value & 0xFF));
        return 40;
    }

    /**
     * Writes an unsigned (32 bit) integer to a byte new array encoded as ITF8. The sign bit is interpreted as a value bit.
     *
     * @param value the value to be written out
     * @return the bytes holding ITF8 representation of the value
     */
    public static byte[] writeUnsignedITF8(final int value) {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        writeUnsignedITF8(value, buffer);

        buffer.flip();
        final byte[] array = new byte[buffer.limit()];
        buffer.get(array);

        buffer.clear();
        return array;
    }

}
