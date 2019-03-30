package htsjdk.samtools.cram.io;

import htsjdk.samtools.util.RuntimeIOException;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;

/**
 * An output stream that calculates CRC32 checksum of all the bytes written through the stream. The java {@link java.util.zip.CRC32}
 * class is used to internally.
 */
public class CRC32OutputStream extends FilterOutputStream {

    private final CRC32 crc32 = new CRC32();

    public CRC32OutputStream(final OutputStream out) {
        super(out);
    }

    @Override
    public void write(@SuppressWarnings("NullableProblems") final byte[] b, final int off, final int length) {
        crc32.update(b, off, length);
        try {
            out.write(b, off, length);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public void write(final int b) {
        crc32.update(b);
        try {
            out.write(b);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public void write(@SuppressWarnings("NullableProblems") final byte[] b) {
        crc32.update(b);
        try {
            out.write(b);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    public long getLongCrc32() {
        return crc32.getValue();
    }

    public byte[] getCrc32_BigEndian() {
        final long value = crc32.getValue();
        return new byte[]{(byte) (0xFF & (value >> 24)),
                (byte) (0xFF & (value >> 16)), (byte) (0xFF & (value >> 8)),
                (byte) (0xFF & value)};
    }

    public byte[] getCrc32_LittleEndian() {
        final long value = crc32.getValue();
        return new byte[]{(byte) (0xFF & (value)),
                (byte) (0xFF & (value >> 8)), (byte) (0xFF & (value >> 16)),
                (byte) (0xFF & (value >> 24))};
    }
}
