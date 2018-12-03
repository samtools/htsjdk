package htsjdk.samtools.cram.structure.block;

/**
 * Represents an uncompressed, non-external Block.
 * It has no Block Content ID and its Block Compression Method is always `RAW`
 * Contrast with {@link CompressibleBlock}
 */
public final class RawBlock extends Block {
    private final byte[] content;

    /**
     * Construct a RawBlock given a BlockContentType and the uncompressed content of the block
     *
     * @param contentType which type of Block does this represent
     * @param rawContent the uncompressed data to store in this Block
     */
    public RawBlock(final BlockContentType contentType, final byte[] rawContent) {
        super(contentType);
        this.content = rawContent;
    }

    @Override
    public final BlockCompressionMethod getMethod() {
        return BlockCompressionMethod.RAW;
    }

    /**
     * Return the uncompressed block content
     */
    @Override
    public final byte[] getRawContent() {
        return content;
    }

    @Override
    public final int getRawContentSize() {
        return content.length;
    }

    /**
     * Return the "compressed" block content.  This is the same as the uncompressed content.
     */
    @Override
    public final byte[] getCompressedContent() {
        return content;
    }

    @Override
    public final int getCompressedContentSize() {
        return content.length;
    }
}
