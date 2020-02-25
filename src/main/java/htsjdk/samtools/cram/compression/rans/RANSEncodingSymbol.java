/*
 * Copyright (c) 2019 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.samtools.cram.compression.rans;

import htsjdk.utils.ValidationUtils;

import java.nio.ByteBuffer;

final class RANSEncodingSymbol {
    private int xMax;       // (Exclusive) upper bound of pre-normalization interval
    private int rcpFreq;    // Fixed-point reciprocal frequency
    private int bias;       // Bias
    private int cmplFreq;   // Complement of frequency: (1 << scaleBits) - freq
    private int rcpShift;   // Reciprocal shift

    public void reset() {
        xMax = rcpFreq = bias = cmplFreq = rcpFreq = 0;
    }

    public void set(final int start, final int freq, final int scaleBits) {
        // RansAssert(scale_bits <= 16); RansAssert(start <= (1u <<
        // scale_bits)); RansAssert(freq <= (1u << scale_bits) - start);

        xMax = ((Constants.RANS_BYTE_L >> scaleBits) << 8) * freq;
        cmplFreq = (1 << scaleBits) - freq;
        if (freq < 2) {
            rcpFreq = (int) ~0L;
            rcpShift = 0;
            bias = start + (1 << scaleBits) - 1;
        } else {
            // Alverson, "Integer Division using reciprocals"
            // shift=ceil(log2(freq))
            int shift = 0;
            while (freq > (1L << shift)) {
                shift++;
            }

            rcpFreq = (int) (((1L << (shift + 31)) + freq - 1) / freq);
            rcpShift = shift - 1;

            // With these values, 'q' is the correct quotient, so we
            // have bias=start.
            bias = start;
        }

        rcpShift += 32; // Avoid the extra >>32 in RansEncPutSymbol
    }

    public int putSymbol(int r, final ByteBuffer byteBuffer) {
        ValidationUtils.validateArg(xMax != 0, "can't encode symbol with freq=0");

        // re-normalize
        int x = r;
        if (x >= xMax) {
            byteBuffer.put((byte) (x & 0xFF));
            x >>= 8;
            if (x >= xMax) {
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
        final long q = ((x * (0xFFFFFFFFL & rcpFreq)) >> rcpShift);
        r = (int) (x + bias + q * cmplFreq);
        return r;
    }
}
