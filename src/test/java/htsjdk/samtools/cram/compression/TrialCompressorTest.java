package htsjdk.samtools.cram.compression;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.rans.RANSNx16Params;
import htsjdk.samtools.cram.structure.CRAMCodecModelContext;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.Test;

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

        final TrialCompressor trial = new TrialCompressor(List.of(gzip, ransOrder0), 5);

        // Compress some data — trial compressor should produce output no larger than either candidate
        final Random random = new Random(42);
        final byte[] data = new byte[10000];
        random.nextBytes(data);

        final byte[] trialResult = trial.compress(data, null);
        final byte[] gzipResult = gzip.compress(data, null);
        final byte[] ransResult = ransOrder0.compress(data, null);

        final int minSize = Math.min(gzipResult.length, ransResult.length);
        Assert.assertTrue(
                trialResult.length <= minSize,
                String.format(
                        "Trial compressor should pick smallest: trial=%d, gzip=%d, rans=%d",
                        trialResult.length, gzipResult.length, ransResult.length));
    }

    @Test
    public void testTrialCompressorRoundTrip() {
        // Verify data compressed by trial compressor can be decompressed
        final ExternalCompressor gzip = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.GZIP, 5);
        final ExternalCompressor ransOrder1 = ExternalCompressor.getCompressorForMethod(
                BlockCompressionMethod.RANSNx16, RANSNx16Params.ORDER.ONE.ordinal());

        final TrialCompressor trial = new TrialCompressor(List.of(gzip, ransOrder1), 5);

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

        final TrialCompressor trial = new TrialCompressor(List.of(gzip, bzip2), 5);

        final byte[] data = new byte[500];
        new Random(99).nextBytes(data);

        // Run through the first trial (2 blocks)
        for (int i = 0; i < 2; i++) {
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
        final ExternalCompressor gzip = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.GZIP, 5);
        final ExternalCompressor bzip2 = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.BZIP2, -1);

        final TrialCompressor trial = new TrialCompressor(List.of(gzip, bzip2), 5);

        final byte[] data = new byte[500];
        new Random(77).nextBytes(data);

        // Run well past the first trial (2 blocks) and into the blocks the winner compresses
        // to ensure the production-phase path is exercised.
        final int totalBlocks = 80;
        for (int i = 0; i < totalBlocks; i++) {
            final byte[] compressed = trial.compress(data, null);
            final BlockCompressionMethod method = trial.getMethod();

            // Verify the declared method can actually decompress the data just produced.
            final ExternalCompressor decoder = method == BlockCompressionMethod.GZIP ? gzip : bzip2;
            final byte[] decompressed = decoder.uncompress(compressed);
            Assert.assertEquals(
                    decompressed,
                    data,
                    String.format(
                            "Block %d: getMethod() returned %s but data could not be decompressed with it", i, method));
        }
    }

    /**
     * Verifies that re-trial actually completes and switches the winning method when data
     * characteristics change. Runs at level 8, where cost doesn't weigh on the choice.
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
            System.arraycopy(
                    randomBlock, 0, gzipFriendly, off, Math.min(randomBlock.length, gzipFriendly.length - off));
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

        final TrialCompressor trial = new TrialCompressor(List.of(gzip, ransOrder0), 8);

        // Phase 1: GZIP-friendly data → the first trial (2 blocks) and the 34 blocks before the next → GZIP wins
        compressTimes(trial, gzipFriendly, 36);
        Assert.assertEquals(trial.getMethod(), BlockCompressionMethod.GZIP, "GZIP should win on high-entropy data");

        // Phase 2: rANS-friendly data → the re-trial (3 blocks) and some of the 70 blocks after it → rANS wins
        compressTimes(trial, ransFriendly, 40);
        Assert.assertEquals(
                trial.getMethod(), BlockCompressionMethod.RANSNx16, "rANS should win on skewed data after re-trial");

        // Phase 3: back to GZIP-friendly → the rest of the 70 blocks, then re-trials, each halving the totals, until
        // GZIP's lead outweighs what rANS built up → should switch back
        compressTimes(trial, gzipFriendly, 200);
        Assert.assertEquals(
                trial.getMethod(),
                BlockCompressionMethod.GZIP,
                "GZIP should win again after switching back to high-entropy data");
    }

    private static void compressTimes(final ExternalCompressor compressor, final byte[] data, final int times) {
        for (int i = 0; i < times; i++) {
            compressor.compress(data, null);
        }
    }

    /** A stand-in compressor whose output is a fixed fraction of its input's size, counting the blocks it is given. */
    private static class FixedRatioCompressor extends ExternalCompressor {
        private final double ratio;
        int blocksCompressed = 0;

        FixedRatioCompressor(final BlockCompressionMethod method, final double ratio) {
            super(method);
            this.ratio = ratio;
        }

        /** The fraction of a block of this size that the output is. */
        double ratioFor(final int blockSize) {
            return ratio;
        }

        @Override
        public byte[] compress(final byte[] data, final CRAMCodecModelContext unused) {
            blocksCompressed++;
            return new byte[(int) (data.length * ratioFor(data.length))];
        }

        @Override
        public byte[] uncompress(final byte[] data) {
            throw new UnsupportedOperationException();
        }
    }

    private static final byte[] BLOCK = new byte[100_000];

    @Test
    public void aSlowerCandidateLosesIfItsOutputIsSmallerByLessThanItsCost() {
        // at level 3 BZIP2's cost is 1.07: 20,000 + 2000 padding, weighted, is 23,540 > 21,000 + 2000
        final FixedRatioCompressor bzip2 = new FixedRatioCompressor(BlockCompressionMethod.BZIP2, 0.20);
        final FixedRatioCompressor raw = new FixedRatioCompressor(BlockCompressionMethod.RAW, 0.21);
        final TrialCompressor trial = new TrialCompressor(List.of(bzip2, raw), 3);
        compressTimes(trial, BLOCK, 3);
        Assert.assertEquals(trial.getMethod(), BlockCompressionMethod.RAW);
    }

    @Test
    public void aSlowerCandidateWinsOnSizeAloneAboveLevel7() {
        final FixedRatioCompressor bzip2 = new FixedRatioCompressor(BlockCompressionMethod.BZIP2, 0.20);
        final FixedRatioCompressor raw = new FixedRatioCompressor(BlockCompressionMethod.RAW, 0.21);
        final TrialCompressor trial = new TrialCompressor(List.of(bzip2, raw), 8);
        compressTimes(trial, BLOCK, 3);
        Assert.assertEquals(trial.getMethod(), BlockCompressionMethod.BZIP2);
    }

    @Test
    public void aCandidateThatKeepsLosingByALotStopsBeingTried() {
        final FixedRatioCompressor winner = new FixedRatioCompressor(BlockCompressionMethod.RAW, 0.5);
        final FixedRatioCompressor loser = new FixedRatioCompressor(BlockCompressionMethod.LZMA, 0.8);
        final TrialCompressor trial = new TrialCompressor(List.of(winner, loser), 5);
        compressTimes(trial, BLOCK, 300);
        final int triedInFirst300 = loser.blocksCompressed;
        compressTimes(trial, BLOCK, 300);
        Assert.assertTrue(triedInFirst300 > 2, "the loser should be tried in more than the first trial");
        Assert.assertEquals(loser.blocksCompressed, triedInFirst300, "the loser should no longer be tried");
    }

    @Test
    public void aCandidateThatLosesNarrowlyKeepsBeingTried() {
        final FixedRatioCompressor winner = new FixedRatioCompressor(BlockCompressionMethod.RAW, 0.500);
        final FixedRatioCompressor loser = new FixedRatioCompressor(BlockCompressionMethod.LZMA, 0.505);
        final TrialCompressor trial = new TrialCompressor(List.of(winner, loser), 8);
        compressTimes(trial, BLOCK, 300);
        final int triedInFirst300 = loser.blocksCompressed;
        compressTimes(trial, BLOCK, 300);
        Assert.assertTrue(loser.blocksCompressed > triedInFirst300, "the loser should still be tried");
    }

    @Test
    public void fqzcompStopsBeingTriedAfterLosingOnce() {
        final FixedRatioCompressor fqzcomp = new FixedRatioCompressor(BlockCompressionMethod.FQZCOMP, 0.51);
        final FixedRatioCompressor raw = new FixedRatioCompressor(BlockCompressionMethod.RAW, 0.50);
        final TrialCompressor trial = new TrialCompressor(List.of(fqzcomp, raw), 5);
        compressTimes(trial, BLOCK, 300);
        Assert.assertEquals(fqzcomp.blocksCompressed, 2, "FQZComp should be tried only in the first trial");
    }

    @Test
    public void trialsSpreadOutWhileTheSameCandidateKeepsWinning() {
        // re-trialling every 73 blocks would try the loser in 44 of the first 1000; spreading them out, in 26
        final FixedRatioCompressor winner = new FixedRatioCompressor(BlockCompressionMethod.RAW, 0.500);
        final FixedRatioCompressor loser = new FixedRatioCompressor(BlockCompressionMethod.LZMA, 0.502);
        final TrialCompressor trial = new TrialCompressor(List.of(winner, loser), 8);
        compressTimes(trial, BLOCK, 1000);
        Assert.assertTrue(loser.blocksCompressed <= 30, "tried in " + loser.blocksCompressed + " blocks");
    }

    /** A stand-in compressor whose output is one fraction of a block below 10,000 bytes and another above. */
    private static FixedRatioCompressor sizeDependent(
            final BlockCompressionMethod method, final double smallBlockRatio, final double largeBlockRatio) {
        return new FixedRatioCompressor(method, 0) {
            @Override
            double ratioFor(final int blockSize) {
                return blockSize < 10_000 ? smallBlockRatio : largeBlockRatio;
            }
        };
    }

    @Test
    public void aSuddenJumpInBlockSizeBringsTheNextTrialForward() {
        final ExternalCompressor goodOnSmallBlocks = sizeDependent(BlockCompressionMethod.RAW, 0.5, 0.9);
        final ExternalCompressor goodOnLargeBlocks = sizeDependent(BlockCompressionMethod.LZMA, 0.9, 0.5);
        final TrialCompressor trial = new TrialCompressor(List.of(goodOnSmallBlocks, goodOnLargeBlocks), 8);
        // 40 blocks of 1,000 bytes take in the first two trials; the next would be 70 blocks later
        compressTimes(trial, new byte[1000], 40);
        Assert.assertEquals(trial.getMethod(), BlockCompressionMethod.RAW);
        // blocks thirty times larger start a trial at once
        compressTimes(trial, new byte[30_000], 4);
        Assert.assertEquals(trial.getMethod(), BlockCompressionMethod.LZMA);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testTrialCompressorRejectsFewerThanTwoCandidates() {
        final ExternalCompressor gzip = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.GZIP, 5);
        new TrialCompressor(List.of(gzip), 5);
    }

    @Test
    public void testTrialCompressorWithEmptyData() {
        final ExternalCompressor gzip = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.GZIP, 5);
        final ExternalCompressor bzip2 = ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.BZIP2, -1);

        final TrialCompressor trial = new TrialCompressor(List.of(gzip, bzip2), 5);
        final byte[] result = trial.compress(new byte[0], null);
        // Empty data still goes through the compressor (e.g., GZIP writes a header),
        // so the result may be non-empty. Verify it decompresses back to empty.
        final byte[] roundTripped = trial.uncompress(result);
        Assert.assertEquals(roundTripped.length, 0);
    }

    @Test
    public void possibleMethodsAreThoseOfEveryCandidate() {
        final TrialCompressor trial = new TrialCompressor(
                List.of(
                        ExternalCompressor.getCompressorForMethod(BlockCompressionMethod.GZIP, 5),
                        ExternalCompressor.getCompressorForMethod(
                                BlockCompressionMethod.RANSNx16, RANSNx16Params.ORDER.ZERO.ordinal())),
                5);
        Assert.assertEquals(
                trial.getPossibleMethods(), EnumSet.of(BlockCompressionMethod.GZIP, BlockCompressionMethod.RANSNx16));
    }
}
