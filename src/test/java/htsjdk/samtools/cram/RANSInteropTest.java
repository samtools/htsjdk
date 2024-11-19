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
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * RANSInteropTest tests if the htsjdk RANS4x8 and RANSNx16 implementations are interoperable
 * with the htslib implementations. The test files for Interop tests are from the htslib repo.
 */
public class RANSInteropTest extends HtsjdkTest {
    public static final String COMPRESSED_RANS4X8_DIR = "r4x8";
    public static final String COMPRESSED_RANSNX16_DIR = "r4x16";

    // enumerates the different flag combinations
    public Object[][] get4x8RoundTripTestCases() throws IOException {

        // params:
        // uncompressed testfile path,
        // RANS encoder, RANS decoder, RANS params
        final List<RANSParams.ORDER> rans4x8ParamsOrderList = Arrays.asList(
                RANSParams.ORDER.ZERO,
                RANSParams.ORDER.ONE);
        final List<Object[]> testCases = new ArrayList<>();
        CRAMInteropTestUtils.getInteropRawTestFiles()
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
        CRAMInteropTestUtils.getInteropRawTestFiles()
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
        // RANS decoder
        final List<Object[]> testCases = new ArrayList<>();
        for (Path path : CRAMInteropTestUtils.getInteropCompressedFilePaths(COMPRESSED_RANS4X8_DIR)) {
            Object[] objects = new Object[]{
                    path,
                    CRAMInteropTestUtils.getUnCompressedFilePath(path),
                    new RANS4x8Decode()
            };
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    // uses the available compressed interop test files
    public Object[][] getNx16DecodeOnlyTestCases() throws IOException {

        // params:
        // compressed testfile path, uncompressed testfile path,
        // RANS decoder
        final List<Object[]> testCases = new ArrayList<>();
        for (Path path : CRAMInteropTestUtils.getInteropCompressedFilePaths(COMPRESSED_RANSNX16_DIR)) {
            Object[] objects = new Object[]{
                    path,
                    CRAMInteropTestUtils.getUnCompressedFilePath(path),
                    new RANSNx16Decode()
            };
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    @DataProvider(name = "roundTripTestCases")
    public Object[][] getRoundTripTestCases() throws IOException {

        // params:
        // uncompressed testfile path,
        // RANS encoder, RANS decoder, RANS params
        return Stream.concat(Arrays.stream(get4x8RoundTripTestCases()), Arrays.stream(getNx16RoundTripTestCases()))
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "decodeOnlyTestCases")
    public Object[][] getDecodeOnlyTestCases() throws IOException {

        // params:
        // compressed testfile path, uncompressed testfile path,
        // RANS decoder
        return Stream.concat(Arrays.stream(get4x8DecodeOnlyTestCases()), Arrays.stream(getNx16DecodeOnlyTestCases()))
                .toArray(Object[][]::new);
    }

    @Test (
            dataProvider = "roundTripTestCases",
            enabled = false,
            description = "Roundtrip using htsjdk RANS. Compare the output with the original file" )
    public void testRANSRoundTrip(
            final Path uncompressedFilePath,
            final RANSEncode<RANSParams> ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params) throws IOException {
        try (final InputStream uncompressedInteropStream = Files.newInputStream(uncompressedFilePath)) {

            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results
            final ByteBuffer uncompressedInteropBytes = CompressionUtils.wrap(CRAMInteropTestUtils.filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));

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
            enabled = false,
            description = "Uncompress the existing compressed file using htsjdk RANS and compare it with the original file.")
    public void testDecodeOnly(
            final Path compressedFilePath,
            final Path uncompressedInteropPath,
            final RANSDecode ransDecode) throws IOException {
        try (final InputStream uncompressedInteropStream = Files.newInputStream(uncompressedInteropPath);
             final InputStream preCompressedInteropStream = Files.newInputStream(compressedFilePath)
        ) {

            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results
            final ByteBuffer uncompressedInteropBytes = CompressionUtils.wrap(CRAMInteropTestUtils.filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));
            final ByteBuffer preCompressedInteropBytes = CompressionUtils.wrap(IOUtils.toByteArray(preCompressedInteropStream));

            // Use htsjdk to uncompress the precompressed file from htscodecs repo
            final ByteBuffer uncompressedHtsjdkBytes = ransDecode.uncompress(preCompressedInteropBytes);

            // Compare the htsjdk uncompressed bytes with the original input file from htscodecs repo
            Assert.assertEquals(uncompressedHtsjdkBytes, uncompressedInteropBytes);
        } catch (final NoSuchFileException ex){
            throw new SkipException("Skipping testDecodeOnly as either input file " +
                    "or precompressed file is missing.", ex);
        }
    }

}