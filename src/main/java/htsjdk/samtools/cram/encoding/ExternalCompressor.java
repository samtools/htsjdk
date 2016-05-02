package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.encoding.rans.RANS.ORDER;
import htsjdk.samtools.cram.io.ExternalCompression;
import htsjdk.samtools.cram.structure.BlockCompressionMethod;

import java.io.IOException;

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
                try {
                    return ExternalCompression.gzip(data);
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public static ExternalCompressor createLZMA() {
        return new ExternalCompressor(BlockCompressionMethod.LZMA) {

            @Override
            public byte[] compress(final byte[] data) {
                try {
                    return ExternalCompression.xz(data);
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public static ExternalCompressor createBZIP2() {
        return new ExternalCompressor(BlockCompressionMethod.BZIP2) {

            @Override
            public byte[] compress(final byte[] data) {
                try {
                    return ExternalCompression.bzip2(data);
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
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
