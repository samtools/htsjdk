package htsjdk.samtools.cram.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Methods to read and write int values as per ITF8 specification in CRAM.
 */
public class ITF8 {

    /**
     * Reads an unsigned (32 bit) integer from an {@link InputStream}. The sign bit should be interpreted as a value bit.
     *
     * @param inputStream the stream to read from
     * @return the value read
     * @throws IOException as per java IO contract
     */
    public static int readUnsignedITF8(final InputStream inputStream) throws IOException {
        final int b1 = inputStream.read();
        if (b1 == -1)
            throw new EOFException();

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

    /**
     * Writes an unsigned (32 bit) integer to an {@link OutputStream} encoded as ITF8. The sign bit is interpreted as a value bit.
     *
     * @param value the value to be written out
     * @param outputStream    the stream to write to
     * @return number of bits written
     * @throws IOException as per java IO contract
     */
    public static int writeUnsignedITF8(final int value, final OutputStream outputStream) throws IOException {
        if ((value >>> 7) == 0) {
            outputStream.write(value);
            return 8;
        }

        if ((value >>> 14) == 0) {
            outputStream.write(((value >> 8) | 128));
            outputStream.write((value & 0xFF));
            return 16;
        }

        if ((value >>> 21) == 0) {
            outputStream.write(((value >> 16) | 192));
            outputStream.write(((value >> 8) & 0xFF));
            outputStream.write((value & 0xFF));
            return 24;
        }

        if ((value >>> 28) == 0) {
            outputStream.write(((value >> 24) | 224));
            outputStream.write(((value >> 16) & 0xFF));
            outputStream.write(((value >> 8) & 0xFF));
            outputStream.write((value & 0xFF));
            return 32;
        }

        outputStream.write(((value >> 28) | 240));
        outputStream.write(((value >> 20) & 0xFF));
        outputStream.write(((value >> 12) & 0xFF));
        outputStream.write(((value >> 4) & 0xFF));
        outputStream.write((value & 0xFF));
        return 40;
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
     * Writes an unsigned (32 bit) integer to an {@link OutputStream} encoded as ITF8. The sign bit is interpreted as a value bit.
     *
     * @param value the value to be written out
     * @param buffer   the {@link ByteBuffer} to write to
     */
    public static void writeUnsignedITF8(final int value, final ByteBuffer buffer) {
        if ((value >>> 7) == 0) {
            buffer.put((byte) value);
            return;
        }

        if ((value >>> 14) == 0) {
            buffer.put((byte) ((value >> 8) | 128));
            buffer.put((byte) (value & 0xFF));
            return;
        }

        if ((value >>> 21) == 0) {
            buffer.put((byte) ((value >> 16) | 192));
            buffer.put((byte) ((value >> 8) & 0xFF));
            buffer.put((byte) (value & 0xFF));
            return;
        }

        if ((value >>> 28) == 0) {
            buffer.put((byte) ((value >> 24) | 224));
            buffer.put((byte) ((value >> 16) & 0xFF));
            buffer.put((byte) ((value >> 8) & 0xFF));
            buffer.put((byte) (value & 0xFF));
            return;
        }

        buffer.put((byte) ((value >> 28) | 240));
        buffer.put((byte) ((value >> 20) & 0xFF));
        buffer.put((byte) ((value >> 12) & 0xFF));
        buffer.put((byte) ((value >> 4) & 0xFF));
        buffer.put((byte) (value & 0xFF));
    }
}
