package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.io.BitOutputStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SliceBlockWriteStreamTest extends HtsjdkTest {

    @Test
    public void testSliceBlocksWriteStreamsRoundTrip() throws IOException {

        // Write directly to streams (sliceBlockWriteStreams), but verify by reading directly from blocks

        // one byte of alternating 1/0 bits to use as test data to write to the core stream
        final byte expectedCoreContent = (byte) 0xAA;
        final Map<Integer, String> expectedExternalContent = new HashMap<>();

        // use the default compression header, which uses a default encoding map, which only
        // encodes those data series htsjdk writes
        final CompressionHeader compressionHeader = new CompressionHeader();
        final SliceBlocksWriteStreams sliceBlocksWriteStreams = new SliceBlocksWriteStreams(compressionHeader);

        // write one byte of alternating 1/0 bits to the core stream
        final BitOutputStream coreOS = sliceBlocksWriteStreams.getCoreOutputStream();
        coreOS.write(expectedCoreContent, 8);

        // and write the name of each data series to the corresponding external stream
        for (final DataSeries dataSeries : DataSeries.values()) {
            if (!StructureTestUtils.DATASERIES_NOT_WRITTEN_BY_HTSJDK.contains(dataSeries)) {
                final String uncompressedContent = dataSeries.getCanonicalName();
                expectedExternalContent.put(dataSeries.getExternalBlockContentId(), uncompressedContent);
                sliceBlocksWriteStreams
                        .getExternalOutputStream(dataSeries.getExternalBlockContentId())
                        .write(uncompressedContent.getBytes());
            }
        }

        // close the streams and write them to compressed slice blocks
        final SliceBlocks sliceBlocks = sliceBlocksWriteStreams.flushStreamsToBlocks();

        // now verify all the blocks in Slice
        final byte[] coreRoundTripContent = sliceBlocks.getCoreBlock().getUncompressedContent(new CompressorCache());
        Assert.assertEquals(coreRoundTripContent.length, 1);
        Assert.assertEquals(coreRoundTripContent[0], expectedCoreContent);
        sliceBlocks.getExternalContentIDs()
                .stream()
                .forEach(id -> Assert.assertEquals(
                        new String(sliceBlocks.getExternalBlock(id).getUncompressedContent(new CompressorCache())),
                        expectedExternalContent.get(id)));
    }
}
