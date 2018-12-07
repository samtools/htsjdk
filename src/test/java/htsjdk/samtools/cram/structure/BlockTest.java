package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.structure.block.*;
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

        Assert.assertEquals(actual.getUncompressedContent(), expectedRaw);
        Assert.assertEquals(actual.getUncompressedContentSize(), expectedRaw.length);
        Assert.assertEquals(actual.getCompressedContent(), expectedCompressed);
        Assert.assertEquals(actual.getCompressedContentSize(), expectedCompressed.length);
    }

    private void rtCheck(final Block srcBlock, final byte[] testData, final Version[] versions) throws IOException {
        for (final Version version : versions) {
            final Block rtBlock = roundTrip(srcBlock, version);
            contentCheck(rtBlock, testData, testData);
        }
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

    private Block roundTrip(final Block in, final Version version) throws IOException {
        byte[] written;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            in.write(version.major, os);
            written = os.toByteArray();
        }

        try (final InputStream is = new ByteArrayInputStream(written)) {
            return Block.read(version.major, is);
        }
    }

    @DataProvider(name = "RoundTripTest")
    public static Object[][] rtProvider() {
        return new Object[][]{
                {"TEST STRING".getBytes(), new Version[]{CramVersions.CRAM_v2_1, CramVersions.CRAM_v3}}
        };
    }

    @Test(dataProvider = "RoundTripTest")
    public void testFileHeaderBlockRoundTrips(final byte[] testData, final Version[] versions) throws IOException {
        final Block fhBlock = Block.createRawFileHeaderBlock(testData);
        rtCheck(fhBlock, testData, versions);
    }

    @Test(dataProvider = "RoundTripTest")
    public void testCompressionHeaderBlockRoundTrips(final byte[] testData, final Version[] versions) throws IOException {
        final Block chBlock = Block.createRawCompressionHeaderBlock(testData);
        rtCheck(chBlock, testData, versions);
    }

    @Test(dataProvider = "RoundTripTest")
    public void testSliceHeaderBlockRoundTrips(final byte[] testData, final Version[] versions) throws IOException {
        final Block shBlock = Block.createRawSliceHeaderBlock(testData);
        rtCheck(shBlock, testData, versions);
    }

    @Test(dataProvider = "RoundTripTest")
    public void testCoreBlockRoundTrips(final byte[] testData, final Version[] versions) throws IOException {
        final Block coreBlock = Block.createRawCoreDataBlock(testData);
        rtCheck(coreBlock, testData, versions);
    }

    @Test
    public void testExternalBlockRoundTrips() throws IOException {
        // arbitrary values
        final ExternalCompressor compressor = ExternalCompressor.createGZIP();
        final int contentID = 5;

        final byte[] uncompressedData = "A TEST STRING WITH REDUNDANCY AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes();
        final byte[] compressedData = compressor.compress(uncompressedData);

        final Block extBlock = new ExternalBlock(compressor.getMethod(), contentID, compressedData, uncompressedData.length);

        final Block rtBlock2 = roundTrip(extBlock, CramVersions.CRAM_v2_1);
        contentCheck(rtBlock2, uncompressedData, compressedData);

        final Block rtBlock3 = roundTrip(extBlock, CramVersions.CRAM_v3);
        contentCheck(rtBlock3, uncompressedData, compressedData);
    }
}
