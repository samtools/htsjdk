package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Encode byte arrays by specifying a stop byte to separate the arrays.
 * This cannot be a byte that appears in the data.
 */
public class ByteArrayStopCodec extends ExternalCodec<byte[]> {
    private final int stop;

    /**
     * Construct a Byte Array Stop Codec
     *
     * @param inputStream the input bytestream to read from
     * @param outputStream the output bytestream to write to
     * @param stopByte the byte used to mark array boundaries
     */
    public ByteArrayStopCodec(final ByteArrayInputStream inputStream,
                              final ByteArrayOutputStream outputStream,
                              final byte stopByte) {
        super(inputStream, outputStream);
        this.stop = 0xFF & stopByte;
    }

    @Override
    public byte[] read() {
        final ByteArrayOutputStream readingBAOS = new ByteArrayOutputStream();
        int b;
        readingBAOS.reset();
        while ((b = inputStream.read()) != -1 && b != stop) {
            readingBAOS.write(b);
        }

        return readingBAOS.toByteArray();
    }

    @Override
    public byte[] read(final int length) {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    public void write(final byte[] value) {
        try {
            outputStream.write(value);
            outputStream.write(stop);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }
}
