package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;

final public class Utils {

    private static void reverse(final byte[] array, final int offset, final int size) {
        if (array == null) {
            return;
        }
        int i = offset;
        int j = offset + size - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }

    public static void reverse(final ByteBuffer byteBuffer) {
        byte tmp;
        if (byteBuffer.hasArray()) {
            reverse(byteBuffer.array(), byteBuffer.arrayOffset(), byteBuffer.limit());
        } else {
            for (int i = 0; i < byteBuffer.limit(); i++) {
                tmp = byteBuffer.get(i);
                byteBuffer.put(i, byteBuffer.get(byteBuffer.limit() - i - 1));
                byteBuffer.put(byteBuffer.limit() - i - 1, tmp);
            }
        }
    }

    // Returns the current cumulative frequency (map it to a symbol yourself!)
    public static int RANSGetCumulativeFrequency(final int r, final int scaleBits) {
        return r & ((1 << scaleBits) - 1);
    }

    // Re-normalize.
    public static int RANSDecodeRenormalize4x8(int r, final ByteBuffer byteBuffer) {
        // re-normalize

        //rans4x8
        if (r < Constants.RANS_4x8_LOWER_BOUND) {
            do {
                r = (r << 8) | (0xFF & byteBuffer.get());
            } while (r < Constants.RANS_4x8_LOWER_BOUND);
        }
        return r;
    }

    public static int RANSDecodeRenormalizeNx16(int r, final ByteBuffer byteBuffer) {
        // ransNx16
        if (r < (Constants.RANS_Nx16_LOWER_BOUND)) {
            int i = (0xFF & byteBuffer.get());
            i |= (0xFF & byteBuffer.get()) << 8;

            r = (r << 16) | i;
        }
        return r;
    }

    public static void writeUint7(int i, ByteBuffer cp) {
        int s = 0;
        int X = i;
        do {
            s += 7;
            X >>= 7;
        } while (X > 0);
        do {
            s -= 7;
            //writeByte
            int s_ = (s > 0) ? 1 : 0;
            cp.put((byte) (((i >> s) & 0x7f) + (s_ << 7)));
        } while (s > 0);
    }

    public static int readUint7(ByteBuffer cp) {
        int i = 0;
        int c;
        do {
            //read byte
            c = cp.get();
            i = (i << 7) | (c & 0x7f);
        } while ((c & 0x80) != 0);
        return i;
    }

    public static void normaliseFrequenciesOrder0(final int[] F, final int bits) {
        // Returns an array of normalised Frequencies,
        // such that the frequencies add up to 1<<bits.
        int T = 0;

        // compute total Frequency
        for (int freq : F) {
            T += freq;
        }

        // Scale total of frequencies to max
        final int renormFreq = 1 << bits;

        // To avoid division by 0 error, if T=0, set tr = 0.
        // when T=0 i.e, when all symbol frequencies are 0, tr is not used anyway.
        final long tr = (T>0)?(((long) (renormFreq) << 31) / T + (1 << 30) / T):0;

        // keep track of the symbol that has the maximum frequency
        // in the input Frequency array.
        // This symbol's frequency might be altered at the end to make sure
        // that the total normalized frequencies add up to "renormFreq" value.
        int m = 0;
        int M = 0;
        for (int symbol = 0; symbol < Constants.NUMBER_OF_SYMBOLS; symbol++) {
            if (m < F[symbol]) {
                m = F[symbol];
                M = symbol;
            }
        }
        int fsum = 0;
        for (int symbol = 0; symbol < Constants.NUMBER_OF_SYMBOLS; symbol++) {
            if (F[symbol] == 0) {
                continue;
            }

            // using tr to normalize symbol frequencies such that their total = renormFreq
            if ((F[symbol] = (int) ((F[symbol] * tr) >> 31)) == 0) {

                // A non-zero symbol frequency should not be incorrectly set to 0.
                // If the calculated value is 0, change it to 1
                F[symbol] = 1;
            }
            fsum += F[symbol];
        }

        // adjust the frequency of the symbol "M" such that
        // the sum of frequencies of all the symbols = renormFreq
        if (fsum < renormFreq) {
            F[M] += renormFreq - fsum;
        } else if (fsum > renormFreq) {
            F[M] -= fsum - renormFreq;
        }
    }

    public static void normaliseFrequenciesOrder1(final int[][] F, final int shift, final boolean constantShift) {
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[Constants.NUMBER_OF_SYMBOLS][j]==0){
                continue;
            }
            int bitSize = shift;
            if (!constantShift) {
                // log2 N = Math.log(N)/Math.log(2)
                bitSize = (int) Math.ceil(Math.log(F[Constants.NUMBER_OF_SYMBOLS][j]) / Math.log(2));

                if (bitSize > shift)
                    bitSize = shift;
            }
            // special case -> if a symbol occurs only once and at the end of the input,
            // then the order 0 freq table associated with it should have a frequency of 1 for symbol 0
            // i.e, F[sym][0] = 1
            normaliseFrequenciesOrder0(F[j], bitSize);
        }
    }

}
