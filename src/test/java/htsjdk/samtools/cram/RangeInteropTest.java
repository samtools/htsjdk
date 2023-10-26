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
import java.util.List;

import static htsjdk.samtools.cram.CRAMInteropTestUtils.filterEmbeddedNewlines;
import static htsjdk.samtools.cram.CRAMInteropTestUtils.getInteropCompressedFilePaths;
import static htsjdk.samtools.cram.CRAMInteropTestUtils.getParamsFormatFlags;
import static htsjdk.samtools.cram.CRAMInteropTestUtils.getUnCompressedFilePath;

public class RangeInteropTest extends HtsjdkTest  {
    public static final String COMPRESSED_RANGE_DIR = "arith";

    @DataProvider(name = "roundTripTestCases")
    public Object[][] getRoundTripTestCases() throws IOException {

        // params:
        // compressed testfile path, uncompressed testfile path,
        // Range encoder, Range decoder, Range params
        final List<Object[]> testCases = new ArrayList<>();
        for (Path path : getInteropCompressedFilePaths(COMPRESSED_RANGE_DIR)) {
            Object[] objects = new Object[]{
                    path,
                    getUnCompressedFilePath(path),
                    new RangeEncode(),
                    new RangeDecode(),
                    new RangeParams(getParamsFormatFlags(path))
            };
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    @Test(description = "Test if CRAM Interop Test Data is available")
    public void testHtsCodecsCorpusIsAvailable() {
        if (!CRAMInteropTestUtils.isInteropTestDataAvailable()) {
            throw new SkipException(String.format("CRAM Interop Test Data is not available at %s",
                    CRAMInteropTestUtils.INTEROP_TEST_FILES_PATH));
        }
    }

    @Test (
            dependsOnMethods = "testHtsCodecsCorpusIsAvailable",
            dataProvider = "roundTripTestCases",
            description = "Roundtrip using htsjdk Range Codec. Compare the output with the original file" )
    public void testRangeRoundTrip(
            final Path unusedCompressedFilePath,
            final Path uncompressedFilePath,
            final RangeEncode rangeEncode,
            final RangeDecode rangeDecode,
            final RangeParams params) throws IOException {
        try (final InputStream uncompressedInteropStream = Files.newInputStream(uncompressedFilePath)) {

            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through Range codec and compare the
            // results
            final ByteBuffer uncompressedInteropBytes = ByteBuffer.wrap(filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));

            if (params.isStripe()) {
                Assert.assertThrows(CRAMException.class, () -> rangeEncode.compress(uncompressedInteropBytes, params));
            } else {
                final ByteBuffer compressedHtsjdkBytes = rangeEncode.compress(uncompressedInteropBytes, params);
                uncompressedInteropBytes.rewind();
                Assert.assertEquals(rangeDecode.uncompress(compressedHtsjdkBytes), uncompressedInteropBytes);
            }
        }
    }

    @Test (
            dependsOnMethods = "testHtsCodecsCorpusIsAvailable",
            dataProvider = "roundTripTestCases",
            description = "Uncompress the existing compressed file using htsjdk Range codec and compare it with the original file.")
    public void testDecodeOnly(
            final Path compressedFilePath,
            final Path uncompressedInteropPath,
            final RangeEncode<RangeParams> unusedRangeEncode,
            final RangeDecode rangeDecode,
            final RangeParams unusedRangeParams) throws IOException {
        try (final InputStream uncompressedInteropStream = Files.newInputStream(uncompressedInteropPath);
             final InputStream preCompressedInteropStream = Files.newInputStream(compressedFilePath)
        ) {

            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through Range codec and compare the
            // results
            final ByteBuffer uncompressedInteropBytes = ByteBuffer.wrap(filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));
            final ByteBuffer preCompressedInteropBytes = ByteBuffer.wrap(IOUtils.toByteArray(preCompressedInteropStream));

            // Use htsjdk to uncompress the precompressed file from htscodecs repo
            final ByteBuffer uncompressedHtsjdkBytes = rangeDecode.uncompress(preCompressedInteropBytes);

            // Compare the htsjdk uncompressed bytes with the original input file from htscodecs repo
            Assert.assertEquals(uncompressedHtsjdkBytes, uncompressedInteropBytes);
        } catch (final NoSuchFileException ex){
            throw new SkipException("Skipping testDecodeOnly as either input file " +
                    "or precompressed file is missing.", ex);
        }
    }

}