/*
* Adapted from example code in
* Title: Hardcore Java
* Title: Java I/O
* Second Edition: May 2006
* ISBN 10: 0-596-52750-0
* ISBN 13: 9780596527501
*
* http://www.javafaq.nu/java-example-code-1078.html
*
*/
package htsjdk.tribble.util;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


/**
 * Input stream with methods to convert byte arrays to numeric values using "little endian" order.
 * <p/>
 * Note: This class is not thread safe => instances should not be shared amongst threads
 */
public class LittleEndianInputStream extends FilterInputStream {

    byte[] buffer;

    public LittleEndianInputStream(InputStream in) {
        super(in);
        buffer = new byte[8];

    }

    /**
     *
     * @return the next byte of this input stream, interpreted as an unsigned 8-bit number
     * @throws IOException
     */
    public byte readByte() throws IOException {
        int ch = in.read();
        if (ch < 0) {
            throw new EOFException();
        }
        return (byte) (ch);
    }

    public short readShort() throws IOException {
        int byte1 = in.read();
        int byte2 = in.read();
        if (byte2 < 0) {
            throw new EOFException();
        }

        return (short) (((byte2 << 24) >>> 16) + ((byte1 << 24) >>> 24));
    }


    public int readInt() throws IOException {
        int byte1 = in.read();
        int byte2 = in.read();
        int byte3 = in.read();
        int byte4 = in.read();
        if (byte4 < 0) {
            throw new EOFException();
        }
        return (byte4 << 24)
                + ((byte3 << 24) >>> 8)
                + ((byte2 << 24) >>> 16)
                + ((byte1 << 24) >>> 24);

    }

    /**
     *
     */

    public long readLong() throws IOException {
        readFully(buffer);
        long byte1 = (long) buffer[0];
        long byte2 = (long) buffer[1];
        long byte3 = (long) buffer[2];
        long byte4 = (long) buffer[3];
        long byte5 = (long) buffer[4];
        long byte6 = (long) buffer[5];
        long byte7 = (long) buffer[6];
        long byte8 = (long) buffer[7];
        return (byte8 << 56)
                + ((byte7 << 56) >>> 8)
                + ((byte6 << 56) >>> 16)
                + ((byte5 << 56) >>> 24)
                + ((byte4 << 56) >>> 32)
                + ((byte3 << 56) >>> 40)
                + ((byte2 << 56) >>> 48)
                + ((byte1 << 56) >>> 56);
    }

    public final double readDouble() throws IOException {
        return Double.longBitsToDouble(this.readLong());
    }

    public final float readFloat() throws IOException {
        return Float.intBitsToFloat(this.readInt());
    }

    /**
     * Read a null terminated byte array and return result as a String
     * This method decodes theh bytes as UTF-8 string
     * @throws IOException if reading from the stream fails for some reason
     * @throws EOFException if the stream ends without encountering a null terminator.
     * @deprecated Prefer the {@link #readString(Charset)} which allows specifying a charset explicitly
     */
    @Deprecated
    public String readString() throws IOException {
        return readString(StandardCharsets.UTF_8);
    }

    /**
     * Read a null terminated byte array and return result as a String
     * @param charset the Charset to use when decoding the bytes to a String
     * @throws IOException if reading from the stream fails for some reason
     * @throws EOFException if the stream ends without encountering a null terminator.
     */
    public String readString(final Charset charset) throws IOException {
        final ByteArrayOutputStream bis = new ByteArrayOutputStream(100);
        int b;
        while ((b = in.read()) != 0) {
            if(b < 0) {
                throw new EOFException();
            }
            bis.write((byte)b);
        }
        return bis.toString(charset.name());
    }


    /**
     * Keep reading until the input buffer is filled.
     */
    private void readFully(byte b[]) throws IOException {
        int len = b.length;
        if (len < 0)
            throw new IndexOutOfBoundsException();
        int n = 0;
        while (n < len) {
            int count = read(b, n, len - n);
            if (count < 0)
                throw new EOFException();
            n += count;
        }
    }


}
