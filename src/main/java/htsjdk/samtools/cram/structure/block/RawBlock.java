package htsjdk.samtools.cram.structure.block;

/**
 * Represents an uncompressed, non-external Block.
 * It has no Block Content ID and its Block Compression Method is always `RAW`
 * Contrast with {@link CompressibleBlock}
 */
public final class RawBlock extends Block {
    /**
     * Construct a RawBlock given a BlockContentType and the uncompressed content of the block
     *
     * @param contentType which type of Block does this represent
     * @param rawContent the uncompressed data to store in this Block
     */
    public RawBlock(final BlockContentType contentType, final byte[] rawContent) {
        super(contentType, rawContent);
    }

    @Override
    public final BlockCompressionMethod getMethod() {
        return BlockCompressionMethod.RAW;
    }

    /**
     * Return the uncompressed block content
     */
    @Override
    public final byte[] getUncompressedContent() {
        return getCompressedContent();
    }
}
