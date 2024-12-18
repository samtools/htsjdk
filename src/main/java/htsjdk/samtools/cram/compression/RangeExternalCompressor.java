package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.compression.range.RangeDecode;
import htsjdk.samtools.cram.compression.range.RangeEncode;
import htsjdk.samtools.cram.compression.range.RangeParams;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

import java.nio.ByteBuffer;

public class RangeExternalCompressor extends ExternalCompressor{

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
        super(BlockCompressionMethod.RANGE);
        this.rangeEncode = rangeEncode;
        this.rangeDecode = rangeDecode;
        this.formatFlags = formatFlags;
    }

    @Override
    public byte[] compress(byte[] data) {
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