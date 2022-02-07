package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.compression.rans40.RANS4x8;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

import java.nio.ByteBuffer;
import java.util.Objects;

public class RANS4x8ExternalCompressor extends ExternalCompressor {
    private final RANS4x8.ORDER order;
    private final RANS4x8 rans4x8;

    /**
     * We use a shared RANS4x8 instance for all compressors.
     * @param rans4x8
     */
    public RANS4x8ExternalCompressor(final RANS4x8 rans4x8) {
        this(RANS4x8.ORDER.ZERO, rans4x8);
    }

    public RANS4x8ExternalCompressor(final int order, final RANS4x8 rans4x8) {
        this(RANS4x8.ORDER.fromInt(order), rans4x8);
    }

    public RANS4x8ExternalCompressor(final RANS4x8.ORDER order, final RANS4x8 rans4x8) {
        //TODO: Note this is hijacking the existing 3.0 RANS ID, so this code won't be selected for an actual
        // block since the framework uses the old RANS codec when it sees this ID !
        super(BlockCompressionMethod.RANS);

        this.rans4x8 = rans4x8;
        this.order = order;
    }

    @Override
    public byte[] compress(final byte[] data) {
        final ByteBuffer buffer = rans4x8.compress(ByteBuffer.wrap(data), order);
        return toByteArray(buffer);
    }

    @Override
    public byte[] uncompress(byte[] data) {
        final ByteBuffer buf = rans4x8.uncompress(ByteBuffer.wrap(data));
        return toByteArray(buf);
    }

    public RANS4x8.ORDER getOrder() { return order; }

    @Override
    public String toString() {
        return String.format("%s(%s)", this.getMethod(), order);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RANS4x8ExternalCompressor that = (RANS4x8ExternalCompressor) o;

        return this.order == that.order;
    }

    //TODO: extract this duplicated method
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
