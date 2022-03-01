package htsjdk.samtools.cram.compression.rans;

public class FrequencyUtils {
    static int[] normaliseFrequenciesOrder0(final int[] F, final int bits) {
        // Returns an array of normalised Frequencies,
        // such that the frequencies add up to 1<<bits.
        int T = 0;

        // compute total Frequency
        for (int freq : F) {
            T += freq;
        }

        // Scale total of frequencies to max
        final int renormFreq = 1 << bits;
        final long tr = ((long) (renormFreq) << 31) / T + (1 << 30) / T;

        // keep track of the symbol that has the maximum frequency
        // in the input Frequency array.
        // This symbol's frequency might be altered at the end to make sure
        // that the total normalized frequencies add up to "renormFreq" value.
        int m = 0;
        int M = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (m < F[j]) {
                m = F[j];
                M = j;
            }
        }
        int fsum = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[j] == 0) {
                continue;
            }

            // using tr to normalize symbol frequencies such that their total = renormFreq
            if ((F[j] = (int) ((F[j] * tr) >> 31)) == 0) {

                // A non-zero symbol frequency should not be incorrectly set to 0.
                // If the calculated value is 0, change it to 1
                F[j] = 1;
            }
            fsum += F[j];
        }

        // adjust the frequency of the symbol "M" such that
        // the sum of frequencies of all the symbols = renormFreq
        if (fsum < renormFreq) {
            F[M] += renormFreq - fsum;
        } else if (fsum > renormFreq){
            F[M] -= fsum - renormFreq;
        }
        return F;
    }
}