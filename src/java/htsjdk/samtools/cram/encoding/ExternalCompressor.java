package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.encoding.rans.RANS.ORDER;
import htsjdk.samtools.cram.io.ByteBufferUtils;
import htsjdk.samtools.cram.structure.BlockCompressionMethod;

import java.io.IOException;

public abstract class ExternalCompressor {
    BlockCompressionMethod method;

    protected ExternalCompressor(BlockCompressionMethod method) {
        this.method = method;
    }

    public BlockCompressionMethod getMethod() {
        return method;
    }

    public abstract byte[] compress(byte[] data);

    public static ExternalCompressor createRAW() {
        return new ExternalCompressor(BlockCompressionMethod.RAW) {

            @Override
            public byte[] compress(byte[] data) {
                return data;
            }
        };
    }

    public static ExternalCompressor createGZIP(final int level) {
        return new ExternalCompressor(BlockCompressionMethod.GZIP) {

            @Override
            public byte[] compress(byte[] data) {
                try {
                    return ByteBufferUtils.gzip(data);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public static ExternalCompressor createLZMA() {
        return new ExternalCompressor(BlockCompressionMethod.LZMA) {

            @Override
            public byte[] compress(byte[] data) {
                try {
                    return ByteBufferUtils.xz(data);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public static ExternalCompressor createBZIP2() {
        return new ExternalCompressor(BlockCompressionMethod.BZIP2) {

            @Override
            public byte[] compress(byte[] data) {
                try {
                    return ByteBufferUtils.bzip2(data);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

    }

    public static ExternalCompressor createRANS(final ORDER order) {
        return new ExternalCompressor(BlockCompressionMethod.RANS) {

            @Override
            public byte[] compress(byte[] data) {
                return ByteBufferUtils.rans(data, order);
            }
        };
    }
}
