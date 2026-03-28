package htsjdk.samtools.cram.compression;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Decode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Encode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Params;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
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

    /**
     * Verifies that getMethod() always returns a method consistent with the bytes just returned
     * by compress(). This is the key invariant needed by createCompressedBlockForStream, which
     * calls compress() and then getMethod() to write the block header.
     *
     * Specifically exercises: the production phase (blocks 4-73) where the old code returned
     * winner.compress() but forgot to call setMethod(winner.getMethod()), leaving the method
     * set to whatever the last trial block's per-block-best happened to be.
     */
    @Test
    public void testGetMethodMatchesCompressedDataThroughoutLifecycle() {
        final ExternalCompressor gzip  = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.GZIP,  5);
        final ExternalCompressor bzip2 = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.BZIP2, -1);

        final TrialCompressor trial = new TrialCompressor(List.of(gzip, bzip2));

        final byte[] data = new byte[500];
        new Random(77).nextBytes(data);

        // Run well past the trial phase (NTRIALS=3) and into the production phase (TRIAL_SPAN=70)
        // to ensure the production-phase path is exercised.
        final int totalBlocks = 80;
        for (int i = 0; i < totalBlocks; i++) {
            final byte[] compressed = trial.compress(data, null);
            final BlockCompressionMethod method = trial.getMethod();

            // Verify the declared method can actually decompress the data just produced.
            final ExternalCompressor decoder = method == BlockCompressionMethod.GZIP ? gzip : bzip2;
            final byte[] decompressed = decoder.uncompress(compressed);
            Assert.assertEquals(decompressed, data,
                    String.format("Block %d: getMethod() returned %s but data could not be decompressed with it", i, method));
        }
    }

    /**
     * Verifies that re-trial actually completes and switches the winning method when data
     * characteristics change. Uses a short trial span to observe the switch.
     *
     * We construct two data patterns with a large compression ratio difference between GZIP
     * and rANS order-0, so the switch is decisive even after the accumulated-size halving
     * that occurs at each re-trial boundary.
     *
     * - "GZIP-friendly" data: random bytes where GZIP compresses at least 10% smaller than rANS.
     * - "rANS-friendly" data: skewed distribution (90% one value, 10% spread over a small range)
     *   which rANS order-0's frequency model handles much better than GZIP's LZ77.
     */
    @Test
    public void testRetrialSwitchesWinner() {
        final ExternalCompressor gzip = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.GZIP, 1);
        final ExternalCompressor ransOrder0 = ExternalCompressor.getCompressorForMethod(
                BlockCompressionMethod.RANSNx16, RANSNx16Params.ORDER.ZERO.ordinal());

        // Repeated random block: 9500 random bytes repeated into 10KB.
        // GZIP's LZ77 finds the repeated pattern and compresses ~7% better than rANS order-0.
        // The block size is chosen so the GZIP advantage is moderate — large enough to win the
        // initial trial, but small enough that the rANS-friendly data can overcome the halved
        // accumulated sizes during re-trial.
        final byte[] randomBlock = new byte[9500];
        new Random(42).nextBytes(randomBlock);
        final byte[] gzipFriendly = new byte[10_000];
        for (int off = 0; off < gzipFriendly.length; off += randomBlock.length) {
            System.arraycopy(randomBlock, 0, gzipFriendly, off,
                    Math.min(randomBlock.length, gzipFriendly.length - off));
        }
        // Sanity check that GZIP actually wins on this data
        Assert.assertTrue(
                gzip.compress(gzipFriendly, null).length < ransOrder0.compress(gzipFriendly, null).length,
                "GZIP should compress repeated-block data better than rANS");

        // Skewed distribution: 90% value 25, 10% spread over 20-30.
        // rANS order-0 excels here because it encodes frequent symbols in < 1 bit.
        final byte[] ransFriendly = new byte[10_000];
        final Random skewRandom = new Random(42);
        for (int i = 0; i < ransFriendly.length; i++) {
            ransFriendly[i] = (byte) (skewRandom.nextInt(100) < 90 ? 25 : (20 + skewRandom.nextInt(11)));
        }
        // Sanity check that rANS actually wins on this data
        Assert.assertTrue(
                ransOrder0.compress(ransFriendly, null).length < gzip.compress(ransFriendly, null).length,
                "rANS should compress skewed data better than GZIP");

        final TrialCompressor trial = new TrialCompressor(List.of(gzip, ransOrder0));
        trial.setNTrials(3);
        trial.setTrialSpan(5);

        // Phase 1: GZIP-friendly data → 3 trial + 5 production = 8 blocks → GZIP should win
        for (int i = 0; i < 8; i++) {
            trial.compress(gzipFriendly, null);
        }
        Assert.assertEquals(trial.getMethod(), BlockCompressionMethod.GZIP,
                "GZIP should win on high-entropy data");

        // Phase 2: rANS-friendly data → 3 re-trial + 5 production = 8 blocks → rANS should win
        for (int i = 0; i < 8; i++) {
            trial.compress(ransFriendly, null);
        }
        Assert.assertEquals(trial.getMethod(), BlockCompressionMethod.RANSNx16,
                "rANS should win on skewed data after re-trial");

        // Phase 3: back to GZIP-friendly → should switch back
        for (int i = 0; i < 8; i++) {
            trial.compress(gzipFriendly, null);
        }
        Assert.assertEquals(trial.getMethod(), BlockCompressionMethod.GZIP,
                "GZIP should win again after switching back to high-entropy data");
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
        // Empty data still goes through the compressor (e.g., GZIP writes a header),
        // so the result may be non-empty. Verify it decompresses back to empty.
        final byte[] roundTripped = trial.uncompress(result);
        Assert.assertEquals(roundTripped.length, 0);
    }
}
