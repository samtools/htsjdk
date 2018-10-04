package htsjdk.samtools.cram.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Methods to read and write CRAM long values as given in the file format specification.
 */

public class CramLong {
    /**
     * Read unsigned little-endian 8 byte integer from an {@link InputStream}.
     *
     * @param inputStream input stream to read from
     * @return a long value read
     * @throws IOException as per java IO contract
     */
    public static long int64(final InputStream inputStream) throws IOException {
        return (long) inputStream.read() |
                (long) inputStream.read() << 8 |
                (long) inputStream.read() << 16 |
                (long) inputStream.read() << 24 |
                (long) inputStream.read() << 32 |
                (long) inputStream.read() << 40 |
                (long) inputStream.read() << 48 |
                (long) inputStream.read() << 56;
    }

    /**
     * Read unsigned little-endian 8 byte integer from an array of bytes.
     *
     * @param data input stream to read from
     * @return a long value read
     */
    public static long int64(final byte[] data) {
        if (data.length != 8)
            throw new IllegalArgumentException("Expecting an 8-byte integer. ");
        return (long) (0xFF & data[0]) |
                ((long) (0xFF & data[1]) << 8) |
                ((long) (0xFF & data[2]) << 16) |
                ((long) (0xFF & data[3]) << 24) |
                ((long) (0xFF & data[4]) << 32) |
                ((long) (0xFF & data[5]) << 40) |
                ((long) (0xFF & data[6]) << 48) |
                ((long) (0xFF & data[7]) << 56);
    }

    /**
     * Read unsigned little-endian 8 byte integer from an {@link ByteBuffer}.
     *
     * @param buffer {@link ByteBuffer} to read from
     * @return a long value read from the buffer
     */
    public static long int64(final ByteBuffer buffer) {
        return (0xFF & (long) buffer.get()) |
                (0xFF & (long) buffer.get()) << 8 |
                (0xFF & (long) buffer.get()) << 16 |
                (0xFF & (long) buffer.get()) << 24 |
                (0xFF & (long) buffer.get()) << 32 |
                (0xFF & (long) buffer.get()) << 40 |
                (0xFF & (long) buffer.get()) << 48 |
                (0xFF & (long) buffer.get()) << 56;
    }

    /**
     * Write int value to {@link OutputStream} encoded as CRAM int64 data type.
     *
     * @param value value to be written out
     * @param outputStream    the output stream
     * @return the number of bits written out
     * @throws IOException as per java IO contract
     */
    @SuppressWarnings("SameReturnValue")
    public static long writeInt64(final long value, final OutputStream outputStream) throws IOException {
        outputStream.write((byte) value);
        outputStream.write((byte) (value >> 8));
        outputStream.write((byte) (value >> 16));
        outputStream.write((byte) (value >> 24));
        outputStream.write((byte) (value >> 32));
        outputStream.write((byte) (value >> 40));
        outputStream.write((byte) (value >> 48));
        outputStream.write((byte) (value >> 56));
        return 8 * 8;
    }

    /**
     * Write int value to an array of bytes encoded as CRAM int64 data type.
     *
     * @param value value to be written out
     * @return the byte array holding the value encoded as CRAM int64 data type
     */
    public static byte[] writeInt64(final long value) {
        final byte[] data = new byte[8];
        data[0] = (byte) (value & 0xFF);
        data[1] = (byte) (value >> 8 & 0xFF);
        data[2] = (byte) (value >> 16 & 0xFF);
        data[3] = (byte) (value >> 24 & 0xFF);
        data[4] = (byte) (value >> 32 & 0xFF);
        data[5] = (byte) (value >> 40 & 0xFF);
        data[6] = (byte) (value >> 48 & 0xFF);
        data[7] = (byte) (value >> 56 & 0xFF);
        return data;
    }

}
