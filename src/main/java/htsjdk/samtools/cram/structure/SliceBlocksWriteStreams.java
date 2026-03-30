package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.CRAMByteWriter;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Provides a layer over a {@link SliceBlocks} object and acts as a bridge between the DataSeries codecs
 * and their underlying blocks when writing a CRAM stream by presenting a bit (core) or byte (external) stream
 * for each block.
 *
 * <p>External block streams use unsynchronized {@link CRAMByteWriter} instead of
 * {@link ByteArrayOutputStream} to eliminate synchronized method call overhead in the hot encode path.
 */
public class SliceBlocksWriteStreams {

    private final CompressionHeader compressionHeader;
    private final ByteArrayOutputStream coreBlockByteOutputStream;
    private final BitOutputStream coreBlockBitOutputStream;

    // content ID to CRAMByteWriter
    private final Map<Integer, CRAMByteWriter> externalWriters = new TreeMap<>();

    /**
     * @param compressionHeader {@link CompressionHeader} for the container containing the slice
     */
    public SliceBlocksWriteStreams(final CompressionHeader compressionHeader) {
        this.compressionHeader = compressionHeader;

        // Core block still uses DefaultBitOutputStream (bit-level access needed for Huffman codecs)
        coreBlockByteOutputStream = new ByteArrayOutputStream();
        coreBlockBitOutputStream = new DefaultBitOutputStream(coreBlockByteOutputStream);

        // Create a writer for each external content ID in the encoding map
        for (final Integer contentID : compressionHeader.getEncodingMap().getExternalIDs()) {
            externalWriters.put(contentID, new CRAMByteWriter());
        }
    }

    /**
     * @return the {@link BitOutputStream} for the core block
     */
    public BitOutputStream getCoreOutputStream() { return coreBlockBitOutputStream; }

    /**
     * Get the {@link CRAMByteWriter} corresponding to the requested content ID.
     * @param contentID ID of content being requested
     * @return CRAMByteWriter for the content ID
     */
    public CRAMByteWriter getExternalWriter(final Integer contentID) { return externalWriters.get(contentID); }

    /**
     * Compress and write each stream to a corresponding Block.
     */
    public SliceBlocks flushStreamsToBlocks(final CRAMCodecModelContext contextModel) {
        closeCoreStream();

        // core block is raw (no compression) and must be written first (prescribed by the spec)
        final Block coreBlock = Block.createRawCoreDataBlock(coreBlockByteOutputStream.toByteArray());

        final List<Block> externalBlocks = new ArrayList<>();
        externalWriters.forEach((contentId, writer) -> {
            if (contentId.equals(Block.NO_CONTENT_ID)) {
                throw new CRAMException("A valid content ID is required.  Given: " + contentId);
            }
            externalBlocks.add(compressionHeader.getEncodingMap().createCompressedBlockForWriter(contextModel, contentId, writer));
         });

        return new SliceBlocks(coreBlock, externalBlocks);
     }

    private void closeCoreStream() {
        try {
            getCoreOutputStream().close();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }
}
