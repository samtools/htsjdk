package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

//TODO: factor out common test code

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

        final byte[] coreContent = "core".getBytes();
        final Map<Integer, String> expectedExternalContent = new HashMap<>();

        final SliceBlocks sliceBlocks = new SliceBlocks();

        // add a core block, and one external block for each Data Series
        // technically, the core block is a bit stream, but for this test we can hijack it and
        // write a bit stream that we'll interpret as a String
        sliceBlocks.setCoreBlock(Block.createRawCoreDataBlock(coreContent));
        for (final DataSeries dataSeries : DataSeries.values()) {
            final String uncompressedContent = dataSeries.getCanonicalName();
            sliceBlocks.addExternalBlock(
                    Block.createExternalBlock(
                            compressionMethod,
                            dataSeries.getExternalBlockContentId(),
                            StructureTestUtils.getCompressorForMethod(compressionMethod).compress(uncompressedContent.getBytes()),
                            dataSeries.getCanonicalName().getBytes().length));
            expectedExternalContent.put(dataSeries.getExternalBlockContentId(), uncompressedContent);
        }

        // ...round trip the SliceBlocks through a stream
        SliceBlocks roundTrippedSliceBlocks;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            sliceBlocks.writeBlocks(CramVersions.CRAM_v3.major, baos);
            final byte[] roundTrippedBytes = baos.toByteArray();
            try (final InputStream is = new ByteArrayInputStream(roundTrippedBytes)) {
                roundTrippedSliceBlocks = new SliceBlocks();
                roundTrippedSliceBlocks.readBlocks(CramVersions.CRAM_v3.major, DataSeries.values().length + 1, is);
            }
        }

        Assert.assertEquals(roundTrippedSliceBlocks.getNumberOfExternalBlocks(), DataSeries.values().length);

        // ensure the round-tripped set of content IDs matches the original. since ordering is not necessarily
        // preserved, use a Set
        final Set<Integer> externalIDs = new HashSet(roundTrippedSliceBlocks.getExternalContentIDs());
        final Set<Integer> expectedExternalIDs = new HashSet(
                Arrays.asList(DataSeries.values())
                        .stream()
                        .map(DataSeries::getExternalBlockContentId)
                        .collect(Collectors.toSet()));
        Assert.assertEquals(externalIDs, expectedExternalIDs);

        // and the round tripped block content matches the original
        final Map<Integer, String> externalContent = new HashMap<>();
        externalIDs.forEach(i -> externalContent.put(i, new String(sliceBlocks.getExternalBlock(i).getUncompressedContent())));
        Assert.assertEquals(externalContent, expectedExternalContent);
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

}
