package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

public abstract class ExternalCompressor {
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
                return new RAWExternalCompressor();
            case GZIP:
                return new GZIPExternalCompressor(compressorSpecificArg);
            case LZMA:
                return new LZMAExternalCompressor();
            case RANS:
                return new RANSExternalCompressor(compressorSpecificArg);
            case BZIP2:
                return new BZIP2ExternalCompressor();
            default:
                throw new IllegalArgumentException(String.format("Unknown compression method %s", compressionMethod));
        }
    }

}

