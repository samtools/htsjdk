package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCodecTest extends HtsjdkTest {

    @DataProvider(name = "compressionLevels")
    public Object[][] compressionLevels() {
        return new Object[][] { {0}, {1}, {5}, {6} };
    }

    @DataProvider(name = "inputSizes")
    public Object[][] inputSizes() {
        return new Object[][] { {0}, {1}, {100}, {1000}, {65536} };
    }

    @DataProvider(name = "formatsAndSizes")
    public Object[][] formatsAndSizes() {
        return new Object[][] {
                {GzipCodec.Format.GZIP, 0},
                {GzipCodec.Format.GZIP, 1},
                {GzipCodec.Format.GZIP, 100},
                {GzipCodec.Format.GZIP, 65536},
                {GzipCodec.Format.BGZF, 0},
                {GzipCodec.Format.BGZF, 1},
                {GzipCodec.Format.BGZF, 100},
                {GzipCodec.Format.BGZF, 10000},
        };
    }

    /** Generate deterministic test data of the specified size. */
    private byte[] makeTestData(final int size) {
        if (size == 0) return new byte[0];
        final Random random = new Random(size); // deterministic per size
        final byte[] data = new byte[size];
        random.nextBytes(data);
        return data;
    }

    @Test(dataProvider = "formatsAndSizes")
    public void testRoundTrip(final GzipCodec.Format format, final int size) {
        final byte[] original = makeTestData(size);
        final GzipCodec codec = new GzipCodec();

        final ByteBuffer compressed = codec.compress(ByteBuffer.wrap(original), format);
        final ByteBuffer decompressed = codec.decompress(compressed);

        final byte[] result = new byte[decompressed.remaining()];
        decompressed.get(result);
        Assert.assertEquals(result, original, "Round-trip failed for format=" + format + " size=" + size);
    }

    @Test(dataProvider = "compressionLevels")
    public void testCompressionLevels(final int level) {
        final byte[] original = makeTestData(5000);
        final GzipCodec codec = new GzipCodec(level);

        final ByteBuffer compressed = codec.compress(ByteBuffer.wrap(original));
        final ByteBuffer decompressed = codec.decompress(compressed);

        final byte[] result = new byte[decompressed.remaining()];
        decompressed.get(result);
        Assert.assertEquals(result, original, "Round-trip failed for level=" + level);
    }

    @Test
    public void testFilteredStrategy() {
        final byte[] original = makeTestData(5000);
        final GzipCodec codec = new GzipCodec(5, Deflater.FILTERED);

        final ByteBuffer compressed = codec.compress(ByteBuffer.wrap(original));
        final ByteBuffer decompressed = codec.decompress(compressed);

        final byte[] result = new byte[decompressed.remaining()];
        decompressed.get(result);
        Assert.assertEquals(result, original);
    }

    @Test(dataProvider = "inputSizes")
    public void testCallerProvidedOutputBuffer(final int size) {
        final byte[] original = makeTestData(size);
        final GzipCodec codec = new GzipCodec();

        // Compress into caller-provided buffer
        final ByteBuffer input = ByteBuffer.wrap(original);
        final ByteBuffer compressOutput = ByteBuffer.allocate(original.length + 256);
        final int compressedSize = codec.compress(input, compressOutput);
        compressOutput.flip();
        Assert.assertEquals(compressedSize, compressOutput.remaining());

        // Decompress into caller-provided buffer
        final ByteBuffer decompOutput = ByteBuffer.allocate(original.length);
        final int decompressedSize = codec.decompress(compressOutput, decompOutput);
        decompOutput.flip();
        Assert.assertEquals(decompressedSize, original.length);

        final byte[] result = new byte[decompOutput.remaining()];
        decompOutput.get(result);
        Assert.assertEquals(result, original);
    }

    @Test
    public void testMultipleBlocksSameCodec() {
        final GzipCodec codec = new GzipCodec();

        for (int i = 0; i < 10; i++) {
            final byte[] original = makeTestData(1000 + i * 100);
            final ByteBuffer compressed = codec.compress(ByteBuffer.wrap(original));
            final ByteBuffer decompressed = codec.decompress(compressed);

            final byte[] result = new byte[decompressed.remaining()];
            decompressed.get(result);
            Assert.assertEquals(result, original, "Failed on iteration " + i);
        }
    }

    @Test
    public void testCodecOutputReadableByGZIPInputStream() throws IOException {
        final byte[] original = makeTestData(5000);
        final GzipCodec codec = new GzipCodec();

        final ByteBuffer compressed = codec.compress(ByteBuffer.wrap(original));
        final byte[] compressedBytes = new byte[compressed.remaining()];
        compressed.get(compressedBytes);

        // Standard GZIPInputStream should be able to read our output
        try (final GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressedBytes))) {
            final byte[] result = gis.readAllBytes();
            Assert.assertEquals(result, original);
        }
    }

    @Test
    public void testGZIPOutputStreamReadableByCodec() throws IOException {
        final byte[] original = makeTestData(5000);

        // Compress with standard GZIPOutputStream
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final GZIPOutputStream gos = new GZIPOutputStream(baos)) {
            gos.write(original);
        }
        final byte[] compressed = baos.toByteArray();

        // Our codec should be able to decompress it
        final GzipCodec codec = new GzipCodec();
        final ByteBuffer decompressed = codec.decompress(ByteBuffer.wrap(compressed));

        final byte[] result = new byte[decompressed.remaining()];
        decompressed.get(result);
        Assert.assertEquals(result, original);
    }

    @Test
    public void testBgzfOutputReadableByBlockGunzipper() {
        final byte[] original = makeTestData(5000);
        final GzipCodec codec = new GzipCodec();

        final ByteBuffer compressed = codec.compress(ByteBuffer.wrap(original), GzipCodec.Format.BGZF);
        final byte[] compressedBytes = new byte[compressed.remaining()];
        compressed.get(compressedBytes);

        // BlockGunzipper should be able to decompress our BGZF output
        final BlockGunzipper gunzipper = new BlockGunzipper();
        final byte[] result = new byte[original.length];
        gunzipper.unzipBlock(result, compressedBytes, compressedBytes.length);

        Assert.assertEquals(result, original);
    }

    @Test
    public void testCrcValidation() {
        final byte[] original = makeTestData(1000);
        final GzipCodec codec = new GzipCodec();
        codec.setCheckCrcs(true);

        // Normal round-trip should succeed with CRC checking
        final ByteBuffer compressed = codec.compress(ByteBuffer.wrap(original));
        final ByteBuffer decompressed = codec.decompress(compressed);

        final byte[] result = new byte[decompressed.remaining()];
        decompressed.get(result);
        Assert.assertEquals(result, original);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testCrcValidationDetectsCorruption() {
        final byte[] original = makeTestData(1000);
        final GzipCodec codec = new GzipCodec();
        codec.setCheckCrcs(true);

        final ByteBuffer compressed = codec.compress(ByteBuffer.wrap(original));

        // Corrupt the CRC32 in the trailer (4 bytes before the last 4 bytes)
        final int crcOffset = compressed.limit() - 8;
        compressed.put(crcOffset, (byte) ~compressed.get(crcOffset));

        final ByteBuffer output = ByteBuffer.allocate(original.length);
        codec.decompress(compressed, output); // should throw
    }
}
