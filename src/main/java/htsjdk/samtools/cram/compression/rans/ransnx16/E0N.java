package htsjdk.samtools.cram.compression.rans.ransnx16;

import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSEncodingSymbol;
import htsjdk.samtools.cram.compression.rans.Utils;

import java.nio.ByteBuffer;

public class E0N {
    static int compress(
            final ByteBuffer inBuffer,
            final RANSEncodingSymbol[] syms,
            final ByteBuffer cp,
            final RANSNx16Params ransNx16Params) {
        final int Nway = ransNx16Params.getInterleaveSize();

        final int cdata_size;
        final int in_size = inBuffer.remaining();
        final ByteBuffer ptr = cp.slice();
        final int[] rans = new int[Nway];
        final int[] c = new int[Nway]; // c is the array of symbols
        int r;
        for (r=0; r<Nway; r++){

            // initialize rans states
            rans[r] = Constants.RANS_Nx16_LOWER_BOUND;
        }

        // number of remaining elements = in_size % N
        int remSize = (in_size%Nway);
        int rev_idx = 1;

        // encoded in LIFO order
        while (remSize>0){

            // encode remaining elements first
            int symbol_ =0xFF & inBuffer.get(in_size - rev_idx);
            rans[remSize - 1] = syms[symbol_].putSymbolNx16(rans[remSize - 1], ptr);
            remSize --;
            rev_idx ++;
        }
        int i;

        for (i = (in_size - (in_size%Nway)); i > 0; i -= Nway) {
            for (r = Nway - 1; r >= 0; r--){

                // encode using Nway parallel rans states. Nway = 4 or 32
                c[r] = 0xFF & inBuffer.get(i - (Nway - r));
                rans[r] = syms[c[r]].putSymbolNx16(rans[r], ptr);
            }
        }
        for (i=Nway-1; i>=0; i--){
            ptr.putInt(rans[i]);
        }
        ptr.position();
        ptr.flip();
        cdata_size = ptr.limit();

        // since the data is encoded in reverse order,
        // reverse the compressed bytes, so that it is in correct order when uncompressed.
        Utils.reverse(ptr);
        inBuffer.position(inBuffer.limit());
        return cdata_size;
    }

}