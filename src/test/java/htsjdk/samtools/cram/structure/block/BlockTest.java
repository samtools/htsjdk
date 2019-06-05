package htsjdk.samtools.cram.structure.block;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.compression.GZIPExternalCompressor;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.cram.structure.CompressorCache;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class BlockTest extends HtsjdkTest {

    private void contentCheck(final Block actual,
                             final byte[] expectedRaw,
                             final byte[] expectedCompressed) {

        // raw and compressed data are equal to what was given

        Assert.assertEquals(actual.getUncompressedContent(new CompressorCache()), expectedRaw);
        Assert.assertEquals(actual.getUncompressedContentSize(), expectedRaw.length);
        Assert.assertEquals(actual.getCompressedContent(), expectedCompressed);
        Assert.assertEquals(actual.getCompressedContentSize(), expectedCompressed.length);
    }

    @Test
    public void uncompressedTest() {
        final byte[] testData = "TEST STRING".getBytes();

        final Block chBlock = Block.createRawCompressionHeaderBlock(testData);
        contentCheck(chBlock, testData, testData);

        final Block shBlock = Block.createRawSliceHeaderBlock(testData);
        contentCheck(shBlock, testData, testData);

        final Block core = Block.createRawCoreDataBlock(testData);
        contentCheck(core, testData, testData);
    }

    private Block roundTrip(final Block in, final CRAMVersion cramVersion) throws IOException {
        byte[] written;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            in.write(cramVersion, os);
            written = os.toByteArray();
        }

        try (final InputStream is = new ByteArrayInputStream(written)) {
            return Block.read(cramVersion, is);
        }
    }

    @DataProvider(name = "RoundTripTest")
    public static Object[][] rtProvider() {
        return new Object[][]{
                {"TEST STRING".getBytes(), CramVersions.CRAM_v2_1},
                {"TEST STRING".getBytes(), CramVersions.CRAM_v3}
        };
    }

    @Test(dataProvider = "RoundTripTest")
    public void testFileHeaderBlockRoundTrips(final byte[] testData, final CRAMVersion cramVersion) throws IOException {
        final Block fhBlock = Block.createGZIPFileHeaderBlock(testData);
        final Block rtBlock = roundTrip(fhBlock, cramVersion);
        contentCheck(rtBlock, testData, (new GZIPExternalCompressor()).compress(testData));
    }

    @Test(dataProvider = "RoundTripTest")
    public void testCompressionHeaderBlockRoundTrips(final byte[] testData, final CRAMVersion cramVersion) throws IOException {
        final Block chBlock = Block.createRawCompressionHeaderBlock(testData);
        final Block rtBlock = roundTrip(chBlock, cramVersion);
        contentCheck(rtBlock, testData, testData);
    }

    @Test(dataProvider = "RoundTripTest")
    public void testSliceHeaderBlockRoundTrips(final byte[] testData, final CRAMVersion cramVersion) throws IOException {
        final Block shBlock = Block.createRawSliceHeaderBlock(testData);
        final Block rtBlock = roundTrip(shBlock, cramVersion);
        contentCheck(rtBlock, testData, testData);
    }

    @Test(dataProvider = "RoundTripTest")
    public void testCoreBlockRoundTrips(final byte[] testData, final CRAMVersion cramVersion) throws IOException {
        final Block coreBlock = Block.createRawCoreDataBlock(testData);
        final Block rtBlock = roundTrip(coreBlock, cramVersion);
        contentCheck(rtBlock, testData, testData);
    }

    @Test
    public void testExternalBlockRoundTrips() throws IOException {
        // arbitrary values
        final ExternalCompressor compressor = new GZIPExternalCompressor(new CRAMEncodingStrategy().getGZIPCompressionLevel());
        final int contentID = 5;

        final byte[] uncompressedData = "A TEST STRING WITH REDUNDANCY AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes();
        final byte[] compressedData = compressor.compress(uncompressedData);

        final Block extBlock = Block.createExternalBlock(compressor.getMethod(), contentID, compressedData, uncompressedData.length);

        final Block rtBlock2 = roundTrip(extBlock, CramVersions.CRAM_v2_1);
        contentCheck(rtBlock2, uncompressedData, compressedData);

        final Block rtBlock3 = roundTrip(extBlock, CramVersions.CRAM_v3);
        contentCheck(rtBlock3, uncompressedData, compressedData);
    }

    @DataProvider(name = "nonExternalTypes")
    private Object[][] nonExternalTypes() {
        return new Object[][] {
                {BlockContentType.COMPRESSION_HEADER},
                {BlockContentType.CORE},
                {BlockContentType.MAPPED_SLICE},
                {BlockContentType.FILE_HEADER},
                {BlockContentType.RESERVED},
        };
    }

    // show that we can build all non-external Block types with NO_CONTENT_ID
    // but we can't if they have other values

    @Test(dataProvider = "nonExternalTypes")
    public void nonExternalContentId(final BlockContentType contentType) {
        new Block(BlockCompressionMethod.RAW, contentType, Block.NO_CONTENT_ID, new byte[0], 0);
    }

    // show that non-external content types cannot have valid contentIds

    @Test(dataProvider = "nonExternalTypes", expectedExceptions = CRAMException.class)
    public void nonExternalContentIdNegative(final BlockContentType contentType) {
        final int VALID_CONTENT_ID = 1;
        new Block(BlockCompressionMethod.RAW, contentType, VALID_CONTENT_ID, new byte[0], 0);
    }
}
