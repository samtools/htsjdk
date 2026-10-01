package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.compression.nametokenisation.NameTokeniserExternalCompressor;
import htsjdk.samtools.cram.compression.range.RangeExternalCompressor;
import htsjdk.samtools.cram.compression.range.RangeParams;
import htsjdk.samtools.cram.compression.rans.RANSNx16Params;
import htsjdk.samtools.cram.compression.rans.RANSParams;
import htsjdk.samtools.cram.structure.CRAMCodecModelContext;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * An {@link ExternalCompressor} that tries several candidate compressors on one kind of block and settles on the one
 * that does best, as htslib's {@code cram_compress_block2} does:
 *
 * <ul>
 *   <li>A trial compresses each of a few blocks with every candidate still being tried, writes the smallest result,
 *       and adds each candidate's size, plus 2000 bytes so that small blocks don't decide on a few bytes, to its
 *       running total.</li>
 *   <li>At the end of a trial each total is weighted by the candidate's relative cost, a penalty for being slower that
 *       shrinks as the compression level rises, and the lowest wins the blocks until the next trial.</li>
 *   <li>The first trial is two blocks and the next comes 35 blocks later; after that a trial is three blocks every 70,
 *       or up to twice as far apart once the same candidate keeps winning. Each trial halves the totals first. A sudden
 *       jump in block size brings the next trial forward.</li>
 *   <li>A candidate that has lost several trials since it last won, by enough in total, is no longer tried, and
 *       FQZComp is dropped after its first loss.</li>
 * </ul>
 *
 * <p>The compression method is unknown until the first non-empty block is compressed, so {@link #getMethod()} throws
 * {@link IllegalStateException} before then. Not thread-safe.
 *
 * @see ExternalCompressor
 */
public class TrialCompressor extends ExternalCompressor {
    private static final int NTRIALS = 3;
    private static final int TRIAL_SPAN = 70;
    private static final int TRIAL_SIZE_PADDING = 2000;
    // A candidate is dropped after this many lost trials since it last won, if it lost them by at least this much in
    // total (twice both at level 7 and above)
    private static final int MAX_LOSSES = 4;
    private static final double MAX_TOTAL_LOSS_MARGIN = 0.20;

    private final List<ExternalCompressor> candidates;
    private final int compressionLevel;
    private final double[] costWeights;
    private final boolean[] stillTried;
    private final long[] totalSizes;
    private final int[] lossesSinceWin;
    private final double[] totalLossMargin;

    private int trialBlocksRemaining = NTRIALS - 1;
    private int blocksUntilTrial = TRIAL_SPAN / 2;
    private int winner = -1;
    private int timesWinnerRepeated = 0;
    private int averageBlockSize = 0;
    private int averageBlockSizeChange = 0;

    /**
     * @param candidates the candidate compressors to try (at least 2)
     * @param compressionLevel the level (1-9) of the profile, which sets how heavily slower candidates are penalised
     */
    public TrialCompressor(final List<ExternalCompressor> candidates, final int compressionLevel) {
        super(null); // method unknown until first trial
        if (candidates.size() < 2) {
            throw new IllegalArgumentException("TrialCompressor requires at least 2 candidates");
        }
        this.candidates = List.copyOf(candidates);
        this.compressionLevel = compressionLevel;
        final int n = candidates.size();
        this.costWeights = new double[n];
        this.stillTried = new boolean[n];
        this.totalSizes = new long[n];
        this.lossesSinceWin = new int[n];
        this.totalLossMargin = new double[n];
        for (int i = 0; i < n; i++) {
            costWeights[i] = costWeight(relativeCost(candidates.get(i)), compressionLevel);
            stillTried[i] = true;
        }
    }

    /**
     * htslib's relative cost of a compressor ({@code meth_cost} in {@code cram_io.c}): how much smaller its output must
     * be, at levels 2 and 3, to be chosen over a compressor of cost 1.
     */
    static double relativeCost(final ExternalCompressor compressor) {
        switch (compressor.getMethod()) {
            case GZIP:
                return ((GZIPExternalCompressor) compressor).getWriteCompressionLevel() == 1 ? 1.01 : 1.04;
            case BZIP2:
                return 1.07;
            case LZMA:
                return 1.08;
            case RANS:
                return ((RANS4x8ExternalCompressor) compressor).getOrder() == RANSParams.ORDER.ONE ? 1.01 : 1.00;
            case RANSNx16: {
                final int flags = ((RANSNx16ExternalCompressor) compressor).getFlags();
                if (flags == (RANSNx16Params.ORDER_FLAG_MASK | RANSNx16Params.STRIPE_FLAG_MASK)) {
                    return 1.03;
                }
                return (flags & RANSNx16Params.ORDER_FLAG_MASK) != 0 ? 1.01 : 1.00;
            }
            case ADAPTIVE_ARITHMETIC:
                return ((RangeExternalCompressor) compressor).getFlags() == RangeParams.PACK_FLAG_MASK ? 1.03 : 1.04;
            case FQZCOMP:
                return 1.05;
            case NAME_TOKENISER:
                return ((NameTokeniserExternalCompressor) compressor).usesArith() ? 1.07 : 1.05;
            default:
                return 1.00;
        }
    }

    /** The factor a total size is multiplied by for a compressor of the given relative cost at the given level. */
    static double costWeight(final double relativeCost, final int compressionLevel) {
        if (compressionLevel <= 1) return 1 + (relativeCost - 1) * 4;
        if (compressionLevel <= 3) return relativeCost;
        if (compressionLevel <= 6) return 1 + (relativeCost - 1) / 2;
        if (compressionLevel <= 7) return 1 + (relativeCost - 1) / 3;
        return 1;
    }

    @Override
    public Set<BlockCompressionMethod> getPossibleMethods() {
        final Set<BlockCompressionMethod> methods = EnumSet.noneOf(BlockCompressionMethod.class);
        for (final ExternalCompressor candidate : candidates) {
            methods.addAll(candidate.getPossibleMethods());
        }
        return methods;
    }

    @Override
    public byte[] compress(final byte[] data, final CRAMCodecModelContext contextModel) {
        if (data.length == 0) {
            // Empty blocks don't count as trials but still need valid compressed output
            final ExternalCompressor comp = winner < 0 ? candidates.get(0) : candidates.get(winner);
            setMethod(comp.getMethod());
            return comp.compress(data, contextModel);
        }

        // htslib's running average and spread of block sizes, kept in ints as htslib does
        final int size = data.length;
        if (averageBlockSize != 0
                && (size / 4 - 750 > averageBlockSize || size < averageBlockSize / 4 - 750)
                && Math.abs(size - averageBlockSize) / 10 > averageBlockSizeChange) {
            blocksUntilTrial = 0;
        }
        averageBlockSizeChange = (int) (0.9 * (averageBlockSizeChange + Math.abs(size - averageBlockSize)));
        averageBlockSize = (int) ((int) (averageBlockSize + size * 0.2) * 0.8);

        if (trialBlocksRemaining > 0 || --blocksUntilTrial <= 0) {
            return compressTrialBlock(data, contextModel);
        }
        setMethod(candidates.get(winner).getMethod());
        return candidates.get(winner).compress(data, contextModel);
    }

    private byte[] compressTrialBlock(final byte[] data, final CRAMCodecModelContext contextModel) {
        if (blocksUntilTrial <= 0) {
            blocksUntilTrial = TRIAL_SPAN;
            trialBlocksRemaining = NTRIALS;
            for (int i = 0; i < totalSizes.length; i++) {
                totalSizes[i] /= 2;
            }
        }

        byte[] smallest = null;
        int smallestIndex = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (!stillTried[i]) continue;
            final byte[] compressed = candidates.get(i).compress(data, contextModel);
            totalSizes[i] += compressed.length + TRIAL_SIZE_PADDING;
            if (smallest == null || compressed.length < smallest.length) {
                smallest = compressed;
                smallestIndex = i;
            }
        }

        if (--trialBlocksRemaining == 0) {
            chooseWinner();
        }
        setMethod(candidates.get(smallestIndex).getMethod());
        return smallest;
    }

    /** Weights the totals by cost, picks the winner, and stops trying candidates that keep losing. */
    private void chooseWinner() {
        int best = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (!stillTried[i]) continue;
            // htslib weights the totals themselves, so a slower candidate's penalty carries into later trials
            totalSizes[i] = (long) (totalSizes[i] * costWeights[i]);
            if (best < 0 || totalSizes[i] < totalSizes[best]) {
                best = i;
            }
        }

        if (best == winner) {
            blocksUntilTrial = (int) (blocksUntilTrial * Math.min(2, 1 + timesWinnerRepeated / 4.0));
            timesWinnerRepeated++;
        } else {
            timesWinnerRepeated = 0;
        }
        winner = best;

        final int scale = compressionLevel >= 7 ? 2 : 1;
        for (int i = 0; i < candidates.size(); i++) {
            if (i == best) {
                lossesSinceWin[i] = 0;
                totalLossMargin[i] = 0;
            } else if (stillTried[i] && totalSizes[best] < totalSizes[i]) {
                final double margin = (double) totalSizes[i] / totalSizes[best] - 1;
                // as in htslib, the margins only start to add up once a candidate has lost MAX_LOSSES trials
                if (++lossesSinceWin[i] >= MAX_LOSSES * scale
                        && (totalLossMargin[i] += margin) >= MAX_TOTAL_LOSS_MARGIN * scale) {
                    stillTried[i] = false;
                }
                // htslib drops FQZComp after one loss, since which of its models suits the data rarely changes
                if (candidates.get(i).getMethod() == BlockCompressionMethod.FQZCOMP) {
                    stillTried[i] = false;
                }
            }
        }
    }

    /**
     * Decompress data. Delegates to the winner if one has been selected, otherwise to the
     * first candidate. In practice, decompression is handled by the method-specific decompressor
     * selected based on the block's compression method ID, not through this trial compressor.
     */
    @Override
    public byte[] uncompress(final byte[] data) {
        return (winner >= 0 ? candidates.get(winner) : candidates.get(0)).uncompress(data);
    }
}
