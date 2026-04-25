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

/**
 * Encoding state for a single symbol in the rANS codec. Fields are package-private to allow
 * the encode loops in {@link RANSNx16Encode} and {@link RANS4x8Encode} to inline the
 * renormalization and state-update arithmetic directly, avoiding method call overhead
 * in the hot inner loop.
 */
public final class RANSEncodingSymbol {
    /** (Exclusive) upper bound of pre-normalization interval. */
    long xMax;
    /** Fixed-point reciprocal frequency for integer division. */
    int rcpFreq;
    /** Bias term for the encoding formula. */
    int bias;
    /** Complement of frequency: (1 &lt;&lt; scaleBits) - freq. */
    int cmplFreq;
    /** Reciprocal shift (includes the +32 adjustment). */
    int rcpShift;

    /** Reset all encoding parameters to zero. */
    public void reset() {
        xMax = rcpFreq = bias = cmplFreq = rcpShift = 0;
    }

    /**
     * Initialize encoding parameters for a symbol given its position in the frequency table.
     * Computes the reciprocal frequency and bias needed for fast integer division during encoding.
     *
     * @param start cumulative frequency of all preceding symbols
     * @param freq frequency of this symbol (must be &gt; 0)
     * @param scaleBits log2 of the total frequency sum
     */
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

    /**
     * byte[] variant for Nx16 encoding — writes backwards (decrementing posHolder[0]).
     * Renormalization bytes are written so the final memory layout is little-endian
     * (LSB at lower address), matching htslib's RansEncPutSymbol output format.
     */
    public long putSymbolNx16(final long r, final byte[] out, final int[] posHolder) {
        long retSymbol = r;
        int pos = posHolder[0];
        if (retSymbol >= xMax) {
            // Write 2-byte LE renorm word: MSB at higher addr (written first), LSB at lower addr (written second)
            out[--pos] = (byte) ((retSymbol >> 8) & 0xFF);
            out[--pos] = (byte) (retSymbol & 0xFF);
            retSymbol >>= 16;
            if (retSymbol >= xMax) {
                out[--pos] = (byte) ((retSymbol >> 8) & 0xFF);
                out[--pos] = (byte) (retSymbol & 0xFF);
                retSymbol >>= 16;
            }
        }
        posHolder[0] = pos;
        final long q = ((retSymbol * (0xFFFFFFFFL & rcpFreq)) >> rcpShift);
        return retSymbol + bias + q * cmplFreq;
    }

    /** byte[] variant for 4x8 encoding — writes backwards, decrementing posHolder[0]. */
    public long putSymbol4x8(final long r, final byte[] out, final int[] posHolder) {
        long retSymbol = r;
        int pos = posHolder[0];
        if (retSymbol >= xMax) {
            out[--pos] = (byte) (retSymbol & 0xFF);
            retSymbol >>= 8;
            if (retSymbol >= xMax) {
                out[--pos] = (byte) (retSymbol & 0xFF);
                retSymbol >>= 8;
            }
        }
        posHolder[0] = pos;
        final long q = ((retSymbol * (0xFFFFFFFFL & rcpFreq)) >> rcpShift);
        return retSymbol + bias + q * cmplFreq;
    }
}