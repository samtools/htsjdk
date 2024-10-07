package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.rans.RANSDecode;
import htsjdk.samtools.cram.compression.rans.RANSEncode;
import htsjdk.samtools.cram.compression.rans.RANSParams;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Decode;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Encode;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Params;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Decode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Encode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Params;
import org.apache.commons.compress.utils.IOUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Verify that the htsjdk RANS4x8 and RANSNx16 implementations are interoperable
 * with the htslib implementations. The test files for Interop tests originate a separate repository,
 * currently at https://github.com/samtools/htscodecs, but are checked into this repo as well.
 */
public class RANSInteropTest extends HtsjdkTest {
    // The precompressed interop files are versions of the raw test files that are located
    // in the interop test data directory that have been precompressed using James B.'s htscodec code.
    public static final String PRE_COMPRESSED_RANS4X8_DIR = "r4x8"; // precompressed interop files
    public static final String PRE_COMPRESSED_RANSNX16_DIR = "r4x16";  // precompressed interop files

    // enumerates the different flag combinations
    public Object[][] get4x8RoundTripTestCases() throws IOException {

        // params:
        // uncompressed testfile path,
        // RANS encoder, RANS decoder, RANS params
        final List<RANSParams.ORDER> rans4x8ParamsOrderList = Arrays.asList(
                RANSParams.ORDER.ZERO,
                RANSParams.ORDER.ONE);
        final List<Object[]> testCases = new ArrayList<>();
        getInteropRawTestFiles()
                .forEach(path ->
                        rans4x8ParamsOrderList.stream().map(rans4x8ParamsOrder -> new Object[]{
                                path,
                                new RANS4x8Encode(),
                                new RANS4x8Decode(),
                                new RANS4x8Params(rans4x8ParamsOrder)
                        }).forEach(testCases::add));
        return testCases.toArray(new Object[][]{});
    }

    // enumerates the different flag combinations
    public Object[][] getNx16RoundTripTestCases() throws IOException {

        // params:
        // uncompressed testfile path,
        // RANS encoder, RANS decoder, RANS params
        final List<Integer> ransNx16ParamsFormatFlagList = Arrays.asList(
                0x00,
                RANSNx16Params.ORDER_FLAG_MASK,
                RANSNx16Params.N32_FLAG_MASK,
                RANSNx16Params.N32_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
                RANSNx16Params.CAT_FLAG_MASK,
                RANSNx16Params.CAT_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
                RANSNx16Params.CAT_FLAG_MASK | RANSNx16Params.N32_FLAG_MASK,
                RANSNx16Params.CAT_FLAG_MASK | RANSNx16Params.N32_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
                RANSNx16Params.RLE_FLAG_MASK,
                RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
                RANSNx16Params.PACK_FLAG_MASK,
                RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
                RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.PACK_FLAG_MASK,
                RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK);
        final List<Object[]> testCases = new ArrayList<>();
        getInteropRawTestFiles()
                .forEach(path ->
                        ransNx16ParamsFormatFlagList.stream().map(ransNx16ParamsFormatFlag -> new Object[]{
                                path,
                                new RANSNx16Encode(),
                                new RANSNx16Decode(),
                                new RANSNx16Params(ransNx16ParamsFormatFlag)
                        }).forEach(testCases::add));
        return testCases.toArray(new Object[][]{});
    }

