package htsjdk.samtools.cram.structure.block;

import java.io.InputStream;

/**
 * A Block used by Slices to store their header data
 */
public class SliceHeaderBlock extends Block {
    private static final BlockContentType type = BlockContentType.MAPPED_SLICE;

    /**
     * Create a new slice header block with the given compression method and uncompressed content.
     * The block will have MAPPED_SLICE content type.
     *
     * @param method the compression method used in this block
     * @param compressedContent the content of this block, in compressed mode
     */
    SliceHeaderBlock(final BlockCompressionMethod method, final byte[] compressedContent) {
        super(method, type, compressedContent);
    }

    /**
     * Read a Block from an InputStream using Block.read() and cast to a SliceHeaderBlock
     * if the Block Content Type is correct
     *
     * @param major the CRAM major version number
     * @param inputStream the stream to read from
     * @return a new SliceHeaderBlock from the input
     */
    public static SliceHeaderBlock read(final int major, final InputStream inputStream) {
        final Block block = Block.read(major, inputStream);
        if (block.getContentType() != type)
            throw new RuntimeException("Content type does not match: " + block.getContentType().name());

        return (SliceHeaderBlock) block;
    }
}
