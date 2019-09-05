package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.compression.*;
import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.util.Tuple;
import htsjdk.utils.ValidationUtils;

import java.util.HashMap;

/**
 * Maintain a cache of reusable compressor instances in order to reduce the need to repeatedly
 * instantiate them, since some, like the RANS de/compressor, allocate large numbers (~256k) of
 * small temporary objects every time its instantiated.
 */
public class CompressorCache {
    private final String argErrorMessage = "Invalid compression arg (%d) requested for CRAM %s compressor";
    private final HashMap<Tuple<BlockCompressionMethod, Integer>, ExternalCompressor> compressorCache = new HashMap<>();
    private RANS sharedRANS;

    /**
     * Return a compressor if its in our cache, otherwise spin one up and cache it and return that.
     * @param compressionMethod
     * @param compressorSpecificArg
     * @return a cached compressor instance
     */
    public ExternalCompressor getCompressorForMethod(
            final BlockCompressionMethod compressionMethod,
            final int compressorSpecificArg) {
        switch (compressionMethod) {
            case RAW:
                ValidationUtils.validateArg(
                        compressorSpecificArg == ExternalCompressor.NO_COMPRESSION_ARG,
                        String.format(argErrorMessage, compressorSpecificArg, compressionMethod));
                return getCachedCompressorForMethod(compressionMethod, compressorSpecificArg);

            case GZIP:
                return getCachedCompressorForMethod(compressionMethod, compressorSpecificArg);

            case LZMA:
                ValidationUtils.validateArg(
                        compressorSpecificArg == ExternalCompressor.NO_COMPRESSION_ARG,
                        String.format(argErrorMessage, compressorSpecificArg, compressionMethod));
                return getCachedCompressorForMethod(compressionMethod, compressorSpecificArg);

            case RANS:
                // for efficiency, we want to share the same underlying RANS object with both order-0 and
                // order-1 ExternalCompressors
                final int ransArg = compressorSpecificArg == ExternalCompressor.NO_COMPRESSION_ARG ?
                                RANS.ORDER.ZERO.ordinal() :
                                compressorSpecificArg;
                final Tuple<BlockCompressionMethod, Integer> compressorTuple = new Tuple<>(
                        BlockCompressionMethod.RANS,
                        ransArg);
                if (!compressorCache.containsKey(compressorTuple)) {
                    if (sharedRANS == null) {
                        sharedRANS = new RANS();
                    }
                    compressorCache.put(
                            new Tuple(BlockCompressionMethod.RANS, ransArg),
                            new RANSExternalCompressor(ransArg, sharedRANS)
                    );
                }
                return getCachedCompressorForMethod(compressorTuple.a, compressorTuple.b);

            case BZIP2:
                ValidationUtils.validateArg(
                        compressorSpecificArg == ExternalCompressor.NO_COMPRESSION_ARG,
                        String.format(argErrorMessage, compressorSpecificArg, compressionMethod));
                return getCachedCompressorForMethod(compressionMethod, compressorSpecificArg);

            default:
                throw new IllegalArgumentException(String.format("Unknown compression method %s", compressionMethod));
        }
    }

    private ExternalCompressor getCachedCompressorForMethod(final BlockCompressionMethod method, final int compressorSpecificArg) {
        return compressorCache.computeIfAbsent(
                new Tuple<>(method, compressorSpecificArg),
                k -> ExternalCompressor.getCompressorForMethod(
                        method,
                        compressorSpecificArg)
        );
    }

}