    // uses the available compressed interop test files
    public Object[][] get4x8DecodeOnlyTestCases() throws IOException {

        // params:
        // compressed testfile path, uncompressed testfile path,
        // RANS encoder, RANS decoder, RANS params
        final List<Object[]> testCases = new ArrayList<>();
        for (final Path path : CRAMInteropTestUtils.getInteropCompressedFilePaths(PRE_COMPRESSED_RANS4X8_DIR)) {
            Object[] objects = new Object[]{
                    path,
                    CRAMInteropTestUtils.getUnCompressedFilePath(path),
                    new RANS4x8Encode(),
                    new RANS4x8Decode(),
                    new RANS4x8Params(RANSParams.ORDER.fromInt(CRAMInteropTestUtils.getParamsFormatFlags(path)))
            };
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    // uses the available compressed interop test files
    public Object[][] getNx16DecodeOnlyTestCases() throws IOException {

        // params:
        // compressed testfile path, uncompressed testfile path,
        // RANS encoder, RANS decoder, RANS params
        final List<Object[]> testCases = new ArrayList<>();
        for (final Path path : CRAMInteropTestUtils.getInteropCompressedFilePaths(PRE_COMPRESSED_RANSNX16_DIR)) {
            Object[] objects = new Object[]{
                    path,
                    CRAMInteropTestUtils.getUnCompressedFilePath(path),
                    new RANSNx16Encode(),
                    new RANSNx16Decode(),
                    new RANSNx16Params(CRAMInteropTestUtils.getParamsFormatFlags(path))
            };
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    @DataProvider(name = "roundTripTestCases")
    public Object[][] getRoundTripTestCases() throws IOException {

        // params:
        // compressed testfile path, uncompressed testfile path,
        // RANS encoder, RANS decoder, RANS params
        return Stream.concat(Arrays.stream(get4x8RoundTripTestCases()), Arrays.stream(getNx16RoundTripTestCases()))
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "decodeOnlyTestCases")
    public Object[][] getDecodeOnlyTestCases() throws IOException {

        // params:
        // compressed testfile path, uncompressed testfile path,
        // RANS encoder, RANS decoder, RANS params
        return Stream.concat(Arrays.stream(get4x8DecodeOnlyTestCases()), Arrays.stream(getNx16DecodeOnlyTestCases()))
                .toArray(Object[][]::new);
    }

    @Test (
            dataProvider = "roundTripTestCases",
            description = "Roundtrip using htsjdk RANS. Compare the round tripped output with the original uncompressed file" )
    public void testRANSRoundTrip(
            final Path uncompressedFilePath,
            final RANSEncode<RANSParams> ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params) throws IOException {
        try (final InputStream uncompressedInteropStream = Files.newInputStream(uncompressedFilePath)) {

            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results
            final ByteBuffer uncompressedInteropBytes = CompressionUtils.wrap(
                    CRAMInteropTestUtils.filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));

            // Stripe Flag is not implemented in RANSNx16 Encoder.
            // The encoder throws CRAMException if Stripe Flag is used.
            if (params instanceof RANSNx16Params && ((RANSNx16Params) params).isStripe()) {
                Assert.assertThrows(CRAMException.class, () -> ransEncode.compress(uncompressedInteropBytes, params));
            } else {
                final ByteBuffer compressedHtsjdkBytes = ransEncode.compress(uncompressedInteropBytes, params);
                uncompressedInteropBytes.rewind();
                Assert.assertEquals(ransDecode.uncompress(compressedHtsjdkBytes), uncompressedInteropBytes);
            }
        }
    }

    @Test (
            dataProvider = "decodeOnlyTestCases",
            description = "Uncompress the pre-compressed test file using htsjdk RANS, and compare it with theoriginal  uncompressed test file.")
    public void testRANSDecodeOnly(
            final Path preCompressedPath,
            final Path uncompressedPath,
            final RANSEncode<RANSParams> unusedRansEncode,
            final RANSDecode ransDecode,
            final RANSParams unusedRansParams) throws IOException {
        try (final InputStream uncompressedStream = Files.newInputStream(uncompressedPath);
             final InputStream preCompressedStream = Files.newInputStream(preCompressedPath)
        ) {

            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results
            final ByteBuffer uncompressedBytes = CompressionUtils.wrap(CRAMInteropTestUtils.filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedStream)));
            final ByteBuffer preCompressedBytes = CompressionUtils.wrap(IOUtils.toByteArray(preCompressedStream));

            // Use htsjdk to uncompress the precompressed file from htscodecs repo
            final ByteBuffer uncompressedHtsjdkBytes = ransDecode.uncompress(preCompressedBytes);

            // Compare the htsjdk uncompressed bytes with the original input file from htscodecs repo
            Assert.assertEquals(uncompressedHtsjdkBytes, uncompressedBytes);
        }
    }

    // return a list of all raw test files in the htscodecs/tests/dat directory
    private List<Path> getInteropRawTestFiles() throws IOException {
        final List<Path> paths = new ArrayList<>();
        Files.newDirectoryStream(
                        CRAMInteropTestUtils.getInteropTestDataLocation().resolve("dat"),
                        path -> (Files.isRegularFile(path)) && !Files.isHidden(path))
                .forEach(path -> paths.add(path));
        return paths;
    }

}