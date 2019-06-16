package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manage the set of streams used to hold block content when constructing a CRAM stream to be written
 */
public class SliceBlocksWriteStreams {

    private final CompressionHeader compressionHeader;
    private final SliceBlocks sliceBlocks;

    private final ByteArrayOutputStream coreBlockByteOutputStream;
    private final BitOutputStream coreBlockBitOutputStream;

    // content ID to ByteArrayOutputStream
    private final Map<Integer, ByteArrayOutputStream> externalOutputStreams = new HashMap<>();

    /**
     * @param compressionHeader {@link CompressionHeader} for the container containing the slice
     * @param sliceBlocks {@link SliceBlocks} for the represented by these blocks {@link Slice}
     */
    //TODO: add tag streams
    public SliceBlocksWriteStreams(final CompressionHeader compressionHeader, final SliceBlocks sliceBlocks) {
        this.compressionHeader = compressionHeader;
        this.sliceBlocks = sliceBlocks;

        coreBlockByteOutputStream = new ByteArrayOutputStream();
        coreBlockBitOutputStream = new DefaultBitOutputStream(coreBlockByteOutputStream);

        // Create an output stream for each external content ID in the encoding map
        for (final Integer contentID : compressionHeader.getEncodingMap().getExternalIDs()) {
            externalOutputStreams.put(contentID, new ByteArrayOutputStream());
        }
    }

    /**
     * @return the {@link BitOutputStream}  for the core block
     */
    public BitOutputStream getCoreOutputStream() { return coreBlockBitOutputStream; }

    /**
     * Get the ByteArrayOutputStream corresponding to the requested contentID
     * @param contentID ID of content being requuested
     * @return ByteArrayOutputStream for contentID
     */
    public ByteArrayOutputStream getExternalOutputStream(final Integer contentID) { return externalOutputStreams.get(contentID); }

    /**
     * Compress and write each block to a CRAM output stream.
     */
    public void writeStreamsToBlocks() {
         closeAllStreams();
         // core block is raw (no compression)
         sliceBlocks.setCoreBlock(Block.createRawCoreDataBlock(coreBlockByteOutputStream.toByteArray()));

         for (final Map.Entry<Integer, ByteArrayOutputStream> streamEntry : externalOutputStreams.entrySet()) {
             final Integer contentId = streamEntry.getKey();
             // remove after https://github.com/samtools/htsjdk/issues/1232
             if (streamEntry.getKey().equals(Block.NO_CONTENT_ID)) {
                 throw new CRAMException("A valid content ID is required.  Given: " + contentId);
             }

             final Block externalBlock = compressionHeader.getEncodingMap().getCompressedBlockForStream(contentId, streamEntry.getValue());
             sliceBlocks.addExternalBlock(contentId, externalBlock);
         }
     }

    private void closeAllStreams() {
        try {
            getCoreOutputStream().close();
            for (ByteArrayOutputStream baos : externalOutputStreams.values()) {
                baos.close();
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

}
