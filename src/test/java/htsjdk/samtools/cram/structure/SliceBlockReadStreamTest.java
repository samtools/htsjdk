package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

public class SliceBlockReadStreamTest extends HtsjdkTest {

    @Test(dataProvider="externalCompressionMethods", dataProviderClass = StructureTestUtils.class)
    public void testSliceBlocksReadStreamsRoundTrip(final BlockCompressionMethod compressionMethod) {

        // Write directly to blocks, and verify by reading through streams (SliceBlocksReadStreams)

        final byte[] coreContent = "core".getBytes();
        final byte[] refContent = "ref".getBytes();
        final int refContentID = 293; // totally made it up
        final Map<Integer, String> expectedExternalContent = new HashMap<>();

        final SliceBlocks sliceBlocks = new SliceBlocks();

        // Add a core block, and one external block for each Data Series. The core block is actually
        // a bit stream, but for this test we hijack it and write bits that we'll interpret as a String.
        sliceBlocks.setCoreBlock(Block.createRawCoreDataBlock(coreContent));
        sliceBlocks.setEmbeddedReferenceBlock(
                Block.createExternalBlock(
                        compressionMethod,
                        refContentID,
                        ExternalCompressor.getCompressorForMethod(compressionMethod, RANS.ORDER.ZERO).compress(refContent),
                        refContent.length));
        for (final DataSeries dataSeries : DataSeries.values()) {
            final String uncompressedContent = dataSeries.getCanonicalName();
            sliceBlocks.addExternalBlock(
                    Block.createExternalBlock(
                            compressionMethod,
                            dataSeries.getExternalBlockContentId(),
                            ExternalCompressor.getCompressorForMethod(compressionMethod, RANS.ORDER.ZERO).compress(uncompressedContent.getBytes()),
                            dataSeries.getCanonicalName().getBytes().length));
            expectedExternalContent.put(dataSeries.getExternalBlockContentId(), uncompressedContent);
        }

        final SliceBlocksReadStreams sliceBlocksReadStream = new SliceBlocksReadStreams(sliceBlocks);

        // "core" is a a bit stream, but interpret the bits as a 4 byte string for verification
        Assert.assertEquals(sliceBlocksReadStream.getCoreBlockInputStream().readBits(8), (int) coreContent[0]);
        Assert.assertEquals(sliceBlocksReadStream.getCoreBlockInputStream().readBits(8), (int) coreContent[1]);
        Assert.assertEquals(sliceBlocksReadStream.getCoreBlockInputStream().readBits(8), (int) coreContent[2]);
        Assert.assertEquals(sliceBlocksReadStream.getCoreBlockInputStream().readBits(8), (int) coreContent[3]);

        byte[] roundTrippedReferenceBlockContent = new byte[refContent.length];
        Assert.assertEquals(
                sliceBlocksReadStream
                        .getExternalInputStream(refContentID)
                        .read(roundTrippedReferenceBlockContent, 0, roundTrippedReferenceBlockContent.length),
                refContent.length);
        Assert.assertEquals(
                new String(roundTrippedReferenceBlockContent),
                new String(refContent));

        // and read back the content (name of the data series) from the stream and verify
        for (final DataSeries dataSeries : DataSeries.values()) {
            byte[] roundTrippedContent = dataSeries.getCanonicalName().getBytes();
            sliceBlocksReadStream
                    .getExternalInputStream(dataSeries.getExternalBlockContentId())
                    .read(roundTrippedContent, 0, roundTrippedContent.length);
            Assert.assertEquals(roundTrippedContent.length, dataSeries.getCanonicalName().length());
            Assert.assertEquals( new String(roundTrippedContent), expectedExternalContent.get(dataSeries.getExternalBlockContentId()));
        }
    }
}
