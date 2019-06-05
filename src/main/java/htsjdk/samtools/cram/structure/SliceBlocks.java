/*
 * Copyright (c) 2019 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockContentType;
import htsjdk.utils.ValidationUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

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
    private Map<Integer, Block> externalBlocks = new TreeMap<>();

    /**
     * Create a new SliceBlocks object from a core block and one or more external blocks.
     * @param coreBlock the core block for the Slice, may not be null
     * @param externalBlocks the external blocks for this Slice, may not be null and must contain a least one block
     */
    public SliceBlocks(final Block coreBlock, final List<Block> externalBlocks) {
        ValidationUtils.nonNull(coreBlock, "A core block is required");
        ValidationUtils.validateArg(externalBlocks != null && externalBlocks.size() != 0, "A core block is required");

        setCoreBlock(coreBlock);
        for (final Block block : externalBlocks) {
            addExternalBlock(block);
        }
    }

    /**
     * Read the set of blocks that make up a slice from a CRAM stream.
     * @param cramVersion CRAM version being read
     * @param numberOfBlocks number of blocks to consume from the stream
     * @param inputStream stream to consume
     */
    public SliceBlocks(final CRAMVersion cramVersion, final int numberOfBlocks, final InputStream inputStream) {
        for (int i = 0; i < numberOfBlocks; i++) {
            final Block block = Block.read(cramVersion, inputStream);

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
        if (getCoreBlock() == null) {
            throw new CRAMException("A core block is required in a CRAM stream but none was found.");
        }
    }

    /**
     * Return the core block for this Slice. May be null.
     */
    public Block getCoreBlock() { return coreBlock; }

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
        return new ArrayList<>(externalBlocks.keySet());
    }

    /**
     * Number of external locks present in this SliceBlocks object (does not include the core block).
     * @return number of external blocks, including any embedded reference block, but excluding the core block
     */
    public int getNumberOfExternalBlocks() { return externalBlocks.size(); }

    /**
     * Write the coreBlock and each external block out to a CRAM stream. There is no predefined
     * order that must be honored, since each block is identified by a unique contentID.
     * @param cramVersion CRAM major version being written
     * @param outputStream stream to write blocks to
     */
    public void writeBlocks(final CRAMVersion cramVersion, final OutputStream outputStream) {
        if (coreBlock == null) {
            throw new IllegalArgumentException(
                    "A core block must be provided before slice blocks can be written to a CRAM stream");
        }
        // write the core and external blocks; any embedded reference block is included in the list of
        // external blocks
        coreBlock.write(cramVersion, outputStream);
        for (final Block block : externalBlocks.values()) {
            block.write(cramVersion, outputStream);
        }
    }

    /**
     * Set the coreBlock for a Slice. Can only be called once.
     * @param coreBlock the core block for a Slice. May not be null.
     */
    private void setCoreBlock(final Block coreBlock) {
        ValidationUtils.nonNull(coreBlock);
        ValidationUtils.validateArg(coreBlock.getContentType() == BlockContentType.CORE, "Invalid slice core block");
        ValidationUtils.validateArg(this.coreBlock == null, "Can't reset slice core block");

        this.coreBlock = coreBlock;
    }

    /**
     * Add an external block to the Slice's slice blocks.
     * @param externalBlock An external block. May not be null, and must not already be present in this SliceBlocks.
     */
    private void addExternalBlock(final Block externalBlock) {
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

}
