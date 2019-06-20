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
 * Manage the (logical) set of blocks that constitute a {@link Slice}. Prevents duplicate
 * blocks (blocks with non-unique content ID) or illogical blocks (i.e., setting  a core block
 * that is not of type core block, or an external block that is not an external block) from
 * being added.
 */
public class SliceBlocks {
    // the core block for this Slice
    private Block coreBlock;
    // the external Blocks as a Map of content ID to block
    private Map<Integer, Block> externalBlocks = new HashMap<>();

    // Modeling the contentID and embedded reference block seperately is redundant, since the
    // block can be retrieved from the external blocks list given the content id, but we retain
    // them both for validation purposes because they're both present in the serialized CRAM stream,
    // and on read these are provided separately when populating the slice.
    private Block embeddedReferenceBlock;
    private int embeddedReferenceBlockContentID = Block.NO_CONTENT_ID;

    /**
     * Set the coreBlock for a Slice. Can only be called once.
     * @param coreBlock the core block for a Slice. May not be null.
     */
    public void setCoreBlock(final Block coreBlock) {
        ValidationUtils.nonNull(coreBlock);
        ValidationUtils.validateArg(coreBlock.getContentType() == BlockContentType.CORE, "Invalid core block");
        ValidationUtils.validateArg(this.coreBlock == null, "Can't reset core block");

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
     * Set the content ID of the embedded reference block. Per the CRAM spec, the value can be
     * -1 ({@link Block#NO_CONTENT_ID}) to indicate no embedded reference block is present. If
     * the reference block content ID already has a non-{@link Block#NO_CONTENT_ID} value,
     * it cannot be reset. If the embedded reference block has already been set, the provided
     * reference block content ID must agree with the content ID of the existing block.
     * @param embeddedReferenceBlockContentID
     */
    public void setEmbeddedReferenceContentID(final int embeddedReferenceBlockContentID) {
        if (this.embeddedReferenceBlockContentID != Block.NO_CONTENT_ID &&
                this.embeddedReferenceBlockContentID != embeddedReferenceBlockContentID) {
            throw new IllegalArgumentException(
                    String.format("Can't reset embedded reference content ID (old %d new %d)",
                            this.embeddedReferenceBlockContentID, embeddedReferenceBlockContentID));

        }
        if (this.embeddedReferenceBlock != null &&
                this.embeddedReferenceBlock.getContentId() != embeddedReferenceBlockContentID) {
            throw new IllegalArgumentException(
                    String.format("Attempt to set embedded reference block content ID (%d) that is in conflict" +
                            "with the content ID (%d) of the existing reference block",
                            embeddedReferenceBlockContentID,
                            this.embeddedReferenceBlock.getContentId()));
        }
        this.embeddedReferenceBlockContentID = embeddedReferenceBlockContentID;
    }

    /**
     * Get the content ID of the embedded reference block. Per the CRAM spec, the value
     * can be -1 ({@link Block#NO_CONTENT_ID}) to indicate no embedded reference block is
     * present.
     * @return id of embedded reference block if present, otherwise {@link Block#NO_CONTENT_ID}
     */
    public int getEmbeddedReferenceContentID() { return embeddedReferenceBlockContentID; }

    public void setEmbeddedReferenceBlock(final Block embeddedReferenceBlock) {
        ValidationUtils.nonNull(embeddedReferenceBlock, "Embedded reference block must be non-null");
        ValidationUtils.validateArg(embeddedReferenceBlock.getContentId() != Block.NO_CONTENT_ID,
                String.format("Invalid content ID (%d) for embedded reference block", embeddedReferenceBlock.getContentId()));
        ValidationUtils.validateArg(embeddedReferenceBlock.getContentType() == BlockContentType.EXTERNAL,
                String.format("Invalid embedded reference block type (%s)", embeddedReferenceBlock.getContentType()));
        if (this.embeddedReferenceBlock != null) {
            throw new IllegalArgumentException("Can't reset embedded reference block");
        } else if (this.embeddedReferenceBlockContentID != Block.NO_CONTENT_ID &&
                embeddedReferenceBlock.getContentId() != this.embeddedReferenceBlockContentID) {
            throw new IllegalArgumentException(
                    String.format(
                            "Embedded reference block content id (%d) conflicts with existing block if (%d)",
                            embeddedReferenceBlock.getContentId(),
                            this.embeddedReferenceBlockContentID));
        }

        this.embeddedReferenceBlock = embeddedReferenceBlock;
        addExternalBlock(embeddedReferenceBlock);
    }

    /**
     * Return the embedded reference block, if any.
     * @return embedded reference block. May be null.
     */
    public Block getEmbeddedReferenceBlock() { return embeddedReferenceBlock; }

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
     * Read the slic blocks from a CRAM stream.
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
                    if (embeddedReferenceBlockContentID == block.getContentId()) {
                        // TODO: embeddedRefBlock seems redundant since its also kept by ID in external blocks
                        setEmbeddedReferenceBlock(block);
                    } else {
                        addExternalBlock(block);
                    }
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
        coreBlock.write(majorVersion, outputStream);
        for (final Block block : externalBlocks.values()) {
            block.write(majorVersion, outputStream);
        }
    }

}
