package htsjdk.samtools.cram.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Methods to read and write LTF8 as per CRAM specs.
 */
public class LTF8 {
    /**
     * Reads an unsigned long value from the input stream. The sign bit should be interpreted just as other bits in the value.
     * @param is input stream to be read from
     * @return value encoded in the stream as LTF8
     * @throws IOException as per java IO contract
     */
    public static long readUnsignedLTF8(final InputStream is) throws IOException {
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

        if ((b1 & 16) == 0) {
            long result = ((long) (b1 & 31) << 24);
            result |= is.read() << 16;
            result |= is.read() << 8;
            result |= is.read();
            return result;
        }

        if ((b1 & 8) == 0) {
            long value = ((long) (b1 & 15) << 32);
            value |= ((0xFF & ((long) is.read())) << 24);
            value |= (is.read() << 16);
            value |= (is.read() << 8);
            value |= is.read();
            return value;
        }

        if ((b1 & 4) == 0) {
            long result = ((long) (b1 & 7) << 40);
            result |= (0xFF & ((long) is.read())) << 32;
            result |= (0xFF & ((long) is.read())) << 24;
            result |= is.read() << 16;
            result |= is.read() << 8;
            result |= is.read();
            return result;
        }

        if ((b1 & 2) == 0) {
            long result = ((long) (b1 & 3) << 48);
            result |= (0xFF & ((long) is.read())) << 40;
            result |= (0xFF & ((long) is.read())) << 32;
            result |= (0xFF & ((long) is.read())) << 24;
            result |= is.read() << 16;
            result |= is.read() << 8;
            result |= is.read();
            return result;
        }

        if ((b1 & 1) == 0) {
            long result = (0xFF & ((long) is.read())) << 48;
            result |= (0xFF & ((long) is.read())) << 40;
            result |= (0xFF & ((long) is.read())) << 32;
            result |= (0xFF & ((long) is.read())) << 24;
            result |= is.read() << 16;
            result |= is.read() << 8;
            result |= is.read();
            return result;
        }

        long result = (0xFF & ((long) is.read())) << 56;
        result |= (0xFF & ((long) is.read())) << 48;
        result |= (0xFF & ((long) is.read())) << 40;
        result |= (0xFF & ((long) is.read())) << 32;
        result |= (0xFF & ((long) is.read())) << 24;
        result |= is.read() << 16;
        result |= is.read() << 8;
        result |= is.read();
        return result;
    }

    /**
     * Writes an unsigned long value to the output stream. The sign bit is interpreted just as other bits in the value.
     * @param value the value to be written
     * @param os the output stream to write to
     * @return the number of bits written
     * @throws IOException as per java IO contract
     */
    public static int writeUnsignedLTF8(final long value, final OutputStream os) throws IOException {
        if ((value >>> 7) == 0) {
            // no control bits
            os.write((int) value);
            return 8;
        }

        if ((value >>> 14) == 0) {
            // one control bit
            os.write((int) ((value >> 8) | 0x80));
            os.write((int) (value & 0xFF));
            return 16;
        }

        if ((value >>> 21) == 0) {
            // two control bits
            os.write((int) ((value >> 16) | 0xC0));
            os.write((int) ((value >> 8) & 0xFF));
            os.write((int) (value & 0xFF));
            return 24;
        }

        if ((value >>> 28) == 0) {
            // three control bits
            os.write((int) ((value >> 24) | 0xE0));
            os.write((int) ((value >> 16) & 0xFF));
            os.write((int) ((value >> 8) & 0xFF));
            os.write((int) (value & 0xFF));
            return 32;
        }

        if ((value >>> 35) == 0) {
            // four control bits
            os.write((int) ((value >> 32) | 0xF0));
            os.write((int) ((value >> 24) & 0xFF));
            os.write((int) ((value >> 16) & 0xFF));
            os.write((int) ((value >> 8) & 0xFF));
            os.write((int) (value & 0xFF));
            return 40;
        }

        if ((value >>> 42) == 0) {
            // five control bits
            os.write((int) ((value >> 40) | 0xF8));
            os.write((int) ((value >> 32) & 0xFF));
            os.write((int) ((value >> 24) & 0xFF));
            os.write((int) ((value >> 16) & 0xFF));
            os.write((int) ((value >> 8) & 0xFF));
            os.write((int) (value & 0xFF));
            return 48;
        }

        if ((value >>> 49) == 0) {
            // six control bits
            os.write((int) ((value >> 48) | 0xFC));
            os.write((int) ((value >> 40) & 0xFF));
            os.write((int) ((value >> 32) & 0xFF));
            os.write((int) ((value >> 24) & 0xFF));
            os.write((int) ((value >> 16) & 0xFF));
            os.write((int) ((value >> 8) & 0xFF));
            os.write((int) (value & 0xFF));
            return 56;
        }

        if ((value >>> 56) == 0) {
            // seven control bits
            os.write(0xFE);
            os.write((int) ((value >> 48) & 0xFF));
            os.write((int) ((value >> 40) & 0xFF));
            os.write((int) ((value >> 32) & 0xFF));
            os.write((int) ((value >> 24) & 0xFF));
            os.write((int) ((value >> 16) & 0xFF));
            os.write((int) ((value >> 8) & 0xFF));
            os.write((int) (value & 0xFF));
            return 64;
        }

        // eight control bits
        os.write((0xFF));
        os.write((int) ((value >> 56) & 0xFF));
        os.write((int) ((value >> 48) & 0xFF));
        os.write((int) ((value >> 40) & 0xFF));
        os.write((int) ((value >> 32) & 0xFF));
        os.write((int) ((value >> 28) & 0xFF));
        os.write((int) ((value >> 16) & 0xFF));
        os.write((int) ((value >> 8) & 0xFF));
        os.write((int) (value & 0xFF));
        return 72;
    }
}
