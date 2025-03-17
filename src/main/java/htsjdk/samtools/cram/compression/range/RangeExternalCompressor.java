package htsjdk.samtools.cram.compression.range;

import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.structure.CRAMCodecModelContext;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

import java.nio.ByteBuffer;

public class RangeExternalCompressor extends ExternalCompressor {

    private final int formatFlags;
    private final RangeEncode rangeEncode;
    private final RangeDecode rangeDecode;

    public RangeExternalCompressor(
            final RangeEncode rangeEncode,
            final RangeDecode rangeDecode) {
        this(0, rangeEncode, rangeDecode);
    }

    public RangeExternalCompressor(
            final int formatFlags,
            final RangeEncode rangeEncode,
            final RangeDecode rangeDecode) {
        super(BlockCompressionMethod.ADAPTIVE_ARITHMETIC);
        this.rangeEncode = rangeEncode;
        this.rangeDecode = rangeDecode;
        this.formatFlags = formatFlags;
    }

    @Override
    public byte[] compress(byte[] data, final CRAMCodecModelContext unused_contextModel) {
        final RangeParams params = new RangeParams(formatFlags);
        final ByteBuffer buffer = rangeEncode.compress(CompressionUtils.wrap(data), params);
        return toByteArray(buffer);
    }

    @Override
    public byte[] uncompress(byte[] data) {
        final ByteBuffer buf = rangeDecode.uncompress(CompressionUtils.wrap(data));
        return toByteArray(buf);
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", this.getMethod(),formatFlags);
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