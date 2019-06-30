package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

public class LZMAExternalCompressor extends ExternalCompressor {

    public LZMAExternalCompressor() {
        super(BlockCompressionMethod.LZMA);
    }

    @Override
    public byte[] compress(final byte[] data) {
        return ExternalCompression.xz(data);
    }

    @Override
    public byte[] uncompress(byte[] data) {
        //TODO: implement this
        return new byte[0];
    }

}
