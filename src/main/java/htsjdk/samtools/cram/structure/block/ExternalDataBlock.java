package htsjdk.samtools.cram.structure.block;

/**
 * A Block used by Slices to store data externally
 */
public class ExternalDataBlock extends Block {
    private static final BlockContentType type = BlockContentType.EXTERNAL;

    private final int contentId;

    /**
     * Create a new external data block with the given compression method, uncompressed content, and content ID.
     * The block will have EXTERNAL content type.
     *
     * @param method the compression method used in this block
     * @param compressedContent the content of this block, in compressed mode
     * @param uncompressedLength the length of the content stored in this block when uncompressed
     * @param contentId the external identifier for the block
     */
    ExternalDataBlock(final BlockCompressionMethod method,
                      final byte[] compressedContent,
                      final int uncompressedLength,
                      final int contentId) {
        super(method, type, compressedContent, uncompressedLength);
        this.contentId = contentId;
    }

    @Override
    public final int getContentId() {
        return contentId;
    }
}
