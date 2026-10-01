package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.structure.CRAMCodecModelContext;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

public class NameTokeniserExternalCompressor extends ExternalCompressor {
    /** The compressor-specific argument that selects the arithmetic coder (htslib's TOKA) rather than rANS (TOK3). */
    public static final int USE_ARITH = 1;

    private final NameTokenisationEncode nameTokEncoder;
    private final NameTokenisationDecode nameTokDecoder;
    // arith coding is typically 1-5% smaller, but around 50-100% slower
    private final boolean useArith;

    public NameTokeniserExternalCompressor(
            final NameTokenisationEncode nameTokEncoder, final NameTokenisationDecode nameTokDecoder) {
        this(nameTokEncoder, nameTokDecoder, false);
    }

    /**
     * @param useArith true to code the token streams with the arithmetic (Range) coder, false for rANS Nx16
     */
    public NameTokeniserExternalCompressor(
            final NameTokenisationEncode nameTokEncoder,
            final NameTokenisationDecode nameTokDecoder,
            final boolean useArith) {
        super(BlockCompressionMethod.NAME_TOKENISER);
        this.nameTokEncoder = nameTokEncoder;
        this.nameTokDecoder = nameTokDecoder;
        this.useArith = useArith;
    }

    @Override
    public byte[] compress(byte[] data, final CRAMCodecModelContext unused_contextModel) {
        return CompressionUtils.toByteArray(
                nameTokEncoder.compress(CompressionUtils.wrap(data), useArith, NameTokenisationDecode.NAME_SEPARATOR));
    }

    @Override
    public byte[] uncompress(byte[] data) {
        return nameTokDecoder.uncompress(CompressionUtils.wrap(data), NameTokenisationDecode.NAME_SEPARATOR);
    }

    @Override
    public boolean equals(final Object o) {
        return super.equals(o) && useArith == ((NameTokeniserExternalCompressor) o).useArith;
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + Boolean.hashCode(useArith);
    }
}
