package htsjdk.samtools.cram.structure.block;

/**
 * A Block used by Containers to store File Header data
 */
public class FileHeaderBlock extends Block {
    private static final BlockContentType type = BlockContentType.FILE_HEADER;

    /**
     * Create a new file header block with the given compression method and uncompressed content.
     * The block will have FILE_HEADER content type.
     *
     * @param method the compression method used in this block
     * @param compressedContent the content of this block, in compressed mode
     * @param uncompressedLength the length of the content stored in this block when uncompressed
     */
    FileHeaderBlock(final BlockCompressionMethod method,
                    final byte[] compressedContent,
                    final int uncompressedLength) {
        super(method, type, compressedContent, uncompressedLength);
    }
}
