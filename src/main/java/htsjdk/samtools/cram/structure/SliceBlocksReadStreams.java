package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.utils.ValidationUtils;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides a layer over a {@link SliceBlocks} object and acts as a bridge between the DataSeries codecs
 * and their underlying blocks when reading a CRAM stream by presenting a bit (core) or byte (external) stream
 * for each block.
 */
public class SliceBlocksReadStreams {

    // bit input stream for the core block
    private final BitInputStream coreBlockInputStream;
    // Map of ByteArrayInputStreams for all external contentIDs, including tag blocks, by content ID
    private final Map<Integer, ByteArrayInputStream> externalInputStreams = new HashMap<>();

    /**
     * @param sliceBlocks {@link SliceBlocks} that have been populated from a CRAM stream
     */
    public SliceBlocksReadStreams(final SliceBlocks sliceBlocks, final CompressorCache compressorCache) {
        ValidationUtils.nonNull(sliceBlocks.getCoreBlock(), "sliceBlocks must have been initialized");
        ValidationUtils.nonNull(sliceBlocks.getNumberOfExternalBlocks() > 0, "sliceBlocks must have been initialized");

        if (sliceBlocks.getCoreBlock() == null || sliceBlocks.getNumberOfExternalBlocks() == 0) {
            throw new CRAMException("slice blocks must be initialized before being used with a reader");
        }
        coreBlockInputStream = new DefaultBitInputStream(
                new ByteArrayInputStream(
                        sliceBlocks.getCoreBlock().getUncompressedContent(compressorCache)));

        final List<Integer> externalContentIDs = sliceBlocks.getExternalContentIDs();
        for (final Integer contentID : externalContentIDs) {
            final Block block = sliceBlocks.getExternalBlock(contentID);
            externalInputStreams.put(contentID, new ByteArrayInputStream(block.getUncompressedContent(compressorCache)));
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
