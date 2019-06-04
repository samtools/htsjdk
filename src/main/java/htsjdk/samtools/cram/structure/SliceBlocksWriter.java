package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

// TODO: SliceBlocksReader and SliceBlocksWriter should live in IO package ?
public class SliceBlocksWriter {

    private CompressionHeader compressionHeader;
    private final SliceBlocks sliceBlocks;

    // content ID to ByteArrayOutputStream
    private final Map<Integer, ByteArrayOutputStream> idToStream;
    private final ByteArrayOutputStream bitBAOS;
    private final BitOutputStream coreBlockOutputStream;

    public SliceBlocksWriter(final CompressionHeader compressionHeader, final SliceBlocks sliceBlocks) {
        this.compressionHeader = compressionHeader;
        this.sliceBlocks = sliceBlocks;
        //TODO: all these streams should be closed - make this Closeable ?
        //TODO: the encoding map doesn't have tag streams, so how/where does that get created ?
        idToStream = compressionHeader.getEncodingMap().createSliceOutputStreamMap();
        bitBAOS = new ByteArrayOutputStream();
        coreBlockOutputStream = new DefaultBitOutputStream(bitBAOS);
    }

    // TODO: still public since its used by DataSeriesWriter
    public BitOutputStream getCoreOutputStream() { return coreBlockOutputStream; }

    public void saveStreamsToBlocks() {
         close();
         sliceBlocks.setCoreBlock(Block.createRawCoreDataBlock(getCoreOuterOutputStream().toByteArray()));

         for (final Integer contentId : getOutputStreamMap().keySet()) {
             // remove after https://github.com/samtools/htsjdk/issues/1232
             if (contentId == Block.NO_CONTENT_ID) {
                 throw new CRAMException("Valid Content ID required.  Given: " + contentId);
             }

             final ExternalCompressor compressor = compressionHeader.getEncodingMap().getExternalCompresssors().get(contentId);
             final byte[] rawContent = getOutputStreamMap().get(contentId).toByteArray();
             final Block externalBlock = Block.createExternalBlock(
                     compressor.getMethod(),
                     contentId,
                     compressor.compress(rawContent),
                     rawContent.length);

             sliceBlocks.addExternalBlock(contentId, externalBlock);
         }
     }

    private ByteArrayOutputStream getCoreOuterOutputStream() { return bitBAOS; }

    public Map<Integer, ByteArrayOutputStream> getOutputStreamMap() { return idToStream; }

    private void close() {
        try {
            getCoreOutputStream().close();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

}
