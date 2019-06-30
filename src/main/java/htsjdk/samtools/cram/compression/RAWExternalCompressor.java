package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

/**
 * Raw compressor that does no compression.
 */
public class RAWExternalCompressor extends ExternalCompressor {

    public RAWExternalCompressor() {
        super(BlockCompressionMethod.RAW);
    }

    @Override
    public byte[] compress(final byte[] data) {
        return data;
    }

    @Override
    public byte[] uncompress(byte[] data) {
        return data;
    }
}
