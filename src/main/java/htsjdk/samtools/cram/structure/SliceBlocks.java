package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.structure.block.Block;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SliceBlocks {

    private Block coreBlock;
    // content ID to external block
    private Map<Integer, Block> externalBlocks = new HashMap<>();

    public SliceBlocks() {
    }

    public void setCoreBlock(final Block coreBlock) {
        this.coreBlock = coreBlock;
    }

    public Block getCoreBlock() { return coreBlock; }

    public List<Integer> getExternalContentIDs() {
        final List<Integer> contentIDs = new ArrayList<>(externalBlocks.size());
        externalBlocks.forEach((i, b) -> contentIDs.add(i));
        return contentIDs;
    }

    public int getNumberOfExternalBlocks() {
        return externalBlocks.size();
    }

    public void addExternalBlock(final Integer contentId, final Block externalBlock) {
        externalBlocks.put(contentId, externalBlock);
    }

    public Block get(final Integer contentID) {
        return externalBlocks.get(contentID);
    }

    // TODO: should this method write Core block ?
    public void write(final int major, final OutputStream outputStream) {
        coreBlock.write(major, outputStream);
        for (final Block block : externalBlocks.values())
            block.write(major, outputStream);
    }

    public Map<Integer, ByteArrayInputStream> createSliceInputStreamMap() {
        return externalBlocks.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new ByteArrayInputStream(e.getValue().getUncompressedContent())));
    }
}
