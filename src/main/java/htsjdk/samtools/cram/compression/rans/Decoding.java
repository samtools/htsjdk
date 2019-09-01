package htsjdk.samtools.cram.compression.rans;

import htsjdk.utils.ValidationUtils;

import java.nio.ByteBuffer;

class Decoding {

    // Initialize a decoder symbol to start "start" and frequency "freq"
    static void RansDecSymbolInit(final RANSDecodingSymbol symbol, final int start, final int freq) {
        ValidationUtils.validateArg(start <= (1 << 16), "invalid symbol start");
        ValidationUtils.validateArg(freq <= (1 << 16) - start, "invalid symbol frequency");
        symbol.start = start;
        symbol.freq = freq;
    }

    // Advances in the bit stream by "popping" a single symbol with range start
    // "start" and frequency "freq". All frequencies are assumed to sum to
    // "1 << scale_bits".
    // No renormalization or output happens.
    private static int RansDecAdvanceStep(final int r, final int start, final int freq, final int scaleBits) {
        final int mask = ((1 << scaleBits) - 1);

        // s, x = D(x)
        return freq * (r >> scaleBits) + (r & mask) - start;
    }

    // Equivalent to RansDecAdvanceStep that takes a symbol.
    static int RansDecAdvanceSymbolStep(final int r, final RANSDecodingSymbol sym, final int scaleBits) {
        return RansDecAdvanceStep(r, sym.start, sym.freq, scaleBits);
    }

    // Returns the current cumulative frequency (map it to a symbol yourself!)
    static int RansDecGet(final int r, final int scaleBits) {
        return r & ((1 << scaleBits) - 1);
    }

    // Equivalent to RansDecAdvance that takes a symbol.
    static int RansDecAdvanceSymbol(final int r, final ByteBuffer byteBuffer, final RANSDecodingSymbol sym, final int scaleBits) {
        return Decoding.RansDecAdvance(r, byteBuffer, sym.start, sym.freq, scaleBits);
    }

    // Advances in the bit stream by "popping" a single symbol with range start
    // "start" and frequency "freq". All frequencies are assumed to sum to
    // "1 << scale_bits",
    // and the resulting bytes get written to ptr (which is updated).
    private static int RansDecAdvance(int r, final ByteBuffer byteBuffer, final int start, final int freq, final int scaleBits) {
        final int mask = (1 << scaleBits) - 1;

        // s, x = D(x)
        r = freq * (r >> scaleBits) + (r & mask) - start;

        // re-normalize
        if (r < Constants.RANS_BYTE_L) {
            do {
                final int b = 0xFF & byteBuffer.get();
                r = (r << 8) | b;
            } while (r < Constants.RANS_BYTE_L);

        }

        return r;
    }

    // Re-normalize.
    static int RansDecRenormalize(int r, final ByteBuffer byteBuffer) {
        // re-normalize
        if (r < Constants.RANS_BYTE_L) {
            do {
                r = (r << 8) | (0xFF & byteBuffer.get());
            } while (r < Constants.RANS_BYTE_L);
        }

        return r;
    }

}
