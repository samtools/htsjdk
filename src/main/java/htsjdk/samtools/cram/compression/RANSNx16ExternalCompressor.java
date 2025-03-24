package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Decode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Encode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Params;
import htsjdk.samtools.cram.structure.CRAMCodecModelContext;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

import java.util.Objects;

public final class RANSNx16ExternalCompressor extends ExternalCompressor {
    private final int flags;
    private final RANSNx16Encode ransEncode;
    private final RANSNx16Decode ransDecode;

    /**
     * We use a shared RANS instance for all compressors.
     *
     * @param ransEncode
     * @param ransDecode
     */
    public RANSNx16ExternalCompressor(
            final RANSNx16Encode ransEncode,
            final RANSNx16Decode ransDecode) {
        this(0, ransEncode, ransDecode); // order 0
    }

    public RANSNx16ExternalCompressor(
            final int flags,
            final RANSNx16Encode ransEncode,
            final RANSNx16Decode ransDecode) {
        super(BlockCompressionMethod.RANSNx16);
        this.ransEncode = ransEncode;
        this.ransDecode = ransDecode;
        this.flags = flags;
    }

    @Override
    public byte[] compress(final byte[] data, final CRAMCodecModelContext unused_contextModel) {
        final RANSNx16Params params = new RANSNx16Params(flags);
        return CompressionUtils.toByteArray(ransEncode.compress(CompressionUtils.wrap(data), params));
    }

    @Override
    public byte[] uncompress(byte[] data) {
        return CompressionUtils.toByteArray(ransDecode.uncompress(CompressionUtils.wrap(data)));
    }

    @Override
    public String toString() {
        return String.format("%s(%x)", this.getMethod(), flags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RANSNx16ExternalCompressor that = (RANSNx16ExternalCompressor) o;

        return this.flags == that.flags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMethod(), flags);
    }

}