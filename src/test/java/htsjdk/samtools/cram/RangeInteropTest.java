package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.range.RangeDecode;
import htsjdk.samtools.cram.compression.range.RangeEncode;
import htsjdk.samtools.cram.compression.range.RangeParams;
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

public class RangeInteropTest extends HtsjdkTest  {
    public static final String COMPRESSED_RANGE_DIR = "arith";

    @DataProvider(name = "allRangeCodecsAndDataForRoundtrip")
    public Object[][] getAllRangeCodecsAndDataForRoundtrip() throws IOException {
        final List<Integer> rangeParamsFormatFlagList = Arrays.asList(
                0x00,
                RangeParams.ORDER_FLAG_MASK,
                RangeParams.RLE_FLAG_MASK,
                RangeParams.RLE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK
        );
        final List<Object[]> testCases = new ArrayList<>();
        getInteropRangeTestFiles()
                .forEach(path ->
                        rangeParamsFormatFlagList.stream().map(rangeParamsFormatFlag -> new Object[]{
                                path,
                                new RangeEncode(),
                                new RangeDecode(),
                                new RangeParams(rangeParamsFormatFlag),
                                COMPRESSED_RANGE_DIR
                        }).forEach(testCases::add));
        return testCases.toArray(new Object[][]{});
    }

    public Object[][] getRangeDecodeOnlyTestData() throws IOException {
        final List<Integer> rangeParamsFormatFlagList = Arrays.asList(
                RangeParams.STRIPE_FLAG_MASK, // TODO: doesn't work because pack is not implemented yet!!!
                RangeParams.ORDER_FLAG_MASK|RangeParams.STRIPE_FLAG_MASK);
        final List<Object[]> testCases = new ArrayList<>();
        getInteropRangeTestFiles()
                .forEach(path ->
                        rangeParamsFormatFlagList.stream().map(rangeParamsFormatFlag -> new Object[]{
                                path,
                                new RangeEncode(),
                                new RangeDecode(),
                                new RangeParams(rangeParamsFormatFlag),
                                COMPRESSED_RANGE_DIR
                        }).forEach(testCases::add));
        return testCases.toArray(new Object[][]{});
    }

    @DataProvider(name = "allRangeCodecsAndData")
    public Object[][] getAllRangeCodecs() throws IOException {

        // params:
        // uncompressed testfile path, Range encoder, Range decoder,
        // Range params, compressed testfile directory name
        return Stream.concat(Arrays.stream(getAllRangeCodecsAndDataForRoundtrip()), Arrays.stream(getRangeDecodeOnlyTestData()))
                .toArray(Object[][]::new);
    }

    @Test(description = "Test if CRAM Interop Test Data is available")
    public void testGetHTSCodecsCorpus() {
        if (!CRAMInteropTestUtils.isInteropTestDataAvailable()) {
            throw new SkipException(String.format("CRAM Interop Test Data is not available at %s",
                    CRAMInteropTestUtils.INTEROP_TEST_FILES_PATH));
        }
    }

    @Test (
            dependsOnMethods = "testGetHTSCodecsCorpus",
            dataProvider = "allRangeCodecsAndDataForRoundtrip",
            description = "Roundtrip using htsjdk Range Codec. Compare the output with the original file" )
    public void testRangeRoundTrip(
            final Path uncompressedInteropPath,
            final RangeEncode rangeEncode,
            final RangeDecode rangeDecode,
            final RangeParams params,
            final String unusedCompressedDirname) throws IOException {
        final Path preCompressedInteropPath = CRAMInteropTestUtils.getCompressedCodecPath(COMPRESSED_RANGE_DIR,uncompressedInteropPath, params.getFormatFlags());
        try (final InputStream uncompressedInteropStream = Files.newInputStream(uncompressedInteropPath);
             final InputStream preCompressedInteropStream = Files.newInputStream(preCompressedInteropPath)) {
            final ByteBuffer uncompressedInteropBytes = ByteBuffer.wrap(CRAMInteropTestUtils.filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));
            final ByteBuffer preCompressedInteropBytes = ByteBuffer.wrap(IOUtils.toByteArray(preCompressedInteropStream));
            final ByteBuffer compressedHtsjdkBytes = rangeEncode.compress(uncompressedInteropBytes, params);
            Assert.assertEquals(compressedHtsjdkBytes, preCompressedInteropBytes);
            Assert.assertEquals(rangeDecode.uncompress(compressedHtsjdkBytes), uncompressedInteropBytes);
        }

    }

    @Test (
            dependsOnMethods = "testGetHTSCodecsCorpus",
            dataProvider = "allRangeCodecsAndData",
            description = "Compress the original file using htsjdk Range Codec and compare it with the existing compressed file. " +
                    "Uncompress the existing compressed file using htsjdk Range Codec and compare it with the original file.")
    public void testRangePreCompressed(
            final Path uncompressedInteropPath,
            final RangeEncode<RangeParams> unused,
            final RangeDecode rangeDecode,
            final RangeParams params,
            final String compressedInteropDirName) throws IOException {

        final Path preCompressedInteropPath = CRAMInteropTestUtils.getCompressedCodecPath(compressedInteropDirName,uncompressedInteropPath, params.getFormatFlags());

        try (final InputStream uncompressedInteropStream = Files.newInputStream(uncompressedInteropPath);
             final InputStream preCompressedInteropStream = Files.newInputStream(preCompressedInteropPath)
        ) {
            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through Range Codec and compare the
            // results
            final ByteBuffer uncompressedInteropBytes = ByteBuffer.wrap(CRAMInteropTestUtils.filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));

            final ByteBuffer preCompressedInteropBytes = ByteBuffer.wrap(IOUtils.toByteArray(preCompressedInteropStream));

            // Use htsjdk to uncompress the precompressed file from htscodecs repo
            final ByteBuffer uncompressedHtsjdkBytes = rangeDecode.uncompress(preCompressedInteropBytes);

            // Compare the htsjdk uncompressed bytes with the original input file from htscodecs repo
            Assert.assertEquals(uncompressedHtsjdkBytes, uncompressedInteropBytes);
        } catch (final NoSuchFileException ex){
            throw new SkipException("Skipping testRangePrecompressed as either input file " +
                    "or precompressed file is missing.", ex);
        }
    }

    // return a list of all Range test data files in the htscodecs/tests/dat directory
    private List<Path> getInteropRangeTestFiles() throws IOException {
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

}