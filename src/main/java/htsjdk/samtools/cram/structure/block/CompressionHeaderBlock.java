package htsjdk.samtools.cram.structure.block;

import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.InputStream;

/**
 * A Block used by Containers to store Compression Header data
 */
public class CompressionHeaderBlock extends Block {
    private static final BlockContentType type = BlockContentType.COMPRESSION_HEADER;

    /**
     * Create a new compression header block with the given compression method and uncompressed content.
     * The block will have COMPRESSION_HEADER content type.
     *
     * @param method the compression method used in this block
     * @param compressedContent the content of this block, in compressed mode
     * @param uncompressedLength the length of the content stored in this block when uncompressed
     */
    CompressionHeaderBlock(final BlockCompressionMethod method,
                           final byte[] compressedContent,
                           final int uncompressedLength) {
        super(method, type, compressedContent, uncompressedLength);
    }

    /**
     * Read a Block from an InputStream using Block.read() and cast to a CompressionHeaderBlock
     * if the Block Content Type is correct
     *
     * @param major the CRAM major version number
     * @param inputStream the stream to read from
     * @return a new CompressionHeaderBlock from the input
     */
    public static CompressionHeaderBlock read(final int major, final InputStream inputStream) {
        final Block block = Block.read(major, inputStream);
        if (block.getContentType() != type)
            throw new RuntimeIOException("Content type does not match: " + block.getContentType().name());

        return (CompressionHeaderBlock) block;
    }

    /**
     * Read a CompressionHeaderBlock from an InputStream and return its contents as a CompressionHeader
     *
     * @param major the CRAM major version number
     * @param inputStream the stream to read from
     * @return a new CompressionHeader from the input
     */
    public static CompressionHeader readAsCompressionHeader(final int major, final InputStream inputStream) {
        final CompressionHeaderBlock block = read(major, inputStream);
        final CompressionHeader header = new CompressionHeader();
        header.read(block.getUncompressedContent());
        return header;
    }
}
