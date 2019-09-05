package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Test
public class SliceBlocksTest  extends HtsjdkTest {

    @Test
    public void testSliceBlocksDefaults() {
        final SliceBlocks sliceBlocks = new SliceBlocks();
        Assert.assertNull(sliceBlocks.getCoreBlock());
        Assert.assertNull(sliceBlocks.getEmbeddedReferenceBlock());
        Assert.assertEquals(sliceBlocks.getEmbeddedReferenceContentID(), Block.NO_CONTENT_ID);
        Assert.assertEquals(sliceBlocks.getNumberOfExternalBlocks(), 0);
    }

    @Test(dataProvider="externalCompressionMethods", dataProviderClass = StructureTestUtils.class)
    public void testSliceBlocksRoundTrip(final BlockCompressionMethod compressionMethod) throws IOException {
        final byte[] coreBlockContent = "core".getBytes();
        final byte[] embeddedRefBlockContent = "ref".getBytes();
        final int embeddedRefBlockContentID = 293; // totally made it up; just has to not collide with any data series id
        final Map<Integer, String> expectedExternalContentStrings = new HashMap<>();

        // populate a SliceBlocksObject and populate expectedExternalContent
        final SliceBlocks sliceBlocks = getSliceBlocksForAllDataSeries(
                compressionMethod,
                coreBlockContent,
                embeddedRefBlockContent,
                embeddedRefBlockContentID,
                expectedExternalContentStrings
        );

        // Serialize and round trip the underlying blocks through a stream to a new SliceBlocks
        SliceBlocks roundTrippedSliceBlocks;
        final int expectedTotalNumberOfBlocks = DataSeries.values().length + 2;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            sliceBlocks.writeBlocks(CramVersions.CRAM_v3.major, baos);
            final byte[] roundTrippedBytes = baos.toByteArray();
            try (final InputStream is = new ByteArrayInputStream(roundTrippedBytes)) {
                roundTrippedSliceBlocks = new SliceBlocks();
                // simulate what happens when reading in a Slice from a CRAM stresm, which is that the
                // content ID of the embedded reference block (if any)  is discovered while reading the
                // slice header block, but before any other blocks are read, so tell the new SliceBLocks
                // object which block is the embedded reference
                roundTrippedSliceBlocks.setEmbeddedReferenceContentID(embeddedRefBlockContentID);
                roundTrippedSliceBlocks.readBlocks(
                        CramVersions.CRAM_v3.major,
                        expectedTotalNumberOfBlocks,
                        is);
            }
        }

        Assert.assertEquals(roundTrippedSliceBlocks.getCoreBlock().getUncompressedContent(new CompressorCache()), coreBlockContent);
        Assert.assertEquals(roundTrippedSliceBlocks.getEmbeddedReferenceContentID(), embeddedRefBlockContentID);
        Assert.assertEquals(
                roundTrippedSliceBlocks.getEmbeddedReferenceBlock().getUncompressedContent(new CompressorCache()),
                embeddedRefBlockContent);

        // we expect one external block for each data series, plus one for the embedded reference block
        Assert.assertEquals(roundTrippedSliceBlocks.getNumberOfExternalBlocks(),expectedExternalContentStrings.size() + 1);
        expectedExternalContentStrings.forEach(
                (id, content) -> Assert.assertEquals(
                        new String(roundTrippedSliceBlocks.getExternalBlock(id).getUncompressedContent(new CompressorCache())),
                        content)
        );
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectNonExternalExternalBlock() {
        final SliceBlocks sliceBlocks = new SliceBlocks();
        final Block block = Block.createRawCoreDataBlock(new byte[2]);
        sliceBlocks.addExternalBlock(block);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testRejectDuplicateExternalBlockIDs() {
        final SliceBlocks sliceBlocks = new SliceBlocks();
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, 27, new byte[2], 2);
        sliceBlocks.addExternalBlock(block);
        sliceBlocks.addExternalBlock(block);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectNonCoreCoreBlock() {
        final SliceBlocks sliceBlocks = new SliceBlocks();
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, 27, new byte[2], 2);
        sliceBlocks.setCoreBlock(block);
    }

