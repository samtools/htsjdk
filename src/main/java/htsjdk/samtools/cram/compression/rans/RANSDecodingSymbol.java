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

import java.nio.ByteBuffer;

final public class RANSDecodingSymbol {
    int start;  // Start of range.
    int freq;   // Symbol frequency.

    public void set(final int start, final int freq) {
        // This method gets called a LOT so this validation is too expensive to leave in.
        //ValidationUtils.validateArg(start <= (1 << 16), "invalid RANSDecodingSymbol start");
        //ValidationUtils.validateArg(freq <= (1 << 16) - start, "invalid RANSDecodingSymbol frequency");
        this.start = start;
        this.freq = freq;
    }

    // Advances in the bit stream by "popping" a single symbol with range start
    // "start" and frequency "freq". All frequencies are assumed to sum to
    // "1 << scale_bits".
    // No renormalization or output happens.
    public int advanceSymbolStep(final int r, final int scaleBits) {
        final int mask = ((1 << scaleBits) - 1);

        // s, x = D(x)
        return freq * (r >> scaleBits) + (r & mask) - start;
    }

    // Advances in the bit stream by "popping" a single symbol with range start
    // "start" and frequency "freq". All frequencies are assumed to sum to
    // "1 << scale_bits".
    public int advanceSymbol(final int rIn, final ByteBuffer byteBuffer, final int scaleBits) {
        final int mask = (1 << scaleBits) - 1;

        // s, x = D(x)
        int r = rIn;
        r = freq * (r >> scaleBits) + (r & mask) - start;

        // re-normalize
        if (r < Constants.RANS_BYTE_L_4x8) {
            do {
                final int b = 0xFF & byteBuffer.get();
                r = (r << 8) | b;
            } while (r < Constants.RANS_BYTE_L_4x8);
        }

        return r;
    }

    public int advanceSymbolNx16(final int rIn, final ByteBuffer byteBuffer, final int scaleBits) {
        final int mask = (1 << scaleBits) - 1;

        // s, x = D(x)
        int r = rIn;
        r = freq * (r >> scaleBits) + (r & mask) - start;

        // re-normalize
        if (r < (Constants.RANS_BYTE_L_Nx16)){
            int i = 0xFF & byteBuffer.get();
            i |= (0xFF & byteBuffer.get())<<8;
            r = (r << 16) + i;
        }

        return r;
    }

}
