package htsjdk.samtools.cram.compression.rans.ransnx16;

import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSEncodingSymbol;
import htsjdk.samtools.cram.compression.rans.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class E1N {

    // uncompress for Nway = 4. then extend Nway to be variable - 4 or 32
    // TODO: debug.
    static int compress(final ByteBuffer inBuffer, final RANSEncodingSymbol[][] syms, final ByteBuffer outBuffer) {
        final int in_size = inBuffer.remaining();
        final int compressedBlobSize;
        int rans0, rans1, rans2, rans3;
        rans0 = Constants.RANS_Nx16_LOWER_BOUND;
        rans1 = Constants.RANS_Nx16_LOWER_BOUND;
        rans2 = Constants.RANS_Nx16_LOWER_BOUND;
        rans3 = Constants.RANS_Nx16_LOWER_BOUND;

        /*
         * Slicing is needed for buffer reversing later.
         */
        final ByteBuffer ptr = outBuffer.slice();

        final int isz4 = in_size >> 2;
        int i0 = isz4 - 2;
        int i1 = 2 * isz4 - 2;
        int i2 = 3 * isz4 - 2;
        int i3 = 4 * isz4 - 2;

        int l0 = 0;
        if (i0 + 1 >= 0) {
            l0 = 0xFF & inBuffer.get(i0 + 1);
        }
        int l1 = 0;
        if (i1 + 1 >= 0) {
            l1 = 0xFF & inBuffer.get(i1 + 1);
        }
        int l2 = 0;
        if (i2 + 1 >= 0) {
            l2 = 0xFF & inBuffer.get(i2 + 1);
        }
        int l3;

        // Deal with the remainder
        l3 = 0xFF & inBuffer.get(in_size - 1);
        for (i3 = in_size - 2; i3 > 4 * isz4 - 2 && i3 >= 0; i3--) {
            final int c3 = 0xFF & inBuffer.get(i3);
            rans3 = syms[c3][l3].putSymbolNx16(rans3, ptr);
            l3 = c3;
        }

        for (; i0 >= 0; i0--, i1--, i2--, i3--) {
            final int c0 = 0xFF & inBuffer.get(i0);
            final int c1 = 0xFF & inBuffer.get(i1);
            final int c2 = 0xFF & inBuffer.get(i2);
            final int c3 = 0xFF & inBuffer.get(i3);

            rans3 = syms[c3][l3].putSymbolNx16(rans3, ptr);
            rans2 = syms[c2][l2].putSymbolNx16(rans2, ptr);
            rans1 = syms[c1][l1].putSymbolNx16(rans1, ptr);
            rans0 = syms[c0][l0].putSymbolNx16(rans0, ptr);

            l0 = c0;
            l1 = c1;
            l2 = c2;
            l3 = c3;
        }

        rans3 = syms[0][l3].putSymbolNx16(rans3, ptr);
        rans2 = syms[0][l2].putSymbolNx16(rans2, ptr);
        rans1 = syms[0][l1].putSymbolNx16(rans1, ptr);
        rans0 = syms[0][l0].putSymbolNx16(rans0, ptr);

        ptr.order(ByteOrder.BIG_ENDIAN);
        ptr.putInt(rans3);
        ptr.putInt(rans2);
        ptr.putInt(rans1);
        ptr.putInt(rans0);
        ptr.flip();
        compressedBlobSize = ptr.limit();
        Utils.reverse(ptr);
        /*
         * Depletion of the in buffer cannot be confirmed because of the get(int
         * position) method use during encoding, hence enforcing:
         */
        inBuffer.position(inBuffer.limit());
        return compressedBlobSize;
    }
}
