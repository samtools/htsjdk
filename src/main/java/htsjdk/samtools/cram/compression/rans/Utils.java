package htsjdk.samtools.cram.compression.rans;

import htsjdk.samtools.cram.CRAMException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final public class Utils {

    private static void reverse(final byte[] array, final int offset, final int size) {
        if (array == null) {
            return;
        }
        int i = offset;
        int j = offset + size - 1;
        while (j > i) {
            byte tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }

    public static void reverse(final ByteBuffer byteBuffer) {
        if (byteBuffer.hasArray()) {
            reverse(byteBuffer.array(), byteBuffer.arrayOffset(), byteBuffer.limit());
        } else {
            for (int i = 0; i < byteBuffer.limit(); i++) {
                byteBuffer.put(i, byteBuffer.get(byteBuffer.limit() - i - 1));
                byteBuffer.put(byteBuffer.limit() - i - 1, byteBuffer.get(i));
            }
        }
    }

    // Returns the current cumulative frequency (map it to a symbol yourself!)
    public static int RANSGetCumulativeFrequency(final long r, final int scaleBits) {
        return (int) (r & ((1 << scaleBits) - 1)); // since cumulative frequency will be a maximum of 4096
    }

    public static long RANSDecodeRenormalize4x8(final long r, final ByteBuffer byteBuffer) {
        long ret = r;
        while (ret < Constants.RANS_4x8_LOWER_BOUND) {
            ret = (ret << 8) | (0xFF & byteBuffer.get());
        }
        return ret;
    }

    public static long RANSDecodeRenormalizeNx16(final long r, final ByteBuffer byteBuffer) {
        long ret = r;
        if (ret < (Constants.RANS_Nx16_LOWER_BOUND)) {
            final int i = (0xFF & byteBuffer.get()) | ((0xFF & byteBuffer.get()) << 8);
            ret = (ret << 16) | i;
        }
        return ret;
    }

    public static void writeUint7(final int i, final ByteBuffer cp) {
        int s = 0;
        int X = i;
        do {
            s += 7;
            X >>= 7;
        } while (X > 0);
        do {
            s -= 7;
            //writeByte
            final int s_ = (s > 0) ? 1 : 0;
            cp.put((byte) (((i >> s) & 0x7f) + (s_ << 7)));
        } while (s > 0);
    }

    public static int readUint7(final ByteBuffer cp) {
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

        // To avoid division by 0 error, if T=0, set tr = 0.
        // when T=0 i.e, when all symbol frequencies are 0, tr is not used anyway.
        final long tr = (T>0)?(((long) (renormFreq) << 31) / T + (1 << 30) / T):0;
        int fsum = 0;
        for (int symbol = 0; symbol < Constants.NUMBER_OF_SYMBOLS; symbol++) {
            if (F[symbol] == 0) {
                continue;
            }

            // As per spec, total frequencies after normalization should be 4096 (4095 could be considered legacy value)
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

    public static void normaliseFrequenciesOrder1(final int[][] F, final int shift) {
        // calculate the minimum bit size required for representing the frequency array for each symbol
        // and normalise the frequency array using the calculated bit size
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[Constants.NUMBER_OF_SYMBOLS][j]==0){
                continue;
            }

            // log2 N = Math.log(N)/Math.log(2)
            int bitSize = (int) Math.ceil(Math.log(F[Constants.NUMBER_OF_SYMBOLS][j]) / Math.log(2));
            if (bitSize > shift)
                bitSize = shift;

            // TODO: check if handling bitSize = 0 is required
            if (bitSize == 0)
                bitSize = 1; // bitSize cannot be zero

            // special case -> if a symbol occurs only once and at the end of the input,
            // then the order 0 freq table associated with it should have a frequency of 1 for symbol 0
            // i.e, F[sym][0] = 1
            normaliseFrequenciesOrder0(F[j], bitSize);
        }
    }

    public static void normaliseFrequenciesOrder0Shift(final int[] frequencies, final int bits){

        // compute total frequency
        int totalFrequency = 0;
        for (int freq : frequencies) {
            totalFrequency += freq;
        }
        if (totalFrequency == 0 || totalFrequency == (1<<bits)){
            return;
        }

        // calculate the bit shift that is required to scale the frequencies to (1 << bits)
        int shift = 0;
        while (totalFrequency < (1 << bits)) {
            totalFrequency *= 2;
            shift++;
        }

        // scale the frequencies to (1 << bits) using the calculated shift
        for (int symbol = 0; symbol < Constants.NUMBER_OF_SYMBOLS; symbol++) {
            if (frequencies[symbol]!=0){
            frequencies[symbol] <<= shift;
            }
        }
    }

    public static void normaliseFrequenciesOrder1Shift(final int[][] F, final int shift){
        // normalise the frequency array for each symbol
        for (int symbol = 0; symbol < Constants.NUMBER_OF_SYMBOLS; symbol++) {
            if (F[Constants.NUMBER_OF_SYMBOLS][symbol]!=0){
                normaliseFrequenciesOrder0Shift(F[symbol],shift);
            }
        }
    }

    public static ByteBuffer allocateOutputBuffer(final int inSize) {
        // This calculation is identical to the one in samtools rANS_static.c
        // Presumably the frequency table (always big enough for order 1) = 257*257,
        // then * 3 for each entry (byte->symbol, 2 bytes -> scaled frequency),
        // + 9 for the header (order byte, and 2 int lengths for compressed/uncompressed lengths).
        final int compressedSize = (int) (inSize + 257 * 257 * 3 + 9);
        final ByteBuffer outputBuffer = ByteBuffer.allocate(compressedSize).order(ByteOrder.LITTLE_ENDIAN);
        if (outputBuffer.remaining() < compressedSize) {
            throw new CRAMException("Failed to allocate sufficient buffer size for RANS coder.");
        }
        return outputBuffer;
    }

    // returns a new LITTLE_ENDIAN ByteBuffer of size = bufferSize
    public static ByteBuffer allocateByteBuffer(final int bufferSize){
        return ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN);
    }

    // returns a LITTLE_ENDIAN ByteBuffer that is created by wrapping a byte[]
    public static ByteBuffer wrap(final byte[] inputBytes){
        return ByteBuffer.wrap(inputBytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    // returns a LITTLE_ENDIAN ByteBuffer that is created by inputBuffer.slice()
    public static ByteBuffer slice(final ByteBuffer inputBuffer){
        return inputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN);
    }

}