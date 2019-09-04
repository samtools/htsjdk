package htsjdk.samtools.cram.compression.rans;

import htsjdk.utils.ValidationUtils;

import java.nio.ByteBuffer;

class Encoding {

    static class RansEncSymbol {
        int xMax;       // (Exclusive) upper bound of pre-normalization interval
        int rcpFreq;    // Fixed-point reciprocal frequency
        int bias;       // Bias
        int cmplFreq;   // Complement of frequency: (1 << scaleBits) - freq
        int rcpShift;   // Reciprocal shift
    }

    static void RansEncSymbolInit(final RansEncSymbol s, final int start, final int freq, final int scaleBits) {
        // RansAssert(scale_bits <= 16); RansAssert(start <= (1u <<
        // scale_bits)); RansAssert(freq <= (1u << scale_bits) - start);

        s.xMax = ((Constants.RANS_BYTE_L >> scaleBits) << 8) * freq;
        s.cmplFreq = (1 << scaleBits) - freq;
        if (freq < 2) {
            s.rcpFreq = (int) ~0L;
            s.rcpShift = 0;
            s.bias = start + (1 << scaleBits) - 1;
        } else {
            // Alverson, "Integer Division using reciprocals"
            // shift=ceil(log2(freq))
            int shift = 0;
            while (freq > (1L << shift)) {
                shift++;
            }

            s.rcpFreq = (int) (((1L << (shift + 31)) + freq - 1) / freq);
            s.rcpShift = shift - 1;

            // With these values, 'q' is the correct quotient, so we
            // have bias=start.
            s.bias = start;
        }

        s.rcpShift += 32; // Avoid the extra >>32 in RansEncPutSymbol
    }

    static int RansEncPutSymbol(int r, final ByteBuffer byteBuffer, final RansEncSymbol sym) {
        ValidationUtils.validateArg(sym.xMax != 0, "can't encode symbol with freq=0");

        // re-normalize
        int x = r;
        final int x_max = sym.xMax;
        if (x >= x_max) {
            byteBuffer.put((byte) (x & 0xFF));
            x >>= 8;
            if (x >= x_max) {
                byteBuffer.put((byte) (x & 0xFF));
                x >>= 8;
            }
        }

        // x = C(s,x)
        // NOTE: written this way so we get a 32-bit "multiply high" when
        // available. If you're on a 64-bit platform with cheap multiplies
        // (e.g. x64), just bake the +32 into rcp_shift.
        // int q = (int) (((uint64_t)x * sym.rcp_freq) >> 32) >> sym.rcp_shift;

        // The extra >>32 has already been added to RansEncSymbolInit
        final long q = ((x * (0xFFFFFFFFL & sym.rcpFreq)) >> sym.rcpShift);
        r = (int) (x + sym.bias + q * sym.cmplFreq);
        return r;
    }
}
