package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

public class BZIP2ExternalCompressor extends ExternalCompressor {

    public BZIP2ExternalCompressor() {
        super(BlockCompressionMethod.BZIP2);
    }

    @Override
    public byte[] compress(final byte[] data) {
        return ExternalCompression.bzip2(data);
    }

    @Override
    public byte[] uncompress(byte[] data) {
        //TODO: implement this
        return new byte[0];
    }
}
