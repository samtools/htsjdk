package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.structure.block.Block;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manage the set of streams used to hold each block that is consumed when reading a CRAM stream.
 */
public class SliceBlocksReadStreams {

    // bit input stream for the core block
    private final BitInputStream coreBlockInputStream;
    // ByteArrayInputStreams for all external contentIDs, including tags
    private final Map<Integer, ByteArrayInputStream> externalInputStreams = new HashMap<>();

    /**
     * @param sliceBlocks {@link SliceBlocks} that have been populated from a CRAM stream
     */
    public SliceBlocksReadStreams(final SliceBlocks sliceBlocks) {
        if (sliceBlocks.getCoreBlock() == null || sliceBlocks.getNumberOfExternalBlocks() == 0) {
            throw new CRAMException("slice blocks must be initialized before being used with a reader");
        }
        coreBlockInputStream = new DefaultBitInputStream(new ByteArrayInputStream(sliceBlocks.getCoreBlock().getUncompressedContent()));

        final List<Integer> externalContentIDs = sliceBlocks.getExternalContentIDs();
        for (final Integer contentID : externalContentIDs) {
            final Block block = sliceBlocks.getExternalBlock(contentID);
            externalInputStreams.put(contentID, new ByteArrayInputStream(block.getUncompressedContent()));
        }
    }

    /**
     * Get the {@link BitInputStream} for this {@link SliceBlocks} core block
     * @return {@link BitInputStream} for the core block
     */
    public BitInputStream getCoreBlockInputStream() {
        return coreBlockInputStream;
    }

    /**
     * Get the ByteArrayInputStream for the given contentID.
     * @param contentID
     * @return ByteArrayInputStream for contentID
     */
    public ByteArrayInputStream getExternalInputStream(final Integer contentID) { return externalInputStreams.get(contentID); }
}
