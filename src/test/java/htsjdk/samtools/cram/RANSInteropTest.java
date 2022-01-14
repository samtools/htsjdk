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
import java.util.zip.GZIPInputStream;

/**
 * RANSInteropTest tests if the htsjdk RANS4x8 and RANSNx16 implementations are interoperable
 * with the htslib implementations. The test files for Interop tests are from the htslib repo.
 */
public class RANSInteropTest extends HtsjdkTest {
    public static final String COMPRESSED_RANS4X8_DIR = "rans4x8";
    public static final String COMPRESSED_RANSNX16_DIR = "ransNx16";

    // The cross product of all interop test files with all the various parameter combinations currently results in
    // about 80 test cases in total. Serially spinning up so many rans encoders/decoders can result in memory pressure
    // and heap space exhaustion in downstream tests when running the test suite in CI (this never happens in real life
    // because we always use a single encoder/decoder per CRAM reader/writer, for the same reason). To mitigate this
    // issue in CI, we reuse the same encoder and decoder instances for all tests in order to avoid excessive object
    // creation, but this depends on the fact that the tests are run serially.
    //
    // !!!Note that this precludes running these tests in PARALLEL!!!
    //
    private RANSEncode rans4x8Encoder = new RANS4x8Encode();
    private RANSDecode rans4x8Decoder = new RANS4x8Decode();
    private RANSNx16Encode ransNx16Encoder = new RANSNx16Encode();
    private RANSNx16Decode ransNx16Decoder = new RANSNx16Decode();

    public Object[][] get4x8RoundTripTestCases() throws IOException {
        // uncompressed testfile path, RANS encoder, RANS decoder, RANS params
        final List<RANSParams.ORDER> rans4x8ParamsOrderList = Arrays.asList(
                RANSParams.ORDER.ZERO,
                RANSParams.ORDER.ONE);
        final List<Object[]> testCases = new ArrayList<>();
        // opportunistically use ALL the raw test files in the interop directory, including ones intended to test
        // the other codecs; from the rans perspective they're just a stream of bits
        CRAMInteropTestUtils.getRawCRAMInteropTestFiles()
                .forEach(path ->
                        rans4x8ParamsOrderList.stream().map(rans4x8ParamsOrder -> new Object[]{
                                path,
                                rans4x8Encoder,
                                rans4x8Decoder,
                                new RANS4x8Params(rans4x8ParamsOrder)
                        }).forEach(testCases::add));
        return testCases.toArray(new Object[][]{});
    }

    // enumerates the different flag combinations
    public Object[][] getNx16RoundTripTestCases() throws IOException {
        // uncompressed testfile path, RANS encoder, RANS decoder, RANS params
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
        // opportunistically gets ALL the raw test files in the interop directory, including ones intended to test
        // the other codecs; from the rans perspective they're just a stream of bits
        CRAMInteropTestUtils.getRawCRAMInteropTestFiles()
                .forEach(path ->
                        ransNx16ParamsFormatFlagList.stream().map(ransNx16ParamsFormatFlag -> new Object[]{
                                path,
                                ransNx16Encoder,
                                ransNx16Decoder,
                                new RANSNx16Params(ransNx16ParamsFormatFlag)
                        }).forEach(testCases::add));
        return testCases.toArray(new Object[][]{});
    }

    public Object[][] get4x8DecodeOnlyTestCases() throws IOException {
        // compressed testfile path, uncompressed testfile path, RANS decoder
        final List<Object[]> testCases = new ArrayList<>();
        for (Path path : CRAMInteropTestUtils.getCRAMInteropCompressedPaths(COMPRESSED_RANS4X8_DIR)) {
            Object[] objects = new Object[]{
                    path,
                    CRAMInteropTestUtils.getUnCompressedPathForCompressedPath(path),
                    rans4x8Decoder
            };
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    public Object[][] getNx16DecodeOnlyTestCases() throws IOException {
        // compressed testfile path, uncompressed testfile path, RANS decoder
        final List<Object[]> testCases = new ArrayList<>();
        for (Path path : CRAMInteropTestUtils.getCRAMInteropCompressedPaths(COMPRESSED_RANSNX16_DIR)) {
            Object[] objects = new Object[]{
                    path,
                    CRAMInteropTestUtils.getUnCompressedPathForCompressedPath(path),
                    ransNx16Decoder
            };
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    @DataProvider(name = "roundTripTestCases")
    public Object[][] getRoundTripTestCases() throws IOException {
        // uncompressed testfile path, RANS encoder, RANS decoder, RANS params
        return Stream.concat(Arrays.stream(get4x8RoundTripTestCases()), Arrays.stream(getNx16RoundTripTestCases()))
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "decodeOnlyTestCases")
    public Object[][] getDecodeOnlyTestCases() throws IOException {
        // compressed testfile path, uncompressed testfile path, RANS decoder
        return Stream.concat(Arrays.stream(get4x8DecodeOnlyTestCases()), Arrays.stream(getNx16DecodeOnlyTestCases()))
                .toArray(Object[][]::new);
    }

    @Test (
            dataProvider = "roundTripTestCases",
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
            final ByteBuffer uncompressedInteropBytes =
                    CompressionUtils.wrap(
                            CRAMInteropTestUtils.filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream))
                    );

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
            description = "Uncompress the existing compressed file using htsjdk RANS and compare it with the original file.")
    public void testDecodeOnly(
            final Path compressedFilePath,
            final Path uncompressedInteropPath,
            final RANSDecode ransDecode) throws IOException {
        try (final InputStream uncompressedInteropStream =
                     new GZIPInputStream(Files.newInputStream(uncompressedInteropPath));
             final InputStream preCompressedInteropStream = Files.newInputStream(compressedFilePath)) {
            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results
            final ByteBuffer uncompressedInteropBytes =
                uncompressedInteropPath.toString().endsWith("u32" + CRAMInteropTestUtils.GZIP_SUFFIX) ?
                    ByteBuffer.wrap(IOUtils.toByteArray(uncompressedInteropStream)) :
                    ByteBuffer.wrap(CRAMInteropTestUtils.filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));
            final ByteBuffer preCompressedInteropBytes = CompressionUtils.wrap(IOUtils.toByteArray(preCompressedInteropStream));

            // Use htsjdk to uncompress the precompressed file from hts-spec repo
            final ByteBuffer uncompressedHtsjdkBytes = ransDecode.uncompress(preCompressedInteropBytes);

            // Compare the htsjdk uncompressed bytes with the original input file from htscodecs repo
            Assert.assertEquals(uncompressedHtsjdkBytes, uncompressedInteropBytes);
        } catch (final NoSuchFileException ex){
            throw new SkipException("Skipping testDecodeOnly as either input file " +
                    "or precompressed file is missing.", ex);
        }
    }

}