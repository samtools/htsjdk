package htsjdk.samtools.cram.encoding;

class ByteArrayLenCodec implements CramCodec<byte[]> {
    private final CramCodec<Integer> lenCodec;
    private final CramCodec<byte[]> byteCodec;

    public ByteArrayLenCodec(final CramCodec<Integer> lenCodec,
                             final CramCodec<byte[]> byteCodec) {
        super();
        this.lenCodec = lenCodec;
        this.byteCodec = byteCodec;
    }

    @Override
    public byte[] read() {
        final int length = lenCodec.read();
        return byteCodec.read(length);
    }

    @Override
    public byte[] read(final int length) {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    public void write(final byte[] object) {
        lenCodec.write(object.length);
        byteCodec.write(object);
    }

}
