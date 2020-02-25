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

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSliceBlocksRequireNonNullCore() {
        // use a raw block so we don't have to compress it before reading...
        final Block extBlock = Block.createExternalBlock(BlockCompressionMethod.RAW, 27, new byte[2], 2);
        // null core block...
        new SliceBlocks(null, Collections.singletonList(extBlock));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSliceBlocksRequireCoreCore() {
        // use a raw block so we don't have to compress it before reading...
        final Block extBlock = Block.createExternalBlock(BlockCompressionMethod.RAW, 27, new byte[2], 2);
        // throw when using a non-core block as a core block
        new SliceBlocks(extBlock, Collections.singletonList(extBlock));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSliceBlocksRequireNonNullExternalBlocks() {
        final Block coreBlock = Block.createRawCoreDataBlock(new byte[2]);
        // throw when using null external blocks
        new SliceBlocks(coreBlock, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSliceBlocksRequireNonOneExternalBlock() {
        final Block coreBlock = Block.createRawCoreDataBlock(new byte[2]);
        // throw when using a non-core block as a core block
        new SliceBlocks(coreBlock, Collections.emptyList());
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
            sliceBlocks.writeBlocks(CramVersions.CRAM_v3, baos);
            final byte[] roundTrippedBytes = baos.toByteArray();
            try (final InputStream is = new ByteArrayInputStream(roundTrippedBytes)) {
                roundTrippedSliceBlocks = new SliceBlocks(
                        CramVersions.CRAM_v3,
                        expectedTotalNumberOfBlocks,
                        is);
            }
        }

        Assert.assertEquals(roundTrippedSliceBlocks.getCoreBlock().getUncompressedContent(new CompressorCache()), coreBlockContent);
        Assert.assertEquals(
                roundTrippedSliceBlocks.getExternalBlock(embeddedRefBlockContentID).getUncompressedContent(new CompressorCache()),
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
        final Block block = Block.createRawCoreDataBlock(new byte[2]);
        new SliceBlocks(block, Collections.singletonList(block));
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testRejectDuplicateExternalBlockIDs() {
        final Block coreBlock = Block.createRawCoreDataBlock(new byte[2]);
        final Block extBlock = Block.createExternalBlock(BlockCompressionMethod.GZIP, 27, new byte[2], 2);
        final List<Block> extBlocks = Arrays.asList(extBlock, extBlock);
        new SliceBlocks(coreBlock, extBlocks);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectNonCoreCoreBlock() {
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, 27, new byte[2], 2);
        new SliceBlocks(block, Collections.emptyList());
    }

    // populate a SliceBlocksObject with an external block for each DataSeries, plus
    // core and embedded reference block content, and update expectedExternalContent
    static SliceBlocks getSliceBlocksForAllDataSeries(
            final BlockCompressionMethod compressionMethod,
            final byte[] coreBlockContent,
            final byte[] embeddedRefBlockContent,
            final int embeddedRefBlockContentID,
            final Map<Integer, String> expectedExternalContentStrings) {

        final ArrayList<Block> extBlocks = new ArrayList<>();

        // add an embedded reference block
        extBlocks.add(
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
            extBlocks.add(
                    Block.createExternalBlock(
                            compressionMethod,
                            dataSeries.getExternalBlockContentId(),
                            ExternalCompressor.getCompressorForMethod(
                                    compressionMethod,
                                    ExternalCompressor.NO_COMPRESSION_ARG).compress(uncompressedContent.getBytes()),
                            dataSeries.getCanonicalName().getBytes().length));
            expectedExternalContentStrings.put(dataSeries.getExternalBlockContentId(), uncompressedContent);
        }

        // include a core block (technically, the core block is a bit stream, but for this test we'll have it
        // carry a stream of bits that are bytes we'll interpret as the bytes of a String)
        return new SliceBlocks(
                Block.createRawCoreDataBlock(coreBlockContent),
                extBlocks
        );
    }

}
