package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.cram.compression.rans.RANS.ORDER;

public abstract class ExternalCompressor {
    private BlockCompressionMethod method;

    protected ExternalCompressor(final BlockCompressionMethod method) {
        this.method = method;
    }

    public BlockCompressionMethod getMethod() {
        return method;
    }

    public abstract byte[] compress(byte[] data);

    public static ExternalCompressor createRAW() {
        return new RawExternalCompressor();
    }

    public static ExternalCompressor createGZIP() {
        return new GZIPExternalCompressor();
    }

    public static ExternalCompressor createLZMA() {
        return new LZMAExternalCompressor();
    }

    public static ExternalCompressor createBZIP2() {
        return new BZIP2ExternalCompressor();

    }

    public static ExternalCompressor createRANS(final ORDER order) {
        if (order == RANS.ORDER.ONE) {
            return new RANSOrder1ExternalCompressor();
        } else {
            return new RANSOrder1ExternalCompressor();
        }
    }

    @Override
    public String toString() {
        return this.getClass().toString();
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

    public static class RawExternalCompressor extends ExternalCompressor {

        public RawExternalCompressor() {
            super(BlockCompressionMethod.RAW);
        }

        @Override
        public byte[] compress(final byte[] data) {
            return data;
        }
    }

    public static class GZIPExternalCompressor extends ExternalCompressor {

        public GZIPExternalCompressor() {
            super(BlockCompressionMethod.GZIP);
        }

        @Override
        public byte[] compress(final byte[] data) {
            return ExternalCompression.gzip(data);
        }
    }

    public static class LZMAExternalCompressor extends ExternalCompressor {

        public LZMAExternalCompressor() {
            super(BlockCompressionMethod.LZMA);
        }

        @Override
        public byte[] compress(final byte[] data) {
            return ExternalCompression.xz(data);
        }
    }

    public static class BZIP2ExternalCompressor extends ExternalCompressor {

        public BZIP2ExternalCompressor() {
            super(BlockCompressionMethod.BZIP2);
        }

        @Override
        public byte[] compress(final byte[] data) {
            return ExternalCompression.bzip2(data);
        }
    }

    public static class RANSOrder0ExternalCompressor extends ExternalCompressor {

        public RANSOrder0ExternalCompressor() {
            super(BlockCompressionMethod.RANS);
        }

        @Override
        public byte[] compress(final byte[] data) {
            return ExternalCompression.rans(data, ORDER.ZERO);
        }
    }

    public static class RANSOrder1ExternalCompressor extends ExternalCompressor {

        public RANSOrder1ExternalCompressor() {
            super(BlockCompressionMethod.RANS);
        }

        @Override
        public byte[] compress(final byte[] data) {
            return ExternalCompression.rans(data, ORDER.ONE);
        }
    }

    // Replace StructureTestUtils with calls to this
    public static ExternalCompressor getCompressorForMethod(
            final BlockCompressionMethod compressionMethod,
            final RANS.ORDER order) {
        switch (compressionMethod) {
            case RAW:
                return ExternalCompressor.createRAW();
            case GZIP:
                return ExternalCompressor.createGZIP();
            case LZMA:
                return ExternalCompressor.createLZMA();
            case RANS:
                if (order == RANS.ORDER.ZERO) {
                    return ExternalCompressor.createRANS(RANS.ORDER.ZERO);
                } else {
                    return ExternalCompressor.createRANS(RANS.ORDER.ONE);
                }
            case BZIP2:
                return ExternalCompressor.createBZIP2();
            default:
                throw new IllegalArgumentException(String.format("Unknown compression method %s", compressionMethod));
        }
    }

}
