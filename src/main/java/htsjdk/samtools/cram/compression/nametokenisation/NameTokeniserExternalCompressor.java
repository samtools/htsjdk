package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.ExternalCompressor;

import htsjdk.samtools.cram.structure.CRAMCodecModelContext;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

public class NameTokeniserExternalCompressor extends ExternalCompressor {

    private final NameTokenisationEncode nameTokEncoder;
    private final NameTokenisationDecode nameTokDecoder;

    public NameTokeniserExternalCompressor(
            final NameTokenisationEncode nameTokEncoder,
            final NameTokenisationDecode nameTokDecoder) {
        super(BlockCompressionMethod.NAME_TOKENISER);
        this.nameTokEncoder = nameTokEncoder;
        this.nameTokDecoder = nameTokDecoder;
    }

    @Override
    public byte[] compress(byte[] data, final CRAMCodecModelContext unused_contextModel) {
        return CompressionUtils.toByteArray(nameTokEncoder.compress(
                CompressionUtils.wrap(data),
                false, // arith coding is typically 1-5% smaller, but around 50-100% slower
                NameTokenisationDecode.NAME_SEPARATOR));
    }

    @Override
    public byte[] uncompress(byte[] data) {
        return nameTokDecoder.uncompress(CompressionUtils.wrap(data), NameTokenisationDecode.NAME_SEPARATOR);
    }

}