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
 * For native development env, the Interop test files are downloaded locally and made available at "../htscodecs/tests"
 * For CI env, the Interop test files are made available from the existing samtools installation
 * at "/samtools-1.14/htslib-1.14/htscodecs/tests"
 */
public class RANSInteropTest extends HtsjdkTest {
    public static final String COMPRESSED_RANS4X8_DIR = "r4x8";
    public static final String COMPRESSED_RANSNX16_DIR = "r4x16";

    /////////////////////////////////////////////////////////////////////////////////////////////////
    // RANS tests
    /////////////////////////////////////////////////////////////////////////////////////////////////

    // RANS4x8 codecs and testdata
    public Object[][] getRANS4x8TestData() throws IOException {
        // cache/reuse this for each test case to eliminate excessive garbage collection
        final List<RANSParams.ORDER> rans4x8ParamsOrderList = Arrays.asList(
                RANSParams.ORDER.ZERO,
                RANSParams.ORDER.ONE);
        final List<Object[]> testCases = new ArrayList<>();
        getInteropRANSTestFiles()
                .forEach(path ->
                        rans4x8ParamsOrderList.stream().map(rans4x8ParamsOrder -> new Object[]{
                                path,
                                new RANS4x8Encode(),
                                new RANS4x8Decode(),
                                new RANS4x8Params(rans4x8ParamsOrder),
                                COMPRESSED_RANS4X8_DIR
                        }).forEach(testCases::add));
        return testCases.toArray(new Object[][]{});
    }

    // RANSNx16 codecs and testdata
    public Object[][] getRANS4x16TestData() throws IOException {
        final List<Integer> ransNx16ParamsFormatFlagList = Arrays.asList(
                0x00,
                RANSNx16Params.ORDER_FLAG_MASK,
                RANSNx16Params.RLE_FLAG_MASK,
                RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
                RANSNx16Params.N32_FLAG_MASK,
                RANSNx16Params.N32_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
                RANSNx16Params.PACK_FLAG_MASK,
                RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
                RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.PACK_FLAG_MASK,
                RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
                RANSNx16Params.STRIPE_FLAG_MASK,
                RANSNx16Params.STRIPE_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK
                );
        final List<Object[]> testCases = new ArrayList<>();
        getInteropRANSTestFiles()
                .forEach(path ->
                        ransNx16ParamsFormatFlagList.stream().map(ransNx16ParamsFormatFlag -> new Object[]{
                                path,
                                new RANSNx16Encode(),
                                new RANSNx16Decode(),
                                new RANSNx16Params(ransNx16ParamsFormatFlag),
                                COMPRESSED_RANSNX16_DIR
                        }).forEach(testCases::add));
        return testCases.toArray(new Object[][]{});
    }

    @DataProvider(name = "allRansCodecsAndData")
    public Object[][] getAllRansCodecs() throws IOException {
        // concatenate RANS4x8 and RANSNx16 codecs and testdata
        return Stream.concat(Arrays.stream(getRANS4x8TestData()), Arrays.stream(getRANS4x16TestData()))
                .toArray(Object[][]::new);
    }

    // TODO: testHtslibVersion should depend on SamtoolsTestUtilsTest.testSamtoolsVersion
    @Test(description = "Test if CRAM Interop Test Data is available")
    public void testGetHTSCodecsCorpus() {
        if (!CRAMInteropTestUtils.isInteropTestDataAvailable()) {
            throw new SkipException(String.format("RANS Interop Test Data is not available at %s",
                    CRAMInteropTestUtils.INTEROP_TEST_FILES_PATH));
        }
    }

    @Test (
            dependsOnMethods = "testGetHTSCodecsCorpus",
            dataProvider = "allRansCodecsAndData",
            description = "Roundtrip using htsjdk RANS. Compare the output with the original file" )
    public void testRANSRoundTrip(
            final Path uncompressedInteropPath,
            final RANSEncode<RANSParams> ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params,
            final String unusedCompressedDirname) throws IOException {
        try (final InputStream uncompressedInteropStream = Files.newInputStream(uncompressedInteropPath)) {

            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results
            final ByteBuffer uncompressedInteropBytes = ByteBuffer.wrap(filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));

            // If Stripe Flag is set, skip the round trip test as encoding is not implemented for this case.
            if ((params.getFormatFlags() & RANSNx16Params.STRIPE_FLAG_MASK)==0) {
                final ByteBuffer compressedHtsjdkBytes = ransEncode.compress(uncompressedInteropBytes, params);
                uncompressedInteropBytes.rewind();
                Assert.assertEquals(ransDecode.uncompress(compressedHtsjdkBytes), uncompressedInteropBytes);
            }
        }
    }

    @Test (
            dependsOnMethods = "testGetHTSCodecsCorpus",
            dataProvider = "allRansCodecsAndData",
            description = "Compress the original file using htsjdk RANS and compare it with the existing compressed file. " +
                    "Uncompress the existing compressed file using htsjdk RANS and compare it with the original file.")
    public void testRANSPreCompressed(
            final Path uncompressedInteropPath,
            final RANSEncode<RANSParams> unused,
            final RANSDecode ransDecode,
            final RANSParams params,
            final String compressedInteropDirName) throws IOException {

        final Path preCompressedInteropPath = getCompressedRANSPath(compressedInteropDirName,uncompressedInteropPath, params);

        try (final InputStream uncompressedInteropStream = Files.newInputStream(uncompressedInteropPath);
             final InputStream preCompressedInteropStream = Files.newInputStream(preCompressedInteropPath)
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

    // return a list of all RANS test data files in the InteropTest/RANS directory
    private List<Path> getInteropRANSTestFiles() throws IOException {
        final List<Path> paths = new ArrayList<>();
        Files.newDirectoryStream(
                        CRAMInteropTestUtils.getInteropTestDataLocation().resolve("dat"),
                        path -> path.getFileName().startsWith("q4") ||
                                path.getFileName().startsWith("q8") ||
                                path.getFileName().startsWith("qvar") ||
                                path.getFileName().startsWith("q40+dir"))
                .forEach(path -> paths.add(path));
        return paths;
    }

    // the input files have embedded newlines that the test remove before round-tripping...
    final byte[] filterEmbeddedNewlines(final byte[] rawBytes) throws IOException {
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

    // Given a test file name, map it to the corresponding rans compressed path
    final Path getCompressedRANSPath(final String ransType,final Path uncompressedInteropPath, RANSParams params) {

        // Example compressedFileName: r4x16/q4.193
        // the substring after "." in the compressedFileName is the formatFlags (aka. the first byte of the compressed stream)
        final String compressedFileName = String.format("%s/%s.%s", ransType, uncompressedInteropPath.getFileName(), params.getFormatFlags());
        return uncompressedInteropPath.getParent().resolve(compressedFileName);
    }

}