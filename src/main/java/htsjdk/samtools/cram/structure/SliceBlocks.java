package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockContentType;
import htsjdk.utils.ValidationUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manage the (logical) set of blocks that constitute a {@link Slice}, not including the Slice header block,
 * which is managed by {@link Slice}.
 *
 * Prevents duplicate blocks (blocks with non-unique content ID) or illogical blocks (i.e., setting
 * a core block that is not of type core block, or an external block that is not an external block) from
 * being added.
 */
public class SliceBlocks {
    // the core block for this Slice
    private Block coreBlock;
    // the external Blocks as a Map of content ID to block
    private Map<Integer, Block> externalBlocks = new HashMap<>();

    /**
     * Set the coreBlock for a Slice. Can only be called once.
     * @param coreBlock the core block for a Slice. May not be null.
     */
    public void setCoreBlock(final Block coreBlock) {
        ValidationUtils.nonNull(coreBlock);
        ValidationUtils.validateArg(coreBlock.getContentType() == BlockContentType.CORE, "Invalid slice core block");
        ValidationUtils.validateArg(this.coreBlock == null, "Can't reset slice core block");

        this.coreBlock = coreBlock;
    }

    /**
     * Return the core block for this Slice. May be null.
     */
    public Block getCoreBlock() { return coreBlock; }

    /**
     * Add an external block to the Slice's slice blocks.
     * @param externalBlock An external block. May not be null, and must not already be present in this SliceBlocks.
     */
    public void addExternalBlock(final Block externalBlock) {
        ValidationUtils.validateArg(externalBlock.getContentType() == BlockContentType.EXTERNAL, "Invalid external block");
        if (externalBlocks.containsKey(externalBlock.getContentId())) {
            throw new CRAMException(
                    String.format(
                            "Attempt to add a duplicate block (id %d of type %s) to compression header encoding map. " +
                                    "Existing block is of type %s.",
                            externalBlock.getContentId(),
                            externalBlock.getContentType(),
                            externalBlocks.get(externalBlock.getContentId()).getContentType()));
        }
        externalBlocks.put(externalBlock.getContentId(), externalBlock);
    }

    /**
     * Get the external block corresponding to a contentID.
     * @param contentID contentID identifying the external block
     * @return external block for the contentID. May be null.
     */
    public Block getExternalBlock(final Integer contentID) {
        return externalBlocks.get(contentID);
    }

    /**
     * Return a list of external content IDs. May be empty. For each content ID returned, the corresponding
     * external block can be obtained using {@link #getExternalBlock}.
     * @return list of external content IDs
     */
    public List<Integer> getExternalContentIDs() {
        final List<Integer> contentIDs = new ArrayList<>(externalBlocks.size());
        externalBlocks.forEach((i, b) -> contentIDs.add(i));
        return contentIDs;
    }

    /**
     * Number of external locks present in this SliceBlocks object (does not include the core block).
     * @return number of external blocks, including any embedded reference block, but excluding the core block
     */
    public int getNumberOfExternalBlocks() { return externalBlocks.size(); }

    /**
     * Read the set of blocks that make up a slice from a CRAM stream.
     * @param majorVersion CRAM version being read
     * @param numberOfBlocks number of blocks to consume from the stream
     * @param inputStream stream to consume
     */
    public void readBlocks(final int majorVersion, final int numberOfBlocks, final InputStream inputStream) {
        for (int i = 0; i < numberOfBlocks; i++) {
            final Block block = Block.read(majorVersion, inputStream);

            switch (block.getContentType()) {
                case CORE:
                    setCoreBlock(block);
                    break;
                case EXTERNAL:
                    addExternalBlock(block);
                    break;

                default:
                    throw new RuntimeException("Not a slice block, content type id " + block.getContentType().name());
            }
        }
    }

    /**
     * Write the coreBlock and each external block out to a CRAM stream. There is no predefined
     * order that must be honored, since each block is identified by a unique contentID.
     * @param majorVersion CRAM major version being written
     * @param outputStream stream to write blocks to
     */
    public void writeBlocks(final int majorVersion, final OutputStream outputStream) {
        if (coreBlock == null) {
            throw new IllegalArgumentException(
                    "A core block must be provided before slice blocks can be written to a CRAM stream");
        }
        // write the core and external blocks; any embedded reference block is included in the list of
        // external blocks
        coreBlock.write(majorVersion, outputStream);
        for (final Block block : externalBlocks.values()) {
            block.write(majorVersion, outputStream);
        }
    }

}
