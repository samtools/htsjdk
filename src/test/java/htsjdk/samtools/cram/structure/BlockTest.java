package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.compression.ExternalCompression;
import htsjdk.samtools.cram.structure.block.*;
import org.testng.Assert;
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

        Assert.assertEquals(actual.getRawContent(), expectedRaw);
        Assert.assertEquals(actual.getRawContentSize(), expectedRaw.length);
        Assert.assertEquals(actual.getCompressedContent(), expectedCompressed);
        Assert.assertEquals(actual.getCompressedContentSize(), expectedCompressed.length);

        // but they are defensive copies, not the same references

        Assert.assertNotSame(actual.getRawContent(), expectedRaw);
        Assert.assertNotSame(actual.getCompressedContent(), expectedCompressed);
    }

    @Test
    public void rawBlockTest() {
        // arbitrary
        final BlockContentType type = BlockContentType.CORE;
        final byte[] testData = "TEST STRING".getBytes();

        final Block block = new RawBlock(type, testData);
        contentCheck(block, testData, testData);
    }

    @Test
    public void compressibleBlockTest() throws IOException {
        // arbitrary values
        final BlockCompressionMethod method = BlockCompressionMethod.GZIP;
        final BlockContentType type = BlockContentType.EXTERNAL;
        final int contentID = 5;

        final byte[] uncompressedData = "A TEST STRING WITH REDUNDANCY AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes();
        final byte[] compressedData = ExternalCompression.gzip(uncompressedData);

        final Block block = new CompressibleBlock(method, type, contentID, uncompressedData, compressedData);
        contentCheck(block, uncompressedData, compressedData);
    }

    // compressible does not necessarily mean that it's using compression
    // test that it works in raw mode

    @Test
    public void compressibleBlockAsRawTest() {
        final BlockCompressionMethod method = BlockCompressionMethod.RAW;
        final BlockContentType type = BlockContentType.CORE;    // arbitrary
        final int contentID = Block.NO_CONTENT_ID;
        final byte[] testData = "TEST STRING".getBytes();

        final CompressibleBlock block = new CompressibleBlock(method, type, contentID, testData, testData);
        contentCheck(block, testData, testData);
    }

    private Block roundTrip(final Block in, final Version version) {
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            in.write(version.major, os);

            try (final InputStream is = new ByteArrayInputStream(os.toByteArray())) {
                return Block.read(version.major, is);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testFileHeaderBlockRoundTrips() {
        final byte[] testData = "TEST STRING".getBytes();

        final Block fhBlock = Block.buildNewFileHeaderBlock(testData);

        final Block rtBlock2 = roundTrip(fhBlock, CramVersions.CRAM_v2_1);
        contentCheck(rtBlock2, testData, testData);

        final Block rtBlock3 = roundTrip(fhBlock, CramVersions.CRAM_v3);
        contentCheck(rtBlock3, testData, testData);
    }

    @Test
    public void testCompressionHeaderBlockRoundTrips() {
        final byte[] testData = "TEST STRING".getBytes();

        final Block chBlock = Block.buildNewCompressionHeaderBlock(testData);

        final Block rtBlock2 = roundTrip(chBlock, CramVersions.CRAM_v2_1);
        contentCheck(rtBlock2, testData, testData);

        final Block rtBlock3 = roundTrip(chBlock, CramVersions.CRAM_v3);
        contentCheck(rtBlock3, testData, testData);
    }

    @Test
    public void testSliceHeaderBlockRoundTrips() {
        final byte[] testData = "TEST STRING".getBytes();

        final Block shBlock = Block.buildNewSliceHeaderBlock(testData);

        final Block rtBlock2 = roundTrip(shBlock, CramVersions.CRAM_v2_1);
        contentCheck(rtBlock2, testData, testData);

        final Block rtBlock3 = roundTrip(shBlock, CramVersions.CRAM_v3);
        contentCheck(rtBlock3, testData, testData);
    }

    @Test
    public void testExternalBlockRoundTrips() {
        // arbitrary values
        final ExternalCompressor compressor = ExternalCompressor.createGZIP();
        final int contentID = 5;

        final byte[] uncompressedData = "A TEST STRING WITH REDUNDANCY AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes();
        final byte[] compressedData = compressor.compress(uncompressedData);

        final Block extBlock = Block.buildNewExternalBlock(contentID, compressor, uncompressedData);

        final Block rtBlock2 = roundTrip(extBlock, CramVersions.CRAM_v2_1);
        contentCheck(rtBlock2, uncompressedData, compressedData);

        final Block rtBlock3 = roundTrip(extBlock, CramVersions.CRAM_v3);
        contentCheck(rtBlock3, uncompressedData, compressedData);
    }

    @Test
    public void testCoreBlockRoundTrips() {
        final byte[] testData = "TEST STRING".getBytes();

        final Block coreBlock = Block.buildNewCoreBlock(testData);

        final Block rtBlock2 = roundTrip(coreBlock, CramVersions.CRAM_v2_1);
        contentCheck(rtBlock2, testData, testData);

        final Block rtBlock3 = roundTrip(coreBlock, CramVersions.CRAM_v3);
        contentCheck(rtBlock3, testData, testData);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testExternalBlockContentId() {
        // arbitrary values
        final ExternalCompressor compressor = ExternalCompressor.createGZIP();
        final byte[] uncompressedData = "A TEST STRING".getBytes();

        // not allowed for external
        final int contentID = Block.NO_CONTENT_ID;
        Block.buildNewExternalBlock(contentID, compressor, uncompressedData);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testNonExternalBlockContentId() {
        // arbitrary values
        final BlockCompressionMethod method = BlockCompressionMethod.RAW;
        final BlockContentType type = BlockContentType.CORE;
        final byte[] testData = "TEST STRING".getBytes();

        // only allowed for external
        final int contentID = 1;
        new CompressibleBlock(method, type, contentID, testData, testData);
    }

}
