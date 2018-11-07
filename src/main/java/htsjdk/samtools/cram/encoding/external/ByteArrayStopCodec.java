package htsjdk.samtools.cram.encoding.external;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ByteArrayStopCodec extends ExternalCodec<byte[]> {
    private final int stop;

    public ByteArrayStopCodec(final InputStream inputStream, final OutputStream outputStream, final byte stopByte) {
        super(inputStream, outputStream);
        this.stop = 0xFF & stopByte;
    }

    @Override
    public byte[] read() {
        final ByteArrayOutputStream readingBAOS = new ByteArrayOutputStream();
        int b;
        readingBAOS.reset();
        try {
            while ((b = inputStream.read()) != -1 && b != stop)
                readingBAOS.write(b);

            return readingBAOS.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
            throw new RuntimeException(e);
        }
    }
}
