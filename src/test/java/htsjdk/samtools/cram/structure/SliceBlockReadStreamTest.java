package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

public class SliceBlockReadStreamTest extends HtsjdkTest {

    @Test(dataProvider="externalCompressionMethods", dataProviderClass = StructureTestUtils.class)
    public void testSliceBlocksReadStreamsRoundTrip(final BlockCompressionMethod compressionMethod) {

        // Write directly to blocks, and verify by reading through streams (SliceBlocksReadStreams)

        final byte[] coreBlockContent = "core".getBytes();
        final byte[] embeddedRefBlockContent = "ref".getBytes();
        final int embeddedRefBlockContentID = 293; // totally made it up
        final Map<Integer, String> expectedExternalContentStrings = new HashMap<>();

        // populate a SliceBlocksObject and update expectedExternalContent
        final SliceBlocks sliceBlocks = SliceBlocksTest.getSliceBlocksForAllDataSeries(
                compressionMethod,
                coreBlockContent,
                embeddedRefBlockContent,
                embeddedRefBlockContentID,
                expectedExternalContentStrings
        );

        final SliceBlocksReadStreams sliceBlocksReadStream = new SliceBlocksReadStreams(sliceBlocks, new CompressorCache());

        // "core" is a a bit stream, but interpret the bits as a 4 byte string for verification
        Assert.assertEquals(sliceBlocksReadStream.getCoreBlockInputStream().readBits(8), (int) coreBlockContent[0]);
        Assert.assertEquals(sliceBlocksReadStream.getCoreBlockInputStream().readBits(8), (int) coreBlockContent[1]);
        Assert.assertEquals(sliceBlocksReadStream.getCoreBlockInputStream().readBits(8), (int) coreBlockContent[2]);
        Assert.assertEquals(sliceBlocksReadStream.getCoreBlockInputStream().readBits(8), (int) coreBlockContent[3]);

        byte[] roundTrippedReferenceBlockContent = new byte[embeddedRefBlockContent.length];
        Assert.assertEquals(
                sliceBlocksReadStream
                        .getExternalInputStream(embeddedRefBlockContentID)
                        .read(roundTrippedReferenceBlockContent, 0, roundTrippedReferenceBlockContent.length),
                embeddedRefBlockContent.length);
        Assert.assertEquals(
                new String(roundTrippedReferenceBlockContent),
                new String(embeddedRefBlockContent));

        // and read back the content (name of the data series) from the stream and verify
        for (final DataSeries dataSeries : DataSeries.values()) {
            byte[] roundTrippedContent = dataSeries.getCanonicalName().getBytes();
            sliceBlocksReadStream
                    .getExternalInputStream(dataSeries.getExternalBlockContentId())
                    .read(roundTrippedContent, 0, roundTrippedContent.length);
            Assert.assertEquals(roundTrippedContent.length, dataSeries.getCanonicalName().length());
            Assert.assertEquals( new String(roundTrippedContent), expectedExternalContentStrings.get(dataSeries.getExternalBlockContentId()));
        }
    }
}
