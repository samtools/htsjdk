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
import java.util.zip.GZIPInputStream;

public class RangeInteropTest extends HtsjdkTest  {
    public static final String COMPRESSED_RANGE_DIR = "range";

    // enumerates the different flag combinations
    @DataProvider(name = "roundTripTestCases")
    public Object[][] getRoundTripTestCases() throws IOException {
        // uncompressed testfile path, Range params
        final List<Integer> rangeParamsFormatFlagList = Arrays.asList(
                0x00,
                RangeParams.ORDER_FLAG_MASK,
                RangeParams.RLE_FLAG_MASK,
                RangeParams.RLE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK,
                RangeParams.CAT_FLAG_MASK,
                RangeParams.CAT_FLAG_MASK | RangeParams.ORDER_FLAG_MASK,
                RangeParams.PACK_FLAG_MASK,
                RangeParams.PACK_FLAG_MASK | RangeParams. ORDER_FLAG_MASK,
                RangeParams.PACK_FLAG_MASK | RangeParams.RLE_FLAG_MASK,
                RangeParams.PACK_FLAG_MASK | RangeParams.RLE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK,
                RangeParams.EXT_FLAG_MASK,
                RangeParams.EXT_FLAG_MASK | RangeParams.PACK_FLAG_MASK);
        final List<Object[]> testCases = new ArrayList<>();
        //note that for the roundtrip tests, we're retrieving ALL of the raw test files in the interop directory,
        // *including* the ones intended for the other codecs such as name tok, etc., but the range codec should
        // be tolerant of any stream of data
        CRAMInteropTestUtils.getRawCRAMInteropTestFiles()
                .forEach(path ->
                        rangeParamsFormatFlagList.stream().map(rangeParamsFormatFlag -> new Object[]{
                                path,
                                new RangeParams(rangeParamsFormatFlag)
                        }).forEach(testCases::add));
        return testCases.toArray(new Object[][]{});
    }

    @DataProvider(name = "decodeOnlyTestCases")
    public Object[][] getDecodeOnlyTestCases() throws IOException {
        // compressed testfile path, uncompressed testfile path,
        final List<Object[]> testCases = new ArrayList<>();
        for (Path path : CRAMInteropTestUtils.getCRAMInteropCompressedPaths(COMPRESSED_RANGE_DIR)) {
            testCases.add(new Object[] { path, CRAMInteropTestUtils.getUnCompressedPathForCompressedPath(path) });
        }
        return testCases.toArray(new Object[][]{});
    }

    @Test (
            dataProvider = "roundTripTestCases",
            description = "Roundtrip using htsjdk Range Codec. Compare the output with the original file" )
    public void testRangeRoundTrip(final Path uncompressedFilePath, final RangeParams params) throws IOException {
        try (final InputStream uncompressedInteropStream = new GZIPInputStream(Files.newInputStream(uncompressedFilePath))) {
            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through Range codec and compare the
            // results
            final ByteBuffer uncompressedInteropBytes =
                    ByteBuffer.wrap(CRAMInteropTestUtils.filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));

            final RangeDecode rangeDecode = new RangeDecode();
            final RangeEncode rangeEncode = new RangeEncode();
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
            dataProvider = "decodeOnlyTestCases",
            description = "Uncompress the existing compressed file using htsjdk Range codec and compare it with the original file.")
    public void testDecodeOnly(final Path compressedFilePath, final Path uncompressedInteropPath) throws IOException {
        try (final InputStream uncompressedInteropStream =
                     new GZIPInputStream(Files.newInputStream(uncompressedInteropPath));
             final InputStream preCompressedInteropStream = Files.newInputStream(compressedFilePath)
        ) {
            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through Range codec
            // and compare the results

            final ByteBuffer uncompressedInteropBytes;
            if (uncompressedInteropPath.toString().endsWith("u32" + CRAMInteropTestUtils.GZIP_SUFFIX)) {
                uncompressedInteropBytes = ByteBuffer.wrap(IOUtils.toByteArray(uncompressedInteropStream));
            } else {
                uncompressedInteropBytes = ByteBuffer.wrap(CRAMInteropTestUtils.filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));
            }
            final ByteBuffer preCompressedInteropBytes = ByteBuffer.wrap(IOUtils.toByteArray(preCompressedInteropStream));

            // Use htsjdk to uncompress the precompressed file from htscodecs repo
            final RangeDecode rangeDecode = new RangeDecode();
            final ByteBuffer uncompressedHtsjdkBytes = rangeDecode.uncompress(preCompressedInteropBytes);

            // Compare the htsjdk uncompressed bytes with the original input file from htscodecs repo
            Assert.assertEquals(uncompressedHtsjdkBytes, uncompressedInteropBytes);
        } catch (final NoSuchFileException ex){
            throw new SkipException("Skipping testDecodeOnly as either input file " +
                    "or precompressed file is missing.", ex);
        }
    }

}