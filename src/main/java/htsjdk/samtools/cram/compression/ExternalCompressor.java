package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.compression.fqzcomp.FQZCompDecode;
import htsjdk.samtools.cram.compression.fqzcomp.FQZCompEncode;
import htsjdk.samtools.cram.compression.fqzcomp.FQZCompExternalCompressor;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokenisationDecode;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokenisationEncode;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokeniserExternalCompressor;
import htsjdk.samtools.cram.compression.range.RangeDecode;
import htsjdk.samtools.cram.compression.range.RangeEncode;
import htsjdk.samtools.cram.compression.range.RangeExternalCompressor;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Decode;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Encode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Decode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Encode;
import htsjdk.samtools.cram.structure.CRAMCodecModelContext;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.utils.ValidationUtils;

public abstract class ExternalCompressor {
    final public static int NO_COMPRESSION_ARG = -1;
    final private static String argErrorMessage = "Invalid compression arg (%d) requested for CRAM %s compressor";

    private BlockCompressionMethod method;

    protected ExternalCompressor(final BlockCompressionMethod method) {
        this.method = method;
    }

    /**
     * Compress the data using the codec-specific context model.
     * @param data the data to compress
     * @param contextModel the context model to use for compression; may be null
     * @return the compressed data
     */
    public abstract byte[] compress(byte[] data, CRAMCodecModelContext contextModel);

    public abstract byte[] uncompress(byte[] data);

    public BlockCompressionMethod getMethod() { return method; }

    @Override
    public String toString() {
        return this.getMethod().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExternalCompressor that = (ExternalCompressor) o;

        return getMethod() == that.getMethod();
    }

    @Override
    public int hashCode() {
        return getMethod().hashCode();
    }

    /**
     * Return an ExternalCompressor subclass based on the BlockCompressionMethod. Compressor-specific arguments
     * must be populated by the caller.
     * @param compressionMethod the type of compressor required ({@link BlockCompressionMethod})
     * @param compressorSpecificArg the required order for RANS compressors; or the desired write compression
     *                             level for GZIP
     * @return an ExternalCompressor of the requested type, populated with an compressor-specific args
     */
    public static ExternalCompressor getCompressorForMethod(
            final BlockCompressionMethod compressionMethod,
            final int compressorSpecificArg) {
        switch (compressionMethod) {
            case RAW:
                ValidationUtils.validateArg(
                        compressorSpecificArg == NO_COMPRESSION_ARG,
                        String.format(argErrorMessage, compressorSpecificArg, compressionMethod));
                return new RAWExternalCompressor();

            case GZIP:
                return compressorSpecificArg == NO_COMPRESSION_ARG ?
                        new GZIPExternalCompressor() :
                        new GZIPExternalCompressor(compressorSpecificArg);

            case LZMA:
                ValidationUtils.validateArg(
                        compressorSpecificArg == NO_COMPRESSION_ARG,
                        String.format(argErrorMessage, compressorSpecificArg, compressionMethod));
                return new LZMAExternalCompressor();

            case RANS:
                return compressorSpecificArg == NO_COMPRESSION_ARG ?
                        new RANS4x8ExternalCompressor(new RANS4x8Encode(), new RANS4x8Decode()) :
                        new RANS4x8ExternalCompressor(compressorSpecificArg, new RANS4x8Encode(), new RANS4x8Decode());

            case RANSNx16:
                return compressorSpecificArg == NO_COMPRESSION_ARG ?
                        new RANSNx16ExternalCompressor(new RANSNx16Encode(), new RANSNx16Decode()) :
                        new RANSNx16ExternalCompressor(compressorSpecificArg, new RANSNx16Encode(), new RANSNx16Decode());

            case ADAPTIVE_ARITHMETIC:
                return compressorSpecificArg == NO_COMPRESSION_ARG ?
                        new RangeExternalCompressor(new RangeEncode(), new RangeDecode()) :
                        new RangeExternalCompressor(compressorSpecificArg, new RangeEncode(), new RangeDecode());

            case NAME_TOKENISER:
                return new NameTokeniserExternalCompressor(new NameTokenisationEncode(), new NameTokenisationDecode());

            case FQZCOMP:
                return new FQZCompExternalCompressor(new FQZCompEncode(), new FQZCompDecode());

            case BZIP2:
                ValidationUtils.validateArg(
                        compressorSpecificArg == NO_COMPRESSION_ARG,
                        String.format(argErrorMessage, compressorSpecificArg, compressionMethod));
                return new BZIP2ExternalCompressor();

            default:
                throw new IllegalArgumentException(String.format("Unknown compression method %s", compressionMethod));
        }
    }

}