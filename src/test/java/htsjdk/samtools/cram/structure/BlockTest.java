package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.structure.block.*;
import htsjdk.samtools.util.RuntimeIOException;
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

        Assert.assertEquals(actual.getUncompressedContent(), expectedRaw);
        Assert.assertEquals(actual.getUncompressedContentSize(), expectedRaw.length);
        Assert.assertEquals(actual.getCompressedContent(), expectedCompressed);
        Assert.assertEquals(actual.getCompressedContentSize(), expectedCompressed.length);
    }

    @Test
    public void uncompressedTest() {
        final byte[] testData = "TEST STRING".getBytes();

        final Block fhBlock = Block.createRawFileHeaderBlock(testData);
        contentCheck(fhBlock, testData, testData);

        final Block chBlock = Block.createRawCompressionHeaderBlock(testData);
        contentCheck(chBlock, testData, testData);

        final Block shBlock = Block.createRawSliceHeaderBlock(testData);
        contentCheck(shBlock, testData, testData);

        final Block core = Block.createRawCoreDataBlock(testData);
        contentCheck(core, testData, testData);
    }

    private Block roundTrip(final Block in, final Version version) {
        byte[] written;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            in.write(version.major, os);
            written = os.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        try (final InputStream is = new ByteArrayInputStream(written)) {
            return Block.read(version.major, is);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Test
    public void testFileHeaderBlockRoundTrips() {
        final byte[] testData = "TEST STRING".getBytes();

        final Block fhBlock = Block.createRawFileHeaderBlock(testData);

        final Block rtBlock2 = roundTrip(fhBlock, CramVersions.CRAM_v2_1);
        contentCheck(rtBlock2, testData, testData);

        final Block rtBlock3 = roundTrip(fhBlock, CramVersions.CRAM_v3);
        contentCheck(rtBlock3, testData, testData);
    }

    @Test
    public void testCompressionHeaderBlockRoundTrips() {
        final byte[] testData = "TEST STRING".getBytes();

        final Block chBlock = Block.createRawCompressionHeaderBlock(testData);

        final Block rtBlock2 = roundTrip(chBlock, CramVersions.CRAM_v2_1);
        contentCheck(rtBlock2, testData, testData);

        final Block rtBlock3 = roundTrip(chBlock, CramVersions.CRAM_v3);
        contentCheck(rtBlock3, testData, testData);
    }

    @Test
    public void testSliceHeaderBlockRoundTrips() {
        final byte[] testData = "TEST STRING".getBytes();

        final Block shBlock = Block.createRawSliceHeaderBlock(testData);

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

        final Block extBlock = new ExternalDataBlock(contentID, compressor, uncompressedData);

        final Block rtBlock2 = roundTrip(extBlock, CramVersions.CRAM_v2_1);
        contentCheck(rtBlock2, uncompressedData, compressedData);

        final Block rtBlock3 = roundTrip(extBlock, CramVersions.CRAM_v3);
        contentCheck(rtBlock3, uncompressedData, compressedData);
    }

    @Test
    public void testCoreBlockRoundTrips() {
        final byte[] testData = "TEST STRING".getBytes();

        final Block coreBlock = Block.createRawCoreDataBlock(testData);

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
        new ExternalDataBlock(contentID, compressor, uncompressedData);
    }
}
