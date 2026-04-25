package htsjdk.samtools.cram.compression.rans;

/**
 * Utility methods for rANS encoding and decoding: cumulative frequency lookup,
 * state renormalization, and frequency table normalization.
 */
public final class Utils {

    /**
     * Extract the cumulative frequency from a rANS state by masking off the lower {@code scaleBits} bits.
     *
     * @param r the current rANS state
     * @param scaleBits log2 of the total frequency sum
     * @return the cumulative frequency used to look up the decoded symbol
     */
    public static int RANSGetCumulativeFrequency(final long r, final int scaleBits) {
        return (int) (r & ((1 << scaleBits) - 1));
    }

    /** Nx16 renormalization: reads 2 LE bytes from buf at posHolder[0] if state is below lower bound. */
    public static long RANSDecodeRenormalizeNx16(final long r, final byte[] buf, final int[] posHolder) {
        long ret = r;
        if (ret < Constants.RANS_Nx16_LOWER_BOUND) {
            int pos = posHolder[0];
            ret = (ret << 16) | (buf[pos++] & 0xFF) | ((buf[pos++] & 0xFF) << 8);
            posHolder[0] = pos;
        }
        return ret;
    }

    /** 4x8 renormalization: reads 1 byte at a time from buf at posHolder[0] until state reaches lower bound. */
    public static long RANSDecodeRenormalize4x8(final long r, final byte[] buf, final int[] posHolder) {
        long ret = r;
        while (ret < Constants.RANS_4x8_LOWER_BOUND) {
            ret = (ret << 8) | (buf[posHolder[0]++] & 0xFF);
        }
        return ret;
    }

    /**
     * Normalize symbol frequencies so they sum to {@code 1 << bits}.
     * Uses fixed-point arithmetic to scale frequencies proportionally.
     */
    public static void normaliseFrequenciesOrder0(final int[] F, final int bits) {
        int T = 0;
        for (final int freq : F) {
            T += freq;
        }

        final int renormFreq = 1 << bits;

        int m = 0;
        int M = 0;
        for (int symbol = 0; symbol < Constants.NUMBER_OF_SYMBOLS; symbol++) {
            if (m < F[symbol]) {
                m = F[symbol];
                M = symbol;
            }
        }

        final long tr = (T > 0) ? (((long) renormFreq << 31) / T + (1 << 30) / T) : 0;
        int fsum = 0;
        for (int symbol = 0; symbol < Constants.NUMBER_OF_SYMBOLS; symbol++) {
            if (F[symbol] == 0) {
                continue;
            }
            if ((F[symbol] = (int) ((F[symbol] * tr) >> 31)) == 0) {
                F[symbol] = 1;
            }
            fsum += F[symbol];
        }

        if (fsum < renormFreq) {
            F[M] += renormFreq - fsum;
        } else if (fsum > renormFreq) {
            F[M] -= fsum - renormFreq;
        }
    }

    /**
     * Normalize Order-1 frequency tables: for each context symbol with non-zero frequency,
     * compute the minimum bit size and normalize that context's frequency table.
     */
    public static void normaliseFrequenciesOrder1(final int[][] F, final int shift) {
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[Constants.NUMBER_OF_SYMBOLS][j] == 0) {
                continue;
            }

            int bitSize = (int) Math.ceil(Math.log(F[Constants.NUMBER_OF_SYMBOLS][j]) / Math.log(2));
            if (bitSize > shift) {
                bitSize = shift;
            }
            if (bitSize == 0) {
                bitSize = 1;
            }

            normaliseFrequenciesOrder0(F[j], bitSize);
        }
    }

    /**
     * Shift-based frequency normalization: scale frequencies by a power of 2 so they sum to {@code 1 << bits}.
     */
    public static void normaliseFrequenciesOrder0Shift(final int[] frequencies, final int bits) {
        int totalFrequency = 0;
        for (final int freq : frequencies) {
            totalFrequency += freq;
        }
        if (totalFrequency == 0 || totalFrequency == (1 << bits)) {
            return;
        }

        int shift = 0;
        while (totalFrequency < (1 << bits)) {
            totalFrequency *= 2;
            shift++;
        }

        for (int symbol = 0; symbol < Constants.NUMBER_OF_SYMBOLS; symbol++) {
            if (frequencies[symbol] != 0) {
                frequencies[symbol] <<= shift;
            }
        }
    }

    /** Shift-based normalization for all Order-1 context rows. */
    public static void normaliseFrequenciesOrder1Shift(final int[][] F, final int shift) {
        for (int symbol = 0; symbol < Constants.NUMBER_OF_SYMBOLS; symbol++) {
            if (F[Constants.NUMBER_OF_SYMBOLS][symbol] != 0) {
                normaliseFrequenciesOrder0Shift(F[symbol], shift);
            }
        }
    }
}
