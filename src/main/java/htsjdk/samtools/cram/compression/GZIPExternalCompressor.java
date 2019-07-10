package htsjdk.samtools.cram.compression;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

public class GZIPExternalCompressor extends ExternalCompressor {
    // The writeCompressionLevel value is used for write only. When this class is used to read
    // (uncompress) data read from a CRAM block, writeCompressionLevel does not necessarily reflect
    // the level that was used to compress that data (the compression level that  used to create a
    // gzip compressed stream is not recovered from Slice block itself).
    private final int writeCompressionLevel;

    public GZIPExternalCompressor() {
        this(Defaults.COMPRESSION_LEVEL);
    }

    public GZIPExternalCompressor(final int compressionLevel) {
        super(BlockCompressionMethod.GZIP);
        this.writeCompressionLevel = compressionLevel;
    }

    /**
     * @return the gzip compression level used by this compressor's compress method
     */
    public int getWriteCompressionLevel() { return writeCompressionLevel; }

    @Override
    public byte[] compress(final byte[] data) {
        return ExternalCompression.gzip(data, writeCompressionLevel);
    }

    @Override
    public byte[] uncompress(byte[] data) {
        // Note that when uncompressing data that was retrieved from a (slice) data block
        // embedded in a CRAM stream, the writeCompressionLevel value is not recovered
        // from the block, and therefore does not necessarily reflect the value that was used
        // to compress the data that is now being uncompressed
        //TODO: implement this
        return new byte[0];
    }
}
