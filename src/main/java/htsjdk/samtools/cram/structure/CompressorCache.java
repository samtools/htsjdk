/*
 * Copyright (c) 2019 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.compression.RANSExternalCompressor;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Decode;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Encode;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Params;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.util.Tuple;
import htsjdk.utils.ValidationUtils;

import java.util.HashMap;

/**
 * Maintain a cache of reusable compressor instances in order to reduce the need to repeatedly
 * instantiate them, since some, like the RANS de/compressor, allocate large numbers (~256k) of
 * small temporary objects every time they're instantiated.
 */
public class CompressorCache {
    private final String argErrorMessage = "Invalid compression arg (%d) requested for CRAM %s compressor";
    private final HashMap<Tuple<BlockCompressionMethod, Integer>, ExternalCompressor> compressorCache = new HashMap<>();
    private RANS4x8Encode sharedRANSEncode;
    private RANS4x8Decode sharedRANSDecode;

    /**
     * Return a compressor if its in our cache, otherwise spin one up and cache it and return it.
     * @param compressionMethod
     * @param compressorSpecificArg
     * @return a cached compressor instance
     */
    public ExternalCompressor getCompressorForMethod(
            final BlockCompressionMethod compressionMethod,
            final int compressorSpecificArg) {
        switch (compressionMethod) {
            case GZIP:
                return getCachedCompressorForMethod(compressionMethod, compressorSpecificArg);

            case BZIP2:
            case RAW:
            case LZMA:
                ValidationUtils.validateArg(
                        compressorSpecificArg == ExternalCompressor.NO_COMPRESSION_ARG,
                        String.format(argErrorMessage, compressorSpecificArg, compressionMethod));
                return getCachedCompressorForMethod(compressionMethod, compressorSpecificArg);

            case RANS:
                // for efficiency, we want to share the same underlying RANS object with both order-0 and
                // order-1 ExternalCompressors
                final int ransArg = compressorSpecificArg == ExternalCompressor.NO_COMPRESSION_ARG ?
                                RANS4x8Params.ORDER.ZERO.ordinal() :
                                compressorSpecificArg;
                final Tuple<BlockCompressionMethod, Integer> compressorTuple = new Tuple<>(
                        BlockCompressionMethod.RANS,
                        ransArg);
                if (!compressorCache.containsKey(compressorTuple)) {
                    if (sharedRANSEncode == null) {
                        sharedRANSEncode = new RANS4x8Encode();
                    }
                    if (sharedRANSDecode == null) {
                        sharedRANSDecode = new RANS4x8Decode();
                    }
                    compressorCache.put(
                            new Tuple(BlockCompressionMethod.RANS, ransArg),
                            new RANSExternalCompressor(ransArg, sharedRANSEncode, sharedRANSDecode)
                    );
                }
                return getCachedCompressorForMethod(compressorTuple.a, compressorTuple.b);
            case RANGE:
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