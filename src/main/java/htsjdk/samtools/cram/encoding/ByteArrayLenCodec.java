package htsjdk.samtools.cram.encoding;

/**
 * Encode byte arrays by specifying encodings for array lengths and contents
 */
class ByteArrayLenCodec implements CRAMCodec<byte[]> {
    private final CRAMCodec<Integer> lenCodec;
    private final CRAMCodec<byte[]> byteCodec;

    /**
     * Construct a Byte Array Len Codec by supplying a codec for length and a codec for values
     *
     * @param lenCodec the length codec, for Integers
     * @param byteCodec the value codec, for byte[]
     */
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
