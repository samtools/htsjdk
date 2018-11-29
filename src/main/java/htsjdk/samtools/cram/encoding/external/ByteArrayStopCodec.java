package htsjdk.samtools.cram.encoding.external;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import htsjdk.samtools.util.RuntimeIOException;

public class ByteArrayStopCodec extends ExternalCodec<byte[]> {
    private final int stop;

    public ByteArrayStopCodec(final ByteArrayInputStream inputStream, final ByteArrayOutputStream outputStream, final byte stopByte) {
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
