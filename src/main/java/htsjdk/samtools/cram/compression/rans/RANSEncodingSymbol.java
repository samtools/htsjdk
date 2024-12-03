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

public final class RANSEncodingSymbol {
    private long xMax;       // (Exclusive) upper bound of pre-normalization interval
    private int rcpFreq;    // Fixed-point reciprocal frequency
    private int bias;       // Bias
    private int cmplFreq;   // Complement of frequency: (1 << scaleBits) - freq
    private int rcpShift;   // Reciprocal shift

    public void reset() {
        xMax = rcpFreq = bias = cmplFreq = rcpShift = 0;
    }

    public void set(final int start, final int freq, final int scaleBits) {

        // Rans4x8: xMax = ((Constants.RANS_BYTE_L_4x8 >> scaleBits) << 8) * freq = (1<< 31-scaleBits) * freq
        // RansNx16: xMax = ((Constants.RANS_BYTE_L_Nx16 >> scaleBits) << 16) * freq = (1<< 31-scaleBits) * freq
        // why freq > 4095 in Nx16?
        xMax = (1L<< (31-scaleBits)) * freq;
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

    public long putSymbol4x8(final long r, final ByteBuffer byteBuffer) {
        ValidationUtils.validateArg(xMax != 0, "can't encode symbol with freq=0");

        // re-normalize
        long retSymbol = r;
        if (retSymbol >= xMax) {
            byteBuffer.put((byte) (retSymbol & 0xFF));
            retSymbol >>= 8;
            if (retSymbol >= xMax) {
                byteBuffer.put((byte) (retSymbol & 0xFF));
                retSymbol >>= 8;
            }
        }

        // x = C(s,x)
        // NOTE: written this way so we get a 32-bit "multiply high" when
        // available. If you're on a 64-bit platform with cheap multiplies
        // (e.g. x64), just bake the +32 into rcp_shift.
        // int q = (int) (((uint64_t)x * sym.rcp_freq) >> 32) >> sym.rcp_shift;

        // The extra >>32 has already been added to RansEncSymbolInit
        final long q = ((retSymbol * (0xFFFFFFFFL & rcpFreq)) >> rcpShift);
        return retSymbol + bias + q * cmplFreq;
    }

    public long putSymbolNx16(final long r, final ByteBuffer byteBuffer) {
        ValidationUtils.validateArg(xMax != 0, "can't encode symbol with freq=0");

        // re-normalize
        long retSymbol = r;
        if (retSymbol >= xMax) {
            byteBuffer.put((byte) ((retSymbol>>8) & 0xFF)); // extra line - 1 more byte
            byteBuffer.put((byte) (retSymbol & 0xFF));
            retSymbol >>=16;
            if (retSymbol >= xMax) {
                byteBuffer.put((byte) ((retSymbol>>8) & 0xFF)); // extra line - 1 more byte
                byteBuffer.put((byte) (retSymbol & 0xFF));
                retSymbol >>=16;
            }
        }

        // x = C(s,x)
        // NOTE: written this way so we get a 32-bit "multiply high" when
        // available. If you're on a 64-bit platform with cheap multiplies
        // (e.g. x64), just bake the +32 into rcp_shift.
        // int q = (int) (((uint64_t)x * sym.rcp_freq) >> 32) >> sym.rcp_shift;

        // The extra >>32 has already been added to RansEncSymbolInit
        final long q = ((retSymbol * (0xFFFFFFFFL & rcpFreq)) >> rcpShift);
        return retSymbol + bias + q * cmplFreq;
    }
}