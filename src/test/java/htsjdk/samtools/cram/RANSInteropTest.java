package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
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

import static htsjdk.samtools.cram.CRAMInteropTestUtils.filterEmbeddedNewlines;
import static htsjdk.samtools.cram.CRAMInteropTestUtils.getInteropCompressedFilePaths;
import static htsjdk.samtools.cram.CRAMInteropTestUtils.getParamsFormatFlags;
import static htsjdk.samtools.cram.CRAMInteropTestUtils.getUnCompressedFilePath;

/**
 * RANSInteropTest tests if the htsjdk RANS4x8 and RANSNx16 implementations are interoperable
 * with the htslib implementations. The test files for Interop tests is kept in a separate repository,
 * currently at https://github.com/samtools/htscodecs so it can be shared across htslib/samtools/htsjdk.
 *
 * For local development env, the Interop test files must be downloaded locally and made available at "../htscodecs/tests"
 * For CI env, the Interop test files are made available from the existing samtools installation
 * at "/samtools-1.14/htslib-1.14/htscodecs/tests"
 */
public class RANSInteropTest extends HtsjdkTest {
    public static final String COMPRESSED_RANS4X8_DIR = "r4x8";
    public static final String COMPRESSED_RANSNX16_DIR = "r4x16";

    // RANS4x8 codecs and testdata
    public Object[][] get4x8TestCases() throws IOException {

        // params:
        // compressed testfile path, uncompressed testfile path,
        // RANS encoder, RANS decoder, RANS params
        final List<Object[]> testCases = new ArrayList<>();
        for (Path path : getInteropCompressedFilePaths(COMPRESSED_RANS4X8_DIR)) {
            Object[] objects = new Object[]{
                    path,
                    getUnCompressedFilePath(path),
                    new RANS4x8Encode(),
                    new RANS4x8Decode(),
                    new RANS4x8Params(RANSParams.ORDER.fromInt(getParamsFormatFlags(path)))
            };
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    // RANSNx16 codecs and testdata
    public Object[][] getNx16TestCases() throws IOException {

        // params:
        // compressed testfile path, uncompressed testfile path,
        // RANS encoder, RANS decoder, RANS params
        final List<Object[]> testCases = new ArrayList<>();
        for (Path path : getInteropCompressedFilePaths(COMPRESSED_RANSNX16_DIR)) {
            Object[] objects = new Object[]{
                    path,
                    getUnCompressedFilePath(path),
                    new RANSNx16Encode(),
                    new RANSNx16Decode(),
                    new RANSNx16Params(getParamsFormatFlags(path))
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
        return Stream.concat(Arrays.stream(get4x8TestCases()), Arrays.stream(getNx16TestCases()))
                .toArray(Object[][]::new);
    }

    @Test(description = "Test if CRAM Interop Test Data is available")
    public void testHtsCodecsCorpusIsAvailable() {
        if (!CRAMInteropTestUtils.isInteropTestDataAvailable()) {
            throw new SkipException(String.format("RANS Interop Test Data is not available at %s",
                    CRAMInteropTestUtils.INTEROP_TEST_FILES_PATH));
        }
    }

    @Test (
            dependsOnMethods = "testHtsCodecsCorpusIsAvailable",
            dataProvider = "roundTripTestCases",
            description = "Roundtrip using htsjdk RANS. Compare the output with the original file" )
    public void testRANSRoundTrip(
            final Path unusedcompressedFilePath,
            final Path uncompressedFilePath,
            final RANSEncode<RANSParams> ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params) throws IOException {
        try (final InputStream uncompressedInteropStream = Files.newInputStream(uncompressedFilePath)) {

            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results
            final ByteBuffer uncompressedInteropBytes = ByteBuffer.wrap(filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));

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
            dependsOnMethods = "testHtsCodecsCorpusIsAvailable",
            dataProvider = "roundTripTestCases",
            description = "Uncompress the existing compressed file using htsjdk RANS and compare it with the original file.")
    public void testDecodeOnly(
            final Path compressedFilePath,
            final Path uncompressedInteropPath,
            final RANSEncode<RANSParams> unusedRansEncode,
            final RANSDecode ransDecode,
            final RANSParams unusedRansParams) throws IOException {
        try (final InputStream uncompressedInteropStream = Files.newInputStream(uncompressedInteropPath);
             final InputStream preCompressedInteropStream = Files.newInputStream(compressedFilePath)
        ) {

            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results
            final ByteBuffer uncompressedInteropBytes = ByteBuffer.wrap(filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));
            final ByteBuffer preCompressedInteropBytes = ByteBuffer.wrap(IOUtils.toByteArray(preCompressedInteropStream));

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