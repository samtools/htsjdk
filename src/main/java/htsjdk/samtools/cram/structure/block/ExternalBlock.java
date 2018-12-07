package htsjdk.samtools.cram.structure.block;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.ExternalCompressor;

/**
 * A Block used by Slices to store data externally
 */
public class ExternalBlock extends Block {
    private final int contentId;

    /**
     * Create a new external data block with the given compression method, uncompressed content, and content ID.
     * The block will have EXTERNAL content type.
     *
     * @param compressionMethod the compression method used in this block
     * @param contentId the external identifier for the block
     * @param compressedContent the content of this block, in compressed mode
     * @param uncompressedLength the length of the content stored in this block when uncompressed
     */
    public ExternalBlock(final BlockCompressionMethod compressionMethod,
                         final int contentId,
                         final byte[] compressedContent,
                         final int uncompressedLength) {
        super(compressionMethod, BlockContentType.EXTERNAL, compressedContent, uncompressedLength);
        this.contentId = contentId;
    }

    @Override
    public final int getContentId() {
        return contentId;
    }
}
