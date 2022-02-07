package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class TokenizedNameCodec extends ExternalCodec<byte[]> {

    public TokenizedNameCodec(final ByteArrayInputStream inputStream,
                              final ByteArrayOutputStream outputStream
                              // plus other args
    ) {
        super(inputStream, outputStream);
    }

    @Override
    public byte[] read() {
        final ByteArrayOutputStream readingBAOS = new ByteArrayOutputStream();
        int b;
        readingBAOS.reset();
//        while ((b = inputStream.read()) != -1 && b != stop) {
//            readingBAOS.write(b);
//        }

        return readingBAOS.toByteArray();
    }

    @Override
    public byte[] read(final int length) {
        //TODO: is this right ?
        throw new RuntimeException("Not implemented.");
    }

    @Override
    public void write(byte[] value) {
        try {
            outputStream.write(value);
            //outputStream.write(stop);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

}
