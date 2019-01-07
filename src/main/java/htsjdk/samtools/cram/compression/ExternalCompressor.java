package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.cram.compression.rans.RANS.ORDER;

public abstract class ExternalCompressor {
    private final BlockCompressionMethod method;

    private ExternalCompressor(final BlockCompressionMethod method) {
        this.method = method;
    }

    public BlockCompressionMethod getMethod() {
        return method;
    }

    public abstract byte[] compress(byte[] data);

    public static ExternalCompressor createRAW() {
        return new ExternalCompressor(BlockCompressionMethod.RAW) {

            @Override
            public byte[] compress(final byte[] data) {
                return data;
            }
        };
    }

    public static ExternalCompressor createGZIP() {
        return new ExternalCompressor(BlockCompressionMethod.GZIP) {

            @Override
            public byte[] compress(final byte[] data) {
                return ExternalCompression.gzip(data);
            }
        };
    }

    public static ExternalCompressor createLZMA() {
        return new ExternalCompressor(BlockCompressionMethod.LZMA) {

            @Override
            public byte[] compress(final byte[] data) {
                return ExternalCompression.xz(data);
            }
        };
    }

    public static ExternalCompressor createBZIP2() {
        return new ExternalCompressor(BlockCompressionMethod.BZIP2) {

            @Override
            public byte[] compress(final byte[] data) {
                return ExternalCompression.bzip2(data);
            }
        };

    }

    public static ExternalCompressor createRANS(final ORDER order) {
        return new ExternalCompressor(BlockCompressionMethod.RANS) {

            @Override
            public byte[] compress(final byte[] data) {
                return ExternalCompression.rans(data, order);
            }
        };
    }
}
