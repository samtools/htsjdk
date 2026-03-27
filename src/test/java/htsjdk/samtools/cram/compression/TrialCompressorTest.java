package htsjdk.samtools.cram.compression;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Decode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Encode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Params;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Random;

/**
 * Tests for {@link TrialCompressor} that verifies it correctly tries multiple codecs
 * and selects the smallest output.
 */
public class TrialCompressorTest extends HtsjdkTest {

    @Test
    public void testTrialCompressorSelectsSmallest() {
        // Create two compressors with different characteristics
        final ExternalCompressor gzip = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.GZIP, 5);
        final ExternalCompressor ransOrder0 = ExternalCompressor.getCompressorForMethod(
                BlockCompressionMethod.RANSNx16, RANSNx16Params.ORDER.ZERO.ordinal());

        final TrialCompressor trial = new TrialCompressor(List.of(gzip, ransOrder0));

        // Compress some data — trial compressor should produce output no larger than either candidate
        final Random random = new Random(42);
        final byte[] data = new byte[10000];
        random.nextBytes(data);

        final byte[] trialResult = trial.compress(data, null);
        final byte[] gzipResult = gzip.compress(data, null);
        final byte[] ransResult = ransOrder0.compress(data, null);

        final int minSize = Math.min(gzipResult.length, ransResult.length);
        Assert.assertTrue(trialResult.length <= minSize,
                String.format("Trial compressor should pick smallest: trial=%d, gzip=%d, rans=%d",
                        trialResult.length, gzipResult.length, ransResult.length));
    }

    @Test
    public void testTrialCompressorRoundTrip() {
        // Verify data compressed by trial compressor can be decompressed
        final ExternalCompressor gzip = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.GZIP, 5);
        final ExternalCompressor ransOrder1 = ExternalCompressor.getCompressorForMethod(
                BlockCompressionMethod.RANSNx16, RANSNx16Params.ORDER.ONE.ordinal());

        final TrialCompressor trial = new TrialCompressor(List.of(gzip, ransOrder1));

        final byte[] data = new byte[1000];
        new Random(123).nextBytes(data);

        final byte[] compressed = trial.compress(data, null);

        // The compressed output is in whichever format won — we need to try both decoders
        // In practice, the block header would identify the format. For this test, just verify
        // that one of the decoders can decompress it.
        boolean decompressed = false;
        try {
            final byte[] result = gzip.uncompress(compressed);
            Assert.assertEquals(result, data);
            decompressed = true;
        } catch (final Exception e) {
            // GZIP decompression failed, try rANS
        }
        if (!decompressed) {
            final byte[] result = ransOrder1.uncompress(compressed);
            Assert.assertEquals(result, data);
        }
    }

    @Test
    public void testTrialCompressorCachesBestMethod() {
        // After trial phase, should use cached best for TRIAL_SPAN blocks
        final ExternalCompressor gzip = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.GZIP, 1);
        final ExternalCompressor bzip2 = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.BZIP2, -1);

        final TrialCompressor trial = new TrialCompressor(List.of(gzip, bzip2));

        final byte[] data = new byte[500];
        new Random(99).nextBytes(data);

        // Run through trial phase (NTRIALS = 3 blocks)
        for (int i = 0; i < 3; i++) {
            trial.compress(data, null);
        }

        // Subsequent calls should use the cached best (fast path) — just verify they don't throw
        for (int i = 0; i < 10; i++) {
            final byte[] result = trial.compress(data, null);
            Assert.assertNotNull(result);
            Assert.assertTrue(result.length > 0);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testTrialCompressorRejectsFewerThanTwoCandidates() {
        final ExternalCompressor gzip = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.GZIP, 5);
        new TrialCompressor(List.of(gzip));
    }

    @Test
    public void testTrialCompressorWithEmptyData() {
        final ExternalCompressor gzip = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.GZIP, 5);
        final ExternalCompressor bzip2 = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.BZIP2, -1);

        final TrialCompressor trial = new TrialCompressor(List.of(gzip, bzip2));
        final byte[] result = trial.compress(new byte[0], null);
        Assert.assertEquals(result.length, 0);
    }
}
