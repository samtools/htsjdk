package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import java.util.Objects;

public class RANSExternalCompressor extends ExternalCompressor {
    private final RANS.ORDER order;

    public RANSExternalCompressor(final RANS.ORDER order) {
        super(BlockCompressionMethod.RANS);
        this.order = order;
    }

    public RANSExternalCompressor(final int order) {
        this(RANS.ORDER.fromInt(order));
    }

    @Override
    public byte[] compress(final byte[] data) {
        return ExternalCompression.rans(data, order);
    }

    @Override
    public byte[] uncompress(byte[] data) {
        //TODO: implement this
        return new byte[0];
    }

    public RANS.ORDER getOrder() { return order; }

    @Override
    public String toString() {
        return String.format("%s (%s)", this.getMethod(), order);
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

}
