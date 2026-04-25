package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.structure.CRAMCodecModelContext;
import java.util.List;

/**
 * An {@link ExternalCompressor} that tries multiple candidate compressors and selects the one
 * that produces the smallest output. Matches htslib's trial compression approach.
 *
 * <p>Not thread-safe. Instances must not be shared across threads.
 *
 * <ol>
 *   <li><b>Trial phase</b> ({@code nTrials} non-empty blocks): compresses with ALL candidates,
 *       picks the smallest for each block, and accumulates sizes to determine the overall winner.</li>
 *   <li><b>Production phase</b> (next {@code trialSpan} blocks): uses the cached winner only.</li>
 *   <li><b>Re-trial</b>: after {@code trialSpan} blocks, re-enters the trial phase to adapt
 *       to changing data characteristics.</li>
 * </ol>
 *
 * <p>The compression method is initially null and is set after the first non-empty block is
 * compressed. Calling {@link #getMethod()} before any non-empty {@link #compress} call will
 * throw {@link IllegalStateException}.
 *
 * @see ExternalCompressor
 */
public class TrialCompressor extends ExternalCompressor {
    /** Default number of non-empty blocks to trial before selecting a winner (matching htslib NTRIALS). */
    private static final int DEFAULT_NTRIALS = 3;

    /** Default number of blocks between re-trials (matching htslib TRIAL_SPAN). */
    private static final int DEFAULT_TRIAL_SPAN = 70;

    private final List<ExternalCompressor> candidates;
    private final long[] accumulatedSizes;

    private int nTrials = DEFAULT_NTRIALS;
    private int trialSpan = DEFAULT_TRIAL_SPAN;

    private ExternalCompressor winner;
    private int trialBlocksRemaining = nTrials;
    private int blocksUntilRetrial = 0;

    /**
     * Create a trial compressor with the given candidate compressors.
     *
     * @param candidates the candidate compressors to try (must have at least 2)
     */
    public TrialCompressor(final List<ExternalCompressor> candidates) {
        super(null); // method unknown until first trial
        if (candidates.size() < 2) {
            throw new IllegalArgumentException("TrialCompressor requires at least 2 candidates");
        }
        this.candidates = List.copyOf(candidates);
        this.accumulatedSizes = new long[candidates.size()];
    }

    /**
     * Set the number of non-empty blocks to trial before selecting a winner.
     * Must be called before the first {@link #compress} call. Intended for testing.
     */
    TrialCompressor setNTrials(final int nTrials) {
        this.nTrials = nTrials;
        this.trialBlocksRemaining = nTrials;
        return this;
    }

    /**
     * Set the number of blocks between re-trials.
     * Must be called before the first {@link #compress} call. Intended for testing.
     */
    TrialCompressor setTrialSpan(final int trialSpan) {
        this.trialSpan = trialSpan;
        return this;
    }

    /**
     * Compress data. During the trial phase, tries all candidates and accumulates size statistics.
     * After {@code nTrials} non-empty blocks, selects the overall winner and uses it exclusively
     * until the next re-trial.
     *
     * @param data the data to compress
     * @param contextModel optional codec context model
     * @return the compressed data from the best compressor for this block
     */
    @Override
    public byte[] compress(final byte[] data, final CRAMCodecModelContext contextModel) {
        if (data.length == 0) {
            // Empty blocks don't count as trials but still need valid compressed output
            final ExternalCompressor comp = this.winner == null ? candidates.get(0) : winner;
            setMethod(comp.getMethod());
            return comp.compress(data, contextModel);
        }

        if (winner != null) {
            // Production phase: use cached winner
            if (blocksUntilRetrial > 0) {
                blocksUntilRetrial--;
                setMethod(winner.getMethod());
                return winner.compress(data, contextModel);
            }
            // Time for re-training: reset trial counter but leave blocksUntilRetrial at 0
            // so subsequent calls fall through to the trial code below until the trial completes.
            else {
                this.trialBlocksRemaining = nTrials;
                this.winner = null;

                // Halve accumulated sizes to weight the new trial blocks
                for (int i = 0; i < accumulatedSizes.length; i++) {
                    accumulatedSizes[i] /= 2;
                }
            }
        }

        // Trial: compress with all candidates, track sizes and per-candidate results
        final byte[][] results = new byte[candidates.size()][];
        for (int i = 0; i < candidates.size(); i++) {
            results[i] = candidates.get(i).compress(data, contextModel);
            accumulatedSizes[i] += results[i].length;
        }

        trialBlocksRemaining--;
        if (trialBlocksRemaining == 0) {
            // Trial complete — select overall winner from accumulated sizes
            long bestAccum = Long.MAX_VALUE;
            int winnerIdx = 0;
            for (int i = 0; i < candidates.size(); i++) {
                if (accumulatedSizes[i] < bestAccum) {
                    bestAccum = accumulatedSizes[i];
                    winnerIdx = i;
                }
            }
            winner = candidates.get(winnerIdx);
            blocksUntilRetrial = trialSpan;
            setMethod(winner.getMethod());

            // Return the winner's result for this block (even if not the smallest for this block)
            return results[winnerIdx];
        } else {
            // Still mid-trial — return the smallest result for this block
            int bestIdx = 0;
            for (int i = 1; i < results.length; i++) {
                if (results[i].length < results[bestIdx].length) {
                    bestIdx = i;
                }
            }
            setMethod(candidates.get(bestIdx).getMethod());
            return results[bestIdx];
        }
    }

    /**
     * Decompress data. Delegates to the winner if one has been selected, otherwise to the
     * first candidate. In practice, decompression is handled by the method-specific decompressor
     * selected based on the block's compression method ID, not through this trial compressor.
     */
    @Override
    public byte[] uncompress(final byte[] data) {
        return (winner != null ? winner : candidates.get(0)).uncompress(data);
    }
}
