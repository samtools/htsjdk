package htsjdk.samtools.cram.compression.fqzcomp;

import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.structure.CRAMCodecModelContext;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

public class FQZCompExternalCompressor extends ExternalCompressor {

    // this codec is decode only; not implemented for writing
    private final FQZCompDecode fqzCompDecoder;

    public FQZCompExternalCompressor(
            final FQZCompEncode unused_fqzCompEncoder,
            final FQZCompDecode fqzCompDecoder) {
        super(BlockCompressionMethod.FQZCOMP);
        this.fqzCompDecoder = fqzCompDecoder;
    }

    @Override
    public byte[] compress(byte[] data, final CRAMCodecModelContext unused_contextModel) {
        throw new UnsupportedOperationException("FQZComp compression is not implemented");
    }

    @Override
    public byte[] uncompress(byte[] data) {
        return fqzCompDecoder.uncompress(CompressionUtils.wrap(data)).array();
    }

}