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

import java.io.ByteArrayOutputStream;
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
        for (Path path : getInteropRansCompressedFilePaths(COMPRESSED_RANS4X8_DIR)) {
            Object[] objects = new Object[]{
                    path,
                    getRansUnCompressedFilePath(path),
                    new RANS4x8Encode(),
                    new RANS4x8Decode(),
                    getRans4x8Params(path)
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
        for (Path path : getInteropRansCompressedFilePaths(COMPRESSED_RANSNX16_DIR)) {
            Object[] objects = new Object[]{
                    path,
                    getRansUnCompressedFilePath(path),
                    new RANSNx16Encode(),
                    new RANSNx16Decode(),
                    getRansNx16Params(path)
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
            if (params instanceof RANSNx16Params){
                RANSNx16Params ransNx16Params = (RANSNx16Params) params;
                if (ransNx16Params.isStripe()) {
                    Assert.assertThrows(CRAMException.class, () -> ransEncode.compress(uncompressedInteropBytes, params));
                }
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
            throw new SkipException("Skipping testRANSPrecompressed as either input file " +
                    "or precompressed file is missing.", ex);
        }
    }

    // the input files have embedded newlines that the test remove before round-tripping...
    private final byte[] filterEmbeddedNewlines(final byte[] rawBytes) throws IOException {
        // 1. filters new lines if any.
        // 2. "q40+dir" file has an extra column delimited by tab. This column provides READ1 vs READ2 flag.
        //     This file is also new-line separated. The extra column, '\t' and '\n' are filtered.
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int skip = 0;
            for (final byte b : rawBytes) {
                if (b == '\t'){
                    skip = 1;
                }
                if (b == '\n') {
                    skip = 0;
                }
                if (skip == 0 && b !='\n') {
                    baos.write(b);
                }
            }
            return baos.toByteArray();
        }
    }

    // return a list of all encoded test data files in the htscodecs/tests/dat/<compressedDir> directory
    private List<Path> getInteropRansCompressedFilePaths(final String compressedDir) throws IOException {
        final List<Path> paths = new ArrayList<>();
        Files.newDirectoryStream(
                        CRAMInteropTestUtils.getInteropTestDataLocation().resolve("dat/"+compressedDir),
                        path -> Files.isRegularFile(path))
                .forEach(path -> paths.add(path));
        return paths;
    }

    // Given a compressed test file path, return the corresponding uncompressed file path
    public static final Path getRansUnCompressedFilePath(final Path compressedInteropPath) {
        String uncompressedFileName = getUncompressedFileName(compressedInteropPath.getFileName().toString());
        // Example compressedInteropPath: ../dat/r4x8/q4.1 => unCompressedFilePath: ../dat/q4
        return compressedInteropPath.getParent().getParent().resolve(uncompressedFileName);
    }

    public static final String getUncompressedFileName(final String compressedFileName) {
        // Returns original filename from compressed file name
        int lastDotIndex = compressedFileName.lastIndexOf(".");
        if (lastDotIndex >= 0) {
            String fileName = compressedFileName.substring(0, lastDotIndex);
            return fileName;
        } else {
            throw new CRAMException("The format of the compressed File Name is not as expected. " +
                    "The name of the compressed file should contain a period followed by a number that" +
                    "indicates the order of compression. Actual compressed file name = "+ compressedFileName);
        }
    }

    public static final RANSParams getRans4x8Params(final Path compressedInteropPath){
        // Returns RANSParams from compressed file path
        final String compressedFileName = compressedInteropPath.getFileName().toString();
        final int lastDotIndex = compressedFileName.lastIndexOf(".");
        if (lastDotIndex >= 0 && lastDotIndex < compressedFileName.length() - 1) {
            return new RANS4x8Params(RANSParams.ORDER.fromInt(Integer.parseInt(compressedFileName.substring(lastDotIndex + 1))));
        } else {
            throw new CRAMException("The format of the compressed File Name is not as expected. " +
                    "The name of the compressed file should contain a period followed by a number that" +
                    "indicates the order of compression. Actual compressed file name = "+ compressedFileName);
        }
    }

    public static final RANSParams getRansNx16Params(final Path compressedInteropPath){
        // Returns RANSParams from compressed file path
        final String compressedFileName = compressedInteropPath.getFileName().toString();
        final int lastDotIndex = compressedFileName.lastIndexOf(".");
        if (lastDotIndex >= 0 && lastDotIndex < compressedFileName.length() - 1) {
            return new RANSNx16Params(Integer.parseInt(compressedFileName.substring(lastDotIndex + 1)));
        } else {
            throw new CRAMException("The format of the compressed File Name is not as expected. " +
                    "The name of the compressed file should contain a period followed by a number that" +
                    "indicates the order of compression. Actual compressed file name = "+ compressedFileName);
        }
    }

}