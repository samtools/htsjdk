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
 * Holds the start and frequency for a single symbol in the rANS decoding table.
 * The reverse-lookup table mapping cumulative frequency to symbol is held separately
 * in the decoder's {@code reverseLookup} arrays.
 *
 * <p>Decoding a symbol is a two-step process:
 * <ol>
 *   <li>{@link #advanceSymbolStep} — updates the rANS state using the symbol's range</li>
 *   <li>{@link Utils#RANSDecodeRenormalizeNx16} or {@link Utils#RANSDecodeRenormalize4x8}
 *       — renormalizes by reading bytes from the compressed stream</li>
 * </ol>
 */
public final class RANSDecodingSymbol {
    int start;
    int freq;

    /**
     * Set the decoding parameters for this symbol.
     *
     * @param start the cumulative frequency of all preceding symbols
     * @param freq the frequency of this symbol
     */
    public void set(final int start, final int freq) {
        this.start = start;
        this.freq = freq;
    }

    /**
     * Advance the rANS state by one decoded symbol. Does not renormalize — the caller
     * must call the appropriate {@code Utils.RANSDecodeRenormalize*} method after this.
     *
     * @param r the current rANS state
     * @param scaleBits the frequency scale (log2 of total frequency sum)
     * @return the updated rANS state (before renormalization)
     */
    public long advanceSymbolStep(final long r, final int scaleBits) {
        final int mask = (1 << scaleBits) - 1;
        return freq * (r >> scaleBits) + (r & mask) - start;
    }
}
