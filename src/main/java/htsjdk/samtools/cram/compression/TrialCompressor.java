package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.structure.CRAMCodecModelContext;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

import java.util.List;

/**
 * An {@link ExternalCompressor} that tries multiple candidate compressors and picks the one
 * that produces the smallest output. This implements a simplified version of htslib's trial
 * compression system ({@code cram_compress_block3} in {@code cram_io.c}).
 *
 * <p>The trial compressor operates in two phases:
 * <ol>
 *   <li><b>Trial phase</b>: Compresses data with all candidates and selects the smallest result.
 *       Runs for {@link #NTRIALS} consecutive blocks to gather statistics.</li>
 *   <li><b>Production phase</b>: Uses the cached best compressor for {@link #TRIAL_SPAN} blocks
 *       before re-entering the trial phase.</li>
 * </ol>
 *
 * <p>Simplifications vs htslib:
 * <ul>
 *   <li>No cost weighting (pure size comparison)</li>
 *   <li>No adaptive culling of consistently-losing methods</li>
 *   <li>No anomaly-triggered retrials</li>
 * </ul>
 *
 * // TODO: Add cost weighting based on compression level (htslib meth_cost[] table)
 * // TODO: Add adaptive culling of methods that consistently lose by >20%
 *
 * @see ExternalCompressor
 */
public class TrialCompressor extends ExternalCompressor {

    /** Number of blocks between trial phases (matching htslib TRIAL_SPAN). */
    private static final int TRIAL_SPAN = 70;

    /** Number of trial blocks to run before selecting a winner (matching htslib NTRIALS). */
    private static final int NTRIALS = 3;

    private final List<ExternalCompressor> candidates;
    private final long[] accumulatedSizes;

    private ExternalCompressor cachedBest;
    private int blocksUntilRetrial = 0;
    private int trialBlocksRemaining = NTRIALS;

    /**
     * Create a trial compressor with the given candidate compressors. The first candidate's
     * {@link BlockCompressionMethod} is used as this compressor's method (for block headers),
     * though the actual method used may vary per block.
     *
     * @param candidates the candidate compressors to try (must have at least 2)
     */
    public TrialCompressor(final List<ExternalCompressor> candidates) {
        super(candidates.get(0).getMethod());
        if (candidates.size() < 2) {
            throw new IllegalArgumentException("TrialCompressor requires at least 2 candidates");
        }
        this.candidates = List.copyOf(candidates);
        this.accumulatedSizes = new long[candidates.size()];
    }

    /**
     * Compress data by trying all candidates (during trial phase) or using the cached best
     * (during production phase).
     *
     * @param data the data to compress
     * @param contextModel optional codec context model
     * @return the compressed data from the winning compressor
     */
    @Override
    public byte[] compress(final byte[] data, final CRAMCodecModelContext contextModel) {
        if (data.length == 0) {
            return data;
        }

        // Production phase: use cached best
        if (cachedBest != null && blocksUntilRetrial > 0) {
            blocksUntilRetrial--;
            return cachedBest.compress(data, contextModel);
        }

        // Trial phase: try all candidates
        byte[] bestResult = null;
        int bestSize = Integer.MAX_VALUE;
        int bestIdx = 0;

        for (int i = 0; i < candidates.size(); i++) {
            final byte[] result = candidates.get(i).compress(data, contextModel);
            accumulatedSizes[i] += result.length;
            if (result.length < bestSize) {
                bestSize = result.length;
                bestResult = result;
                bestIdx = i;
            }
        }

        trialBlocksRemaining--;
        if (trialBlocksRemaining <= 0) {
            // Select winner from accumulated sizes
            long bestAccum = Long.MAX_VALUE;
            int winnerIdx = 0;
            for (int i = 0; i < candidates.size(); i++) {
                if (accumulatedSizes[i] < bestAccum) {
                    bestAccum = accumulatedSizes[i];
                    winnerIdx = i;
                }
            }
            cachedBest = candidates.get(winnerIdx);
            blocksUntilRetrial = TRIAL_SPAN;
            trialBlocksRemaining = NTRIALS;
            // Reset accumulators (halve instead of zero, matching htslib)
            for (int i = 0; i < accumulatedSizes.length; i++) {
                accumulatedSizes[i] /= 2;
            }
        }

        return bestResult;
    }

    /**
     * Decompress data. Delegates to the first candidate since all candidates must be able to
     * decompress the same format (the block header identifies the actual method used).
     *
     * <p>Note: In practice, decompression is handled by the method-specific decompressor selected
     * based on the block's compression method ID, not through this trial compressor.
     *
     * @param data the compressed data
     * @return the decompressed data
     */
    @Override
    public byte[] uncompress(final byte[] data) {
        // Decompression is always done via the method-specific decompressor, not the trial compressor.
        // This method exists only to satisfy the abstract class contract.
        return candidates.get(0).uncompress(data);
    }
}
