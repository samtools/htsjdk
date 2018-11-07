package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ByteArrayStopCodec implements BitCodec<byte[]> {

    private final int stop;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final ByteArrayOutputStream readingBAOS = new ByteArrayOutputStream();
    private int b;

    public ByteArrayStopCodec(final byte stopByte, final InputStream inputStream, final OutputStream outputStream) {
        this.stop = 0xFF & stopByte;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    @Override
    public byte[] read(final BitInputStream bitInputStream) throws IOException {
        readingBAOS.reset();
        while ((b = inputStream.read()) != -1 && b != stop)
            readingBAOS.write(b);

        return readingBAOS.toByteArray();
    }

    @Override
    public byte[] read(final BitInputStream bitInputStream, final int length) throws IOException {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    public void write(final BitOutputStream bitOutputStream, final byte[] object)
            throws IOException {
        outputStream.write(object);
        outputStream.write(stop);
    }

}
