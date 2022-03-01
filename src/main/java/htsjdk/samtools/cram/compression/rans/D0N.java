package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;

public class D0N {
    static void uncompress(
            final ByteBuffer inBuffer,
            ArithmeticDecoder D,
            RANSDecodingSymbol[] syms,
            final ByteBuffer outBuffer,
            final int out_sz,
            final int Nway) {

        // Nway parallel rans states. Nway = 4 or 32
        final int[] rans = new int[Nway];

        // c is the array of decoded symbols
        final byte[] c = new byte[Nway];
        int r;
        for (r=0; r<Nway; r++){
            rans[r] = inBuffer.getInt();
        }

        // Number of elements that don't fall into the Nway streams
        int remSize = (out_sz % Nway);
        final int out_end = out_sz - remSize;
        for (int i = 0; i < out_end; i += Nway) {
            for (r=0; r<Nway; r++){

                // Nway parallel decoding rans states
                c[r] = D.R[Utils.RANSDecodeGet(rans[r], Constants.TF_SHIFT)];
                outBuffer.put(i+r, c[r]);
                rans[r] = syms[0xFF & c[r]].advanceSymbolStep(rans[r], Constants.TF_SHIFT);
                rans[r] = Utils.RANSDecodeRenormalizeNx16(rans[r], inBuffer);
            }
        }
        outBuffer.position(out_end);
        int rev_idx = 0;

        // decode the remaining bytes
        while (remSize>0){
            byte symbol = D.R[Utils.RANSDecodeGet(rans[rev_idx], Constants.TF_SHIFT)];
            syms[0xFF & symbol].advanceSymbolNx16(rans[rev_idx], inBuffer, Constants.TF_SHIFT);
            outBuffer.put(symbol);
            remSize --;
            rev_idx ++;
        }
        outBuffer.position(0);
    }

}