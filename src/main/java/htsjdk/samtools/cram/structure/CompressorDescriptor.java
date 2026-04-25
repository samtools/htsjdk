package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

/**
 * Describes which compression method and parameters to use for a CRAM data series block.
 * Pairs a {@link BlockCompressionMethod} with an optional compressor-specific integer argument
 * (e.g., GZIP compression level, rANS order). Maps 1:1 to
 * {@link ExternalCompressor#getCompressorForMethod(BlockCompressionMethod, int)}.
 *
 * @param method the block compression method
 * @param arg compressor-specific argument, or {@link ExternalCompressor#NO_COMPRESSION_ARG} if none
 */
public record CompressorDescriptor(BlockCompressionMethod method, int arg) {

    /**
     * Create a descriptor for a compression method that takes no argument
     * (e.g., RAW, BZIP2, LZMA, NAME_TOKENISER, FQZCOMP).
     *
     * @param method the block compression method
     */
    public CompressorDescriptor(final BlockCompressionMethod method) {
        this(method, ExternalCompressor.NO_COMPRESSION_ARG);
    }
}
