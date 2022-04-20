package htsjdk.samtools.cram.compression.rans.ransnx16;

import htsjdk.samtools.cram.compression.rans.ArithmeticDecoder;
import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSDecodingSymbol;
import htsjdk.samtools.cram.compression.rans.Utils;

import java.nio.ByteBuffer;

public class D0N {
    static void uncompress(
            final ByteBuffer inBuffer,
            ArithmeticDecoder D,
            RANSDecodingSymbol[] syms,
            final ByteBuffer outBuffer,
            final int out_sz,
            final RANSNx16Params ransNx16Params) {
        final int Nway = ransNx16Params.getInterleaveSize();

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
                c[r] = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans[r], Constants.TOTAL_FREQ_SHIFT)];
                outBuffer.put(i+r, c[r]);
                rans[r] = syms[0xFF & c[r]].advanceSymbolStep(rans[r], Constants.TOTAL_FREQ_SHIFT);
                rans[r] = Utils.RANSDecodeRenormalizeNx16(rans[r], inBuffer);
            }
        }
        outBuffer.position(out_end);
        int rev_idx = 0;

        // decode the remaining bytes
        while (remSize>0){
            byte symbol = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans[rev_idx], Constants.TOTAL_FREQ_SHIFT)];
            syms[0xFF & symbol].advanceSymbolNx16(rans[rev_idx], inBuffer, Constants.TOTAL_FREQ_SHIFT);
            outBuffer.put(symbol);
            remSize --;
            rev_idx ++;
        }
        outBuffer.position(0);
    }

}