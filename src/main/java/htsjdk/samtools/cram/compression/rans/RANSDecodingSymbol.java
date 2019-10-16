package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;

class RANSDecodingSymbol {
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
    // "1 << scale_bits",
    // and the resulting bytes get written to ptr (which is updated).
    //TODO: this javadoc above says this writes to ptr... ?
    public int advanceSymbol(final int rIn, final ByteBuffer byteBuffer, final int scaleBits) {
        final int mask = (1 << scaleBits) - 1;

        // s, x = D(x)
        int r = rIn;
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

}
