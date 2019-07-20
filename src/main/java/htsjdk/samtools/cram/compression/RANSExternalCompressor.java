package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

import java.nio.ByteBuffer;
import java.util.Objects;

public class RANSExternalCompressor extends ExternalCompressor {
    private final RANS.ORDER order;
    private final RANS rans = new RANS();

    public RANSExternalCompressor() {
        this(RANS.ORDER.ZERO);
    }

    public RANSExternalCompressor(final RANS.ORDER order) {
        super(BlockCompressionMethod.RANS);
        this.order = order;
    }

    public RANSExternalCompressor(final int order) {
        this(RANS.ORDER.fromInt(order));
    }

    @Override
    public byte[] compress(final byte[] data) {
        final ByteBuffer buffer = rans.compress(ByteBuffer.wrap(data), order, null);
        return toByteArray(buffer);
    }

    @Override
    public byte[] uncompress(byte[] data) {
        final ByteBuffer buf = rans.uncompress(ByteBuffer.wrap(data), null);
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
