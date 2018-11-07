package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

import java.io.IOException;

class ByteArrayLenCodec implements BitCodec<byte[]> {
    private final BitCodec<Integer> lenCodec;
    private final BitCodec<byte[]> byteCodec;

    public ByteArrayLenCodec(final BitCodec<Integer> lenCodec,
                             final BitCodec<byte[]> byteCodec) {
        super();
        this.lenCodec = lenCodec;
        this.byteCodec = byteCodec;
    }

    @Override
    public byte[] read(final BitInputStream bitInputStream) throws IOException {
        final int length = lenCodec.read(bitInputStream);
        return byteCodec.read(bitInputStream, length);
    }

    @Override
    public byte[] read(final BitInputStream bitInputStream, final int length) throws IOException {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    public void write(final BitOutputStream bitOutputStream, final byte[] object)
            throws IOException {
        lenCodec.write(bitOutputStream, object.length);
        byteCodec.write(bitOutputStream, object);
    }

}
