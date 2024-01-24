package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.compression.range.RangeDecode;
import htsjdk.samtools.cram.compression.range.RangeEncode;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Decode;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Encode;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.utils.ValidationUtils;

public abstract class ExternalCompressor {
    final public static int NO_COMPRESSION_ARG = -1;
    final private static String argErrorMessage = "Invalid compression arg (%d) requested for CRAM %s compressor";

    private BlockCompressionMethod method;

    protected ExternalCompressor(final BlockCompressionMethod method) {
        this.method = method;
    }

    public abstract byte[] compress(byte[] data);

    public abstract byte[] uncompress(byte[] data);

    public BlockCompressionMethod getMethod() { return method; }

    @Override
    public String toString() {
        return this.getMethod().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExternalCompressor that = (ExternalCompressor) o;

        return getMethod() == that.getMethod();
    }

    @Override
    public int hashCode() {
        return getMethod().hashCode();
    }

    /**
     * Return an ExternalCompressor subclass based on the BlockCompressionMethod. Compressor-specific arguments
     * must be populated by the caller.
     * @param compressionMethod the type of compressor required ({@link BlockCompressionMethod})
     * @param compressorSpecificArg the required order for RANS compressors; or the desired write compression
     *                             level for GZIP
     * @return an ExternalCompressor of the requested type, populated with an compressor-specific args
     */
    public static ExternalCompressor getCompressorForMethod(
            final BlockCompressionMethod compressionMethod,
            final int compressorSpecificArg) {
        switch (compressionMethod) {
            case RAW:
                ValidationUtils.validateArg(
                        compressorSpecificArg == NO_COMPRESSION_ARG,
                        String.format(argErrorMessage, compressorSpecificArg, compressionMethod));
                return new RAWExternalCompressor();

            case GZIP:
                return compressorSpecificArg == NO_COMPRESSION_ARG ?
                        new GZIPExternalCompressor() :
                        new GZIPExternalCompressor(compressorSpecificArg);

            case LZMA:
                ValidationUtils.validateArg(
                        compressorSpecificArg == NO_COMPRESSION_ARG,
                        String.format(argErrorMessage, compressorSpecificArg, compressionMethod));
                return new LZMAExternalCompressor();

            case RANS:
                return compressorSpecificArg == NO_COMPRESSION_ARG ?
                        new RANSExternalCompressor(new RANS4x8Encode(), new RANS4x8Decode()) :
                        new RANSExternalCompressor(compressorSpecificArg, new RANS4x8Encode(), new RANS4x8Decode());

            case RANGE:
                return compressorSpecificArg == NO_COMPRESSION_ARG ?
                        new RangeExternalCompressor(new RangeEncode(), new RangeDecode()) :
                        new RangeExternalCompressor(compressorSpecificArg, new RangeEncode(), new RangeDecode());

            case BZIP2:
                ValidationUtils.validateArg(
                        compressorSpecificArg == NO_COMPRESSION_ARG,
                        String.format(argErrorMessage, compressorSpecificArg, compressionMethod));
                return new BZIP2ExternalCompressor();

            default:
                throw new IllegalArgumentException(String.format("Unknown compression method %s", compressionMethod));
        }
    }

}