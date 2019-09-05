package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

import java.nio.ByteBuffer;
import java.util.Objects;

public class RANSExternalCompressor extends ExternalCompressor {
    private final RANS.ORDER order;
    private final RANS rans;

    /**
     * We use a shared RANS instance for all compressors.
     * @param rans
     */
    public RANSExternalCompressor(final RANS rans) {
        this(RANS.ORDER.ZERO, rans);
    }

    public RANSExternalCompressor(final int order, final RANS rans) {
        this(RANS.ORDER.fromInt(order), rans);
    }

    public RANSExternalCompressor(final RANS.ORDER order, final RANS rans) {
        super(BlockCompressionMethod.RANS);
        this.rans = rans;
        this.order = order;
    }

    @Override
    public byte[] compress(final byte[] data) {
        final ByteBuffer buffer = rans.compress(ByteBuffer.wrap(data), order);
        return toByteArray(buffer);
    }

    @Override
    public byte[] uncompress(byte[] data) {
        final ByteBuffer buf = rans.uncompress(ByteBuffer.wrap(data));
        return toByteArray(buf);
    }

    public RANS.ORDER getOrder() { return order; }

    @Override
    public String toString() {
        return String.format("%s(%s)", this.getMethod(), order);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RANSExternalCompressor that = (RANSExternalCompressor) o;

        return this.order == that.order;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMethod(), order);
    }

    private byte[] toByteArray(final ByteBuffer buffer) {
        if (buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.array().length == buffer.limit()) {
            return buffer.array();
        }

        final byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

}
