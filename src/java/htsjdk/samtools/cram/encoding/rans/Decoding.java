package htsjdk.samtools.cram.encoding.rans;

import java.nio.ByteBuffer;

@SuppressWarnings("SameParameterValue")
class Decoding {

    static class FC {
        int F, C;
    }

    static class ari_decoder {
        final FC[] fc = new FC[256];
        byte[] R;
    }

    static class RansDecSymbol {
        int start; // Start of range.
        int freq; // Symbol frequency.
    }

    // Initialize a decoder symbol to start "start" and frequency "freq"
    static void RansDecSymbolInit(RansDecSymbol s, int start, int freq) {
        assert (start <= (1 << 16));
        assert (freq <= (1 << 16) - start);
        s.start = start;
        s.freq = freq;
    }

    // Advances in the bit stream by "popping" a single symbol with range start
    // "start" and frequency "freq". All frequencies are assumed to sum to
    // "1 << scale_bits".
    // No renormalization or output happens.
    private static int RansDecAdvanceStep(int r, int start, int freq, int scale_bits) {
        int mask = ((1 << scale_bits) - 1);

        // s, x = D(x)
        return freq * (r >> scale_bits) + (r & mask) - start;
    }

    // Equivalent to RansDecAdvanceStep that takes a symbol.
    static int RansDecAdvanceSymbolStep(int r, RansDecSymbol sym, int scale_bits) {
        return RansDecAdvanceStep(r, sym.start, sym.freq, scale_bits);
    }

    // Returns the current cumulative frequency (map it to a symbol yourself!)
    static int RansDecGet(int r, int scale_bits) {
        return r & ((1 << scale_bits) - 1);
    }

    // Equivalent to RansDecAdvance that takes a symbol.
    static int RansDecAdvanceSymbol(int r, ByteBuffer pptr, RansDecSymbol sym,
                                    int scale_bits) {
        return Decoding
                .RansDecAdvance(r, pptr, sym.start, sym.freq, scale_bits);
    }

    // Advances in the bit stream by "popping" a single symbol with range start
    // "start" and frequency "freq". All frequencies are assumed to sum to
    // "1 << scale_bits",
    // and the resulting bytes get written to ptr (which is updated).
    private static int RansDecAdvance(int r, ByteBuffer pptr, int start, int freq,
                                      int scale_bits) {
        int mask = (1 << scale_bits) - 1;

        // s, x = D(x)
        r = freq * (r >> scale_bits) + (r & mask) - start;

        // renormalize
        if (r < Constants.RANS_BYTE_L) {
            do {
                final int b = 0xFF & pptr.get();
                r = (r << 8) | b;
            } while (r < Constants.RANS_BYTE_L);

        }

        return r;
    }

    // Renormalize.
    static int RansDecRenorm(int r, ByteBuffer pptr) {
        // renormalize
        if (r < Constants.RANS_BYTE_L) {
            do
                r = (r << 8) | (0xFF & pptr.get());
            while (r < Constants.RANS_BYTE_L);
        }

        return r;
    }

}
