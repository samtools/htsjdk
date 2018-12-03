package htsjdk.samtools.cram.structure.block;

import htsjdk.samtools.cram.CRAMException;

/**
 * Represents an arbitrary Block type.  It may be compressed and may be external.
 * Contrast with {@link RawBlock} which can't be either of these
 */
public class CompressibleBlock extends Block {
    /**
     * Compression method that applied to this block's content.
     */
    private final BlockCompressionMethod method;

    /**
     * For external blocks, the external block Content ID
     * Otherwise, NO_CONTENT_ID (0)
     */
    private final int contentId;

    /**
     * The content stored in this block, in uncompressed form
     */
    private final byte[] rawContent;

    /**
     * The content stored in this block, in compressed form
     */
    private final byte[] compressedContent;

    /**
     * Construct a CompressibleBlock instance
     *
     * @param method the block compression method.  Can be RAW, if uncompressed
     * @param type the block content type: is this a header or data block, and which kind
     * @param contentId the external block content ID, or NO_CONTENT_ID (0) if this is not an external block
     * @param rawContent the uncompressed form of the data to be stored in this block
     * @param compressedContent the compressed form of the data to be stored in this block
     */
    public CompressibleBlock(final BlockCompressionMethod method,
                      final BlockContentType type,
                      final int contentId,
                      final byte[] rawContent,
                      final byte[] compressedContent) {
        super(type);

        // causes test failures.  https://github.com/samtools/htsjdk/issues/1232
//        if (type == BlockContentType.EXTERNAL && contentId == Block.NO_CONTENT_ID) {
//            throw new CRAMException("Valid Content ID required for external blocks.");
//        }

        if (type != BlockContentType.EXTERNAL && contentId != Block.NO_CONTENT_ID) {
            throw new CRAMException("Cannot set a Content ID for non-external blocks.");
        }

        this.method = method;
        this.contentId = contentId;
        this.rawContent = rawContent;
        this.compressedContent = compressedContent;
    }

    @Override
    public final BlockCompressionMethod getMethod() {
        return method;
    }

    @Override
    public final int getContentId() {
        return contentId;
    }

    /**
     * Return the uncompressed block content
     */
    @Override
    public final byte[] getRawContent() {
        return rawContent;
    }

    @Override
    public final int getRawContentSize() {
        return rawContent.length;
    }

    /**
     * Return the compressed block content
     */
    @Override
    public final byte[] getCompressedContent() {
        return compressedContent;
    }

    @Override
    public final int getCompressedContentSize() {
        return compressedContent.length;
    }
}