    // Embedded reference block tests

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectEmbeddedReferenceBlockNoContentID() {
        final SliceBlocks sliceBlocks = new SliceBlocks();
        // this test is a little bogus in that, per the spec, it shouldn't even be possible to create an external block
        // with contentID=0 in the first place, but we allow it due to  https://github.com/samtools/htsjdk/issues/1232,
        // and because we have lots of CRAM files floating around that were generated this way
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, Block.NO_CONTENT_ID, new byte[2], 2);
        sliceBlocks.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectNonExternalEmbeddedReferenceBlock() {
        final SliceBlocks sliceBlocks = new SliceBlocks();
        final Block block = Block.createRawCoreDataBlock(new byte[2]);
        sliceBlocks.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectEmbeddedReferenceBlockConflictsWithID() {
        final int embeddedReferenceBlockContentID = 27;
        final SliceBlocks sliceBlocks = new SliceBlocks();
        sliceBlocks.setEmbeddedReferenceContentID(embeddedReferenceBlockContentID);
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, embeddedReferenceBlockContentID + 1, new byte[2], 2);
        sliceBlocks.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectResetEmbeddedReferenceBlock() {
        final SliceBlocks sliceBlocks = new SliceBlocks();
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, 27, new byte[2], 2);
        sliceBlocks.setEmbeddedReferenceBlock(block);
        sliceBlocks.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectResetEmbeddedReferenceBlockContentID() {
        final SliceBlocks sliceBlocks = new SliceBlocks();
        sliceBlocks.setEmbeddedReferenceContentID(27);
        sliceBlocks.setEmbeddedReferenceContentID(28);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectConflictingEmbeddedReferenceBlockContentID() {
        final SliceBlocks sliceBlocks = new SliceBlocks();
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, 27, new byte[2], 2);
        sliceBlocks.setEmbeddedReferenceContentID(28);
        sliceBlocks.setEmbeddedReferenceBlock(block);
    }

    // populate a SliceBlocksObject with an external block for each DataSeries, plus
    // core and embedded reference block content, and update expectedExternalContent
    static SliceBlocks getSliceBlocksForAllDataSeries(
            final BlockCompressionMethod compressionMethod,
            final byte[] coreBlockContent,
            final byte[] embeddedRefBlockContent,
            final int embeddedRefBlockContentID,
            final Map<Integer, String> expectedExternalContentStrings) {

        final SliceBlocks sliceBlocks = new SliceBlocks();

        // add a core block (technically, the core block is a bit stream, but for this test we'll have it
        // carry a stream of bits that are bytes we'll interpret as the bytes of a String)
        sliceBlocks.setCoreBlock(Block.createRawCoreDataBlock(coreBlockContent));

        // add an embedded reference block
        sliceBlocks.setEmbeddedReferenceBlock(
                Block.createExternalBlock(
                        compressionMethod,
                        embeddedRefBlockContentID,
                        // use a non-default RANS order (ORDER one) compressor to ensure that the order
                        // is round-tripped correctly
                        ExternalCompressor.getCompressorForMethod(
                                compressionMethod,
                                compressionMethod == BlockCompressionMethod.RANS ?
                                        1 :
                                        ExternalCompressor.NO_COMPRESSION_ARG).compress(embeddedRefBlockContent),
                        embeddedRefBlockContent.length));

        // add one external block for each Data Series
        for (final DataSeries dataSeries : DataSeries.values()) {
            final String uncompressedContent = dataSeries.getCanonicalName();
            sliceBlocks.addExternalBlock(
                    Block.createExternalBlock(
                            compressionMethod,
                            dataSeries.getExternalBlockContentId(),
                            ExternalCompressor.getCompressorForMethod(
                                    compressionMethod,
                                    ExternalCompressor.NO_COMPRESSION_ARG).compress(uncompressedContent.getBytes()),
                            dataSeries.getCanonicalName().getBytes().length));
            expectedExternalContentStrings.put(dataSeries.getExternalBlockContentId(), uncompressedContent);
        }

        return sliceBlocks;
    }

}
