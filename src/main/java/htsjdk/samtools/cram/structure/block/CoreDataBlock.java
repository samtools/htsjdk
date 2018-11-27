package htsjdk.samtools.cram.structure.block;

/**
 * A Block used by Slices to store core data
 */
public class CoreDataBlock extends Block {
    private static final BlockContentType type = BlockContentType.CORE;

    /**
     * Create a new core data block with the given compression method and uncompressed content.
     * The block will have CORE content type.
     *
     * @param method the compression method used in this block
     * @param compressedContent the content of this block, in compressed mode
     */
    CoreDataBlock(final BlockCompressionMethod method, final byte[] compressedContent) {
        super(method, type, compressedContent);
    }
}
