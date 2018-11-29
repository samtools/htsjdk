package htsjdk.samtools.cram.encoding;

class ByteArrayLenCodec implements CRAMCodec<byte[]> {
    private final CRAMCodec<Integer> lenCodec;
    private final CRAMCodec<byte[]> byteCodec;

    public ByteArrayLenCodec(final CRAMCodec<Integer> lenCodec,
                      final CRAMCodec<byte[]> byteCodec) {
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
