package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.cram.compression.rans.RANS.ORDER;

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

    // TODO: this is only used to create a compressor from a serialized compression map (and for tests)
    public static ExternalCompressor getCompressorForMethod(
            final BlockCompressionMethod compressionMethod,
            //TODO: fix this arg list to be compressor-specific (ie. add gzip level)
            // or make it just use defaults ?
            final RANS.ORDER order) {
        switch (compressionMethod) {
            case RAW:
                return new RAWExternalCompressor();
            case GZIP:
                return new GZIPExternalCompressor(new CRAMEncodingStrategy().getGZIPCompressionLevel());
            case LZMA:
                return new LZMAExternalCompressor();
            case RANS:
                if (order == RANS.ORDER.ZERO) {
                    return new RANSExternalCompressor(ORDER.ZERO);
                } else {
                    return new RANSExternalCompressor(ORDER.ONE);
                }
            case BZIP2:
                return new BZIP2ExternalCompressor();
            default:
                throw new IllegalArgumentException(String.format("Unknown compression method %s", compressionMethod));
        }
    }

}

