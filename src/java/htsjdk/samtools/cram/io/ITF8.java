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
     * @param is the stream to read from
     * @return
     * @throws IOException as per java IO contract
     */
    public static int readUnsignedITF8(final InputStream is) throws IOException {
        final int b1 = is.read();
        if (b1 == -1)
            throw new EOFException();

        if ((b1 & 128) == 0)
            return b1;

        if ((b1 & 64) == 0)
            return ((b1 & 127) << 8) | is.read();

        if ((b1 & 32) == 0) {
            final int b2 = is.read();
            final int b3 = is.read();
            return ((b1 & 63) << 16) | b2 << 8 | b3;
        }

        if ((b1 & 16) == 0)
            return ((b1 & 31) << 24) | is.read() << 16 | is.read() << 8 | is.read();

        return ((b1 & 15) << 28) | is.read() << 20 | is.read() << 12 | is.read() << 4 | (15 & is.read());
    }

    /**
     * Writes an unsigned (32 bit) integer to an {@link OutputStream} encoded as ITF8. The sign bit is interpreted as a value bit.
     * @param value the value to be written out
     * @param os the stream to write to
     * @return number of bits written
     * @throws IOException as per java IO contract
     */
    public static int writeUnsignedITF8(final int value, final OutputStream os) throws IOException {
        if ((value >>> 7) == 0) {
            os.write(value);
            return 8;
        }

        if ((value >>> 14) == 0) {
            os.write(((value >> 8) | 128));
            os.write((value & 0xFF));
            return 16;
        }

        if ((value >>> 21) == 0) {
            os.write(((value >> 16) | 192));
            os.write(((value >> 8) & 0xFF));
            os.write((value & 0xFF));
            return 24;
        }

        if ((value >>> 28) == 0) {
            os.write(((value >> 24) | 224));
            os.write(((value >> 16) & 0xFF));
            os.write(((value >> 8) & 0xFF));
            os.write((value & 0xFF));
            return 32;
        }

        os.write(((value >> 28) | 240));
        os.write(((value >> 20) & 0xFF));
        os.write(((value >> 12) & 0xFF));
        os.write(((value >> 4) & 0xFF));
        os.write((value & 0xFF));
        return 40;
    }

    /**
     * Reads an unsigned (32 bit) integer from an array of bytes. The sign bit should be interpreted as a value bit.
     * @param data the bytes to read from
     * @return
     */
    public static int readUnsignedITF8(final byte[] data) {
        final ByteBuffer buf = ByteBuffer.wrap(data);
        final int value = readUnsignedITF8(buf);
        buf.clear();

        return value;
    }

    /**
     * Writes an unsigned (32 bit) integer to a byte new array encoded as ITF8. The sign bit is interpreted as a value bit.
     * @param value the value to be written out
     * @return the bytes holding ITF8 representation of the value
     */
    public static byte[] writeUnsignedITF8(final int value) {
        final ByteBuffer buf = ByteBuffer.allocate(10);
        writeUnsignedITF8(value, buf);

        buf.flip();
        final byte[] array = new byte[buf.limit()];
        buf.get(array);

        buf.clear();
        return array;
    }

    /**
     * Reads an unsigned (32 bit) integer from a {@link ByteBuffer}. The sign bit should be interpreted as a value bit.
     * @param buf the bytes to read from
     * @return unsigned integer value from the buffer
     */
    public static int readUnsignedITF8(final ByteBuffer buf) {
        final int b1 = 0xFF & buf.get();

        if ((b1 & 128) == 0)
            return b1;

        if ((b1 & 64) == 0)
            return ((b1 & 127) << 8) | (0xFF & buf.get());

        if ((b1 & 32) == 0) {
            final int b2 = 0xFF & buf.get();
            final int b3 = 0xFF & buf.get();
            return ((b1 & 63) << 16) | b2 << 8 | b3;
        }

        if ((b1 & 16) == 0)
            return ((b1 & 31) << 24) | (0xFF & buf.get()) << 16 | (0xFF & buf.get()) << 8 | (0xFF & buf.get());

        return ((b1 & 15) << 28) | (0xFF & buf.get()) << 20 | (0xFF & buf.get()) << 12 | (0xFF & buf.get()) << 4
                | (15 & buf.get());
    }

    /**
     * Writes an unsigned (32 bit) integer to an {@link OutputStream} encoded as ITF8. The sign bit is interpreted as a value bit.
     * @param value the value to be written out
     * @param buf the {@link ByteBuffer} to write to
     * @return number of bits written
     */
    public static void writeUnsignedITF8(final int value, final ByteBuffer buf) {
        if ((value >>> 7) == 0) {
            buf.put((byte) value);
            return;
        }

        if ((value >>> 14) == 0) {
            buf.put((byte) ((value >> 8) | 128));
            buf.put((byte) (value & 0xFF));
            return;
        }

        if ((value >>> 21) == 0) {
            buf.put((byte) ((value >> 16) | 192));
            buf.put((byte) ((value >> 8) & 0xFF));
            buf.put((byte) (value & 0xFF));
            return;
        }

        if ((value >>> 28) == 0) {
            buf.put((byte) ((value >> 24) | 224));
            buf.put((byte) ((value >> 16) & 0xFF));
            buf.put((byte) ((value >> 8) & 0xFF));
            buf.put((byte) (value & 0xFF));
            return;
        }

        buf.put((byte) ((value >> 28) | 240));
        buf.put((byte) ((value >> 20) & 0xFF));
        buf.put((byte) ((value >> 12) & 0xFF));
        buf.put((byte) ((value >> 4) & 0xFF));
        buf.put((byte) (value & 0xFF));
    }
}
