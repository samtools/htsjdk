package htsjdk.samtools.cram.compression;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.Defaults;
import htsjdk.samtools.cram.compression.range.RangeParams;
import htsjdk.samtools.cram.structure.CompressorCache;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.zip.Deflater;

public class CompressorCacheTest extends HtsjdkTest {
    public static final File BZIP2_FILE = new File("src/test/resources/htsjdk/samtools/cram/io/bzip2-test.bz2");
    public static final byte[] TEST_BYTES = "This is a simple string to test compression".getBytes();

    final CompressorCache compressorCache = new CompressorCache();

    @DataProvider(name = "cachedCompressorForMethodPositiveTests")
    public Object[][] cachedCompressorForMethodPositiveTests() {
        return new Object[][]{
                {BlockCompressionMethod.RAW, ExternalCompressor.NO_COMPRESSION_ARG, RAWExternalCompressor.class},
                {BlockCompressionMethod.GZIP, ExternalCompressor.NO_COMPRESSION_ARG, GZIPExternalCompressor.class},
                {BlockCompressionMethod.GZIP, Deflater.NO_COMPRESSION, GZIPExternalCompressor.class},
                {BlockCompressionMethod.GZIP, Defaults.COMPRESSION_LEVEL, GZIPExternalCompressor.class},
                {BlockCompressionMethod.GZIP, Deflater.BEST_COMPRESSION, GZIPExternalCompressor.class},
                {BlockCompressionMethod.BZIP2, ExternalCompressor.NO_COMPRESSION_ARG, BZIP2ExternalCompressor.class},
                {BlockCompressionMethod.LZMA, ExternalCompressor.NO_COMPRESSION_ARG, LZMAExternalCompressor.class},
                {BlockCompressionMethod.RANS, 1, RANSExternalCompressor.class},
                {BlockCompressionMethod.RANS, 0, RANSExternalCompressor.class},
                {BlockCompressionMethod.RANS, ExternalCompressor.NO_COMPRESSION_ARG, RANSExternalCompressor.class},
                {BlockCompressionMethod.RANGE, 0x00, RangeExternalCompressor.class},
                {BlockCompressionMethod.RANGE, RangeParams.ORDER_FLAG_MASK, RangeExternalCompressor.class},
                {BlockCompressionMethod.RANGE, RangeParams.RLE_FLAG_MASK, RangeExternalCompressor.class},
                {BlockCompressionMethod.RANGE, RangeParams.RLE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK, RangeExternalCompressor.class},
                {BlockCompressionMethod.RANGE, RangeParams.CAT_FLAG_MASK, RangeExternalCompressor.class},
                {BlockCompressionMethod.RANGE, RangeParams.CAT_FLAG_MASK | RangeParams.ORDER_FLAG_MASK, RangeExternalCompressor.class},
                {BlockCompressionMethod.RANGE, RangeParams.PACK_FLAG_MASK, RangeExternalCompressor.class},
                {BlockCompressionMethod.RANGE, RangeParams.PACK_FLAG_MASK | RangeParams. ORDER_FLAG_MASK, RangeExternalCompressor.class},
                {BlockCompressionMethod.RANGE, RangeParams.PACK_FLAG_MASK | RangeParams.RLE_FLAG_MASK, RangeExternalCompressor.class},
                {BlockCompressionMethod.RANGE, RangeParams.PACK_FLAG_MASK | RangeParams.RLE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK, RangeExternalCompressor.class},
                {BlockCompressionMethod.RANGE, RangeParams.EXT_FLAG_MASK, RangeExternalCompressor.class},
                {BlockCompressionMethod.RANGE, RangeParams.EXT_FLAG_MASK | RangeParams.PACK_FLAG_MASK, RangeExternalCompressor.class},
                {BlockCompressionMethod.RANGE, ExternalCompressor.NO_COMPRESSION_ARG, RangeExternalCompressor.class},
        };
    }

    @Test(dataProvider = "cachedCompressorForMethodPositiveTests")
    public void testGetCachedCompressorForMethodPositive(
            final BlockCompressionMethod method,
            final int compressorSpecificArg,
            final Class<ExternalCompressor> expectedCompressorClass) {
        final ExternalCompressor externalCompressor = compressorCache.getCompressorForMethod(method, compressorSpecificArg);
        Assert.assertEquals(externalCompressor.getClass(), expectedCompressorClass);
    }

    @DataProvider(name = "cachedCompressorForMethodNegativeTests")
    public Object[][] compressorForMethodNegativeTests() {
        return new Object[][]{
                {BlockCompressionMethod.RAW, -2},
                {BlockCompressionMethod.RAW, 99},
                {BlockCompressionMethod.GZIP, Deflater.NO_COMPRESSION - 2},
                {BlockCompressionMethod.GZIP, Deflater.BEST_COMPRESSION + 1},
                {BlockCompressionMethod.BZIP2, -2},
                {BlockCompressionMethod.BZIP2, 99},
                {BlockCompressionMethod.LZMA, -2},
                {BlockCompressionMethod.LZMA, 99},
                {BlockCompressionMethod.RANS, 2},
        };
    }

    @Test(dataProvider = "cachedCompressorForMethodNegativeTests", expectedExceptions = IllegalArgumentException.class)
    public void testGetCompressorForMethodNegative(
            final BlockCompressionMethod method,
            final int compressorSpecificArg) {
        compressorCache.getCompressorForMethod(method, compressorSpecificArg);
    }
}