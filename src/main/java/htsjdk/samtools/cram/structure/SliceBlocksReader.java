package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.structure.block.Block;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: SliceBlocksReader and SliceBlocksWriter should live in IO package ?
public class SliceBlocksReader {

    //TODO: unused ?
    private final SliceBlocks sliceBlocks;

    // content ID to ByteArrayOutputStream
    private final Map<Integer, ByteArrayInputStream> idToStream;
    private final BitInputStream coreBlockInputStream;

    public SliceBlocksReader(final SliceBlocks sliceBlocks) {
        this.sliceBlocks = sliceBlocks;
        coreBlockInputStream = new DefaultBitInputStream(new ByteArrayInputStream(sliceBlocks.getCoreBlock().getUncompressedContent()));

        idToStream = new HashMap<>();
        final List<Integer> externalContentIDs = sliceBlocks.getExternalContentIDs();
        for (final Integer contentID : externalContentIDs) {
            final Block block = sliceBlocks.get(contentID);
            idToStream.put(contentID, new ByteArrayInputStream(block.getUncompressedContent()));
        }
    }

    // TOTO: this should turn into a method that looks up a stream from an ID, to be used by the Data SeriesReader/Writer
    public Map<Integer, ByteArrayInputStream> getInputStreamMap() { return idToStream; }

    public BitInputStream getCoreBlockInputStream() {
        return coreBlockInputStream;
    }
}
