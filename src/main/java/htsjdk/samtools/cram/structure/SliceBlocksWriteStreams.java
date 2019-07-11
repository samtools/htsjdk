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
 * Provides a layer over a {@link SliceBlocks} object and acts as a bridge between the DataSeries codecs
 * and their underlying blocks when writing a CRAM stream by presenting a bit (core) or byte (external) stream
 * for each block.
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
     * @param contentID ID of content being requested
     * @return ByteArrayOutputStream for contentID
     */
    public ByteArrayOutputStream getExternalOutputStream(final Integer contentID) { return externalOutputStreams.get(contentID); }

    /**
     * Compress and write each block to a CRAM output stream.
     */
    public void writeStreamsToSliceBlocks() {
         closeAllStreams();

         // core block is raw (no compression) and must be written first (prescribed by the spec)
         sliceBlocks.setCoreBlock(Block.createRawCoreDataBlock(coreBlockByteOutputStream.toByteArray()));

         for (final Map.Entry<Integer, ByteArrayOutputStream> streamEntry : externalOutputStreams.entrySet()) {
             final Integer contentId = streamEntry.getKey();
             // remove after https://github.com/samtools/htsjdk/issues/1232
             if (streamEntry.getKey().equals(Block.NO_CONTENT_ID)) {
                 throw new CRAMException("A valid content ID is required.  Given: " + contentId);
             }

             final Block externalBlock = compressionHeader.getEncodingMap().createCompressedBlockForStream(contentId, streamEntry.getValue());
             sliceBlocks.addExternalBlock(externalBlock);
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
