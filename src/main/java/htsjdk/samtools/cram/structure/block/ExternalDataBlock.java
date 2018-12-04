package htsjdk.samtools.cram.structure.block;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.ExternalCompressor;

/**
 * A Block used by Slices to store data externally
 */
public class ExternalDataBlock extends Block {
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
        super(method, BlockContentType.EXTERNAL, compressedContent, uncompressedLength);
        this.contentId = contentId;
    }

    /**
     * Create a new external data block with the given content ID, compressor, and uncompressed content.
     * The block will have EXTERNAL content type.
     *
     * @param contentId the external identifier for the block
     * @param compressor which external compressor to use on this block
     * @param rawContent the uncompressed content of the block
     */
    public ExternalDataBlock(final int contentId, final ExternalCompressor compressor, final byte[] rawContent) {
        this(compressor.getMethod(), compressor.compress(rawContent), rawContent.length, contentId);
        
        // remove after https://github.com/samtools/htsjdk/issues/1232
        if (contentId == Block.NO_CONTENT_ID) {
            throw new CRAMException("Valid Content ID required.  Given: " + contentId);
        }
    }

    @Override
    public final int getContentId() {
        return contentId;
    }
}
