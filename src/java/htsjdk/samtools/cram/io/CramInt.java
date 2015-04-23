package htsjdk.samtools.cram.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Methods to read and write CRAM int values as given in the file format specification.
 */

public class CramInt {
    /**
     * Read unsigned little-endian 4 byte integer from an {@link InputStream}.
     *
     * @param is input stream to read from
     * @return an integer value read
     * @throws IOException as per java IO contract
     */
    public static int int32(final InputStream is) throws IOException {
        return is.read() | is.read() << 8 | is.read() << 16 | is.read() << 24;
    }

    /**
     * Read unsigned little-endian 4 byte integer from an array of bytes.
     *
     * @param data input stream to read from
     * @return an integer value read
     */
    public static int int32(final byte[] data) {
        if (data.length != 4)
            throw new IllegalArgumentException("Expecting a 4-byte integer. ");
        return (0xFF & data[0]) | ((0xFF & data[1]) << 8) | ((0xFF & data[2]) << 16) | ((0xFF & data[3]) << 24);
    }

    /**
     * Read unsigned little-endian 4 byte integer from an {@link ByteBuffer}.
     *
     * @param buf {@link ByteBuffer} to read from
     * @return an integer value read from the buffer
     */
    public static int int32(final ByteBuffer buf) {
        return buf.get() | buf.get() << 8 | buf.get() << 16 | buf.get() << 24;
    }

    /**
     * Write int value to {@link OutputStream} encoded as CRAM int data type.
     * @param value value to be written out
     * @param os the output stream
     * @return the number of bits written out
     * @throws IOException as per java IO contract
     */
    @SuppressWarnings("SameReturnValue")
    public static int writeInt32(final int value, final OutputStream os) throws IOException {
        os.write((byte) value);
        os.write((byte) (value >> 8));
        os.write((byte) (value >> 16));
        os.write((byte) (value >> 24));
        return 4*8;
    }

    /**
     * Write int value to {@link OutputStream} encoded as CRAM int data type.
     * @param value value to be written out
     * @return the byte array holding the value encoded as CRAM int data type
     */
    public static byte[] writeInt32(final int value) {
        final byte[] data = new byte[4] ;
        data[0] = (byte) (value & 0xFF);
        data[1] = (byte) (value >> 8 & 0xFF);
        data[2] = (byte) (value >> 16 & 0xFF);
        data[3] = (byte) (value >> 24 & 0xFF);
        return data;
    }

}
